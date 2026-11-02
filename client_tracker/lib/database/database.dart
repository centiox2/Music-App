/// SQLite data layer (package:sqlite3 + sqlite3_flutter_libs).
///
/// Platform bootstrap lives in `app_database_io.dart` (Windows / Android /
/// iOS) and `app_database_web.dart` (browser build), selected by the
/// conditional import in `database_bootstrap.dart`.
///
/// Design notes:
/// - WAL mode for durable, crash-safe writes on io platforms.
/// - `PRAGMA foreign_keys = ON` on every connection.
/// - Totals are never stored; they are derived from `client_counts` rows.
/// - On io platforms, a rotating rolling backup of the database file is made
///   (`client_tracker.db.bak1` … `bak5`, one per app launch) so a corrupt
///   write can never destroy more than the last few sessions.
library;

import 'dart:convert';

import 'package:sqlite3/common.dart' as common;

/// Thrown when the database schema cannot be created or migrated.
class DatabaseExceptionApp implements Exception {
  DatabaseExceptionApp(this.message);
  final String message;

  @override
  String toString() => 'DatabaseExceptionApp: $message';
}

class AppDatabase {
  /// Public so the platform bootstrap files (same package, different
  /// libraries) can construct instances.
  AppDatabase.internal(this.file, this._db);

  final String file;
  final common.CommonDatabase _db;

  static const int schemaVersion = 3;
  static const int rollingBackupCount = 5;

  /// Runs [action] inside a transaction; rolls back on any error.
  T transaction<T>(T Function() action) {
    _db.execute('BEGIN');
    try {
      final result = action();
      _db.execute('COMMIT');
      return result;
    } catch (_) {
      _db.execute('ROLLBACK');
      rethrow;
    }
  }

  common.CommonDatabase get raw => _db;

  // ---- schema & seed -------------------------------------------------------

  static void migrate(common.CommonDatabase db) {
    db.execute('BEGIN');
    try {
      db.execute('''
        CREATE TABLE IF NOT EXISTS staff (
          id            INTEGER PRIMARY KEY AUTOINCREMENT,
          name          TEXT NOT NULL,
          active        INTEGER NOT NULL DEFAULT 1,
          display_order INTEGER NOT NULL DEFAULT 0,
          created_at    TEXT NOT NULL
        );
      ''');

      db.execute('''
        CREATE TABLE IF NOT EXISTS weeks (
          id           INTEGER PRIMARY KEY AUTOINCREMENT,
          start_date  TEXT NOT NULL UNIQUE,
          end_date    TEXT NOT NULL,
          created_at  TEXT NOT NULL,
          updated_at  TEXT NOT NULL,
          show_zeros  INTEGER NOT NULL DEFAULT 1
        );
      ''');

      db.execute('''
        CREATE TABLE IF NOT EXISTS work_days (
          id      INTEGER PRIMARY KEY AUTOINCREMENT,
          week_id INTEGER NOT NULL REFERENCES weeks(id) ON DELETE CASCADE,
          date    TEXT NOT NULL UNIQUE,
          weekday INTEGER NOT NULL
        );
      ''');

      db.execute('''
        CREATE TABLE IF NOT EXISTS client_counts (
          id               INTEGER PRIMARY KEY AUTOINCREMENT,
          day_id           INTEGER NOT NULL REFERENCES work_days(id) ON DELETE CASCADE,
          staff_id         INTEGER NOT NULL REFERENCES staff(id),
          new_clients      INTEGER NOT NULL DEFAULT 0,
          existing_clients INTEGER NOT NULL DEFAULT 0,
          updated_at       TEXT NOT NULL,
          UNIQUE(day_id, staff_id)
        );
      ''');

      // v2: per-staff time off. One row = a staff member is off on that
      // working day of that week. Numbers already entered for the day are
      // kept but excluded from every total until the mark is removed.
      db.execute('''
        CREATE TABLE IF NOT EXISTS staff_day_off (
          id       INTEGER PRIMARY KEY AUTOINCREMENT,
          day_id   INTEGER NOT NULL REFERENCES work_days(id) ON DELETE CASCADE,
          staff_id INTEGER NOT NULL REFERENCES staff(id),
          UNIQUE(day_id, staff_id)
        );
      ''');

      db.execute('''
        CREATE INDEX IF NOT EXISTS idx_staff_day_off_day
          ON staff_day_off(day_id);
      ''');

      // v3: per-week show-zeros toggle. SQLite does not support
      // `ADD COLUMN IF NOT EXISTS`, so inspect the existing schema before
      // adding the column. The old try/catch silently left upgraded databases
      // without this column, making the toggle's UPDATE fail at runtime.
      final weekColumns = db.select('PRAGMA table_info(weeks);');
      final hasShowZeros = weekColumns.any(
        (column) => column['name'] == 'show_zeros',
      );
      if (!hasShowZeros) {
        db.execute(
          'ALTER TABLE weeks ADD COLUMN show_zeros INTEGER NOT NULL DEFAULT 1;',
        );
      }

      db.execute('''
        CREATE INDEX IF NOT EXISTS idx_work_days_week
          ON work_days(week_id);
      ''');
      db.execute('''
        CREATE INDEX IF NOT EXISTS idx_client_counts_day
          ON client_counts(day_id);
      ''');
      db.execute('''
        CREATE INDEX IF NOT EXISTS idx_client_counts_staff
          ON client_counts(staff_id);
      ''');

      db.execute('PRAGMA user_version = $schemaVersion;');
      db.execute('COMMIT');
    } catch (e) {
      db.execute('ROLLBACK');
      throw DatabaseExceptionApp('Schema migration failed: $e');
    }
  }

  /// First-launch convenience only: seeds the current spreadsheet's staff so
  /// the app is immediately usable. These are editable records, not constants.
  static void seedStaff(common.CommonDatabase db) {
    final existing = db.select('SELECT COUNT(*) AS c FROM staff;');
    if (existing.first['c']! as int > 0) return;

    const seed = [
      'Beatrice',
      'Collins',
      'Dennis',
      'Ian',
      'Joy',
      'Joyner',
      'Whitney',
      'Rhab',
      'Talaam',
      'Winne',
      'Karren',
      'Director/Eve',
      'Canisius Yego',
      'Cinthia',
    ];
    final now = DateTime.now().toIso8601String();
    final stmt = db.prepare(
      'INSERT INTO staff (name, active, display_order, created_at) '
      'VALUES (?, 1, ?, ?);',
    );
    db.execute('BEGIN');
    try {
      for (var i = 0; i < seed.length; i++) {
        stmt.execute([seed[i], i, now]);
      }
      db.execute('COMMIT');
    } catch (_) {
      db.execute('ROLLBACK');
      rethrow;
    } finally {
      stmt.dispose();
    }
  }

  // ---- Backup / restore ---------------------------------------------------

  /// Export of the full logical database contents as JSON (portable across
  /// machines and browsers, and across schema versions better than raw
  /// database files).
  String exportToJson() {
    List<Map<String, Object?>> dump(String sql) =>
        _db.select(sql).map(_rowToJson).toList();
    final tables = <String, List<Map<String, Object?>>>{
      'staff': dump('SELECT * FROM staff ORDER BY id;'),
      'weeks': dump('SELECT * FROM weeks ORDER BY id;'),
      'work_days': dump('SELECT * FROM work_days ORDER BY id;'),
      'client_counts': dump('SELECT * FROM client_counts ORDER BY id;'),
      'staff_day_off': dump('SELECT * FROM staff_day_off ORDER BY id;'),
    };
    return const JsonEncoder.withIndent('  ').convert({
      'format': 'client-tracker-backup',
      'version': schemaVersion,
      'exportedAt': DateTime.now().toIso8601String(),
      'tables': tables,
    });
  }

  /// Replaces all data with the contents of a JSON backup. Rows keep their
  /// original IDs so cross-references stay intact.
  ///
  /// Table and column names come from the (potentially untrusted) backup
  /// file, so they are validated against this allowlist before being
  /// interpolated into SQL.
  static const Map<String, Set<String>> _restorableColumns = {
    'staff': {'id', 'name', 'active', 'display_order', 'created_at'},
    'weeks': {
      'id',
      'start_date',
      'end_date',
      'created_at',
      'updated_at',
      'show_zeros',
    },
    'work_days': {'id', 'week_id', 'date', 'weekday'},
    'client_counts': {
      'id',
      'day_id',
      'staff_id',
      'new_clients',
      'existing_clients',
      'updated_at',
    },
    'staff_day_off': {'id', 'day_id', 'staff_id'},
  };

  void restoreFromJson(String json) {
    final Map<String, Object?> doc;
    try {
      doc = jsonDecode(json) as Map<String, Object?>;
    } catch (e) {
      throw DatabaseExceptionApp('Backup file is not valid JSON: $e');
    }
    if (doc['format'] != 'client-tracker-backup') {
      throw DatabaseExceptionApp('Not a Client Tracker backup file.');
    }
    final tables = doc['tables'];
    if (tables is! Map<String, Object?>) {
      throw DatabaseExceptionApp('Backup is missing table data.');
    }

    transaction(() {
      _db.execute('DELETE FROM staff_day_off;');
      _db.execute('DELETE FROM client_counts;');
      _db.execute('DELETE FROM work_days;');
      _db.execute('DELETE FROM weeks;');
      _db.execute('DELETE FROM staff;');
      const order = [
        'staff',
        'weeks',
        'work_days',
        'client_counts',
        'staff_day_off',
      ];
      for (final name in order) {
        final rows = tables[name];
        if (rows is! List) continue;
        final allowed = _restorableColumns[name]!;
        for (final row in rows) {
          final map = (row as Map).cast<String, Object?>();
          final cols = map.keys.where((c) => allowed.contains(c)).toList();
          if (cols.isEmpty) {
            throw DatabaseExceptionApp(
              'Backup row for "$name" has no valid columns.',
            );
          }
          final placeholders = List.filled(cols.length, '?').join(', ');
          _db.execute(
            'INSERT INTO $name (${cols.join(', ')}) VALUES ($placeholders);',
            [for (final c in cols) map[c]],
          );
        }
        _db.execute('DELETE FROM sqlite_sequence WHERE name = ?;', [name]);
        _db.execute(
          'INSERT INTO sqlite_sequence(name, seq) '
          "SELECT ?, COALESCE(MAX(id), 0) FROM $name;",
          [name],
        );
      }
    });
  }

  static Map<String, Object?> _rowToJson(common.Row row) => {
    for (final col in row.keys) col: row[col],
  };

  void close() => _db.dispose();
}
