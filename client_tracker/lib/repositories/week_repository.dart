import '../database/database.dart';
import '../models/staff.dart';
import 'staff_repository.dart';

/// Data access for weeks and their five working days.
class WeekRepository {
  WeekRepository(this._db);

  final AppDatabase _db;

  // ---- Weeks --------------------------------------------------------------

  Week? getWeek(int id) {
    final rows = _db.raw.select('SELECT * FROM weeks WHERE id = ?;', [id]);
    return rows.isEmpty ? null : Week.fromRow(rows.first);
  }

  /// Finds the week whose Monday is [monday], if any.
  Week? findByStartDate(DateTime monday) {
    final rows = _db.raw.select('SELECT * FROM weeks WHERE start_date = ?;', [
      _dateKey(monday),
    ]);
    return rows.isEmpty ? null : Week.fromRow(rows.first);
  }

  /// The week containing [date], if any.
  Week? findByDate(DateTime date) {
    final key = _dateKey(date);
    final rows = _db.raw.select(
      'SELECT * FROM weeks WHERE start_date <= ? AND end_date >= ? '
      'ORDER BY start_date DESC LIMIT 1;',
      [key, key],
    );
    return rows.isEmpty ? null : Week.fromRow(rows.first);
  }

  /// Newest weeks first.
  List<Week> getAllWeeks() {
    final rows = _db.raw.select(
      'SELECT * FROM weeks ORDER BY start_date DESC;',
    );
    return rows.map(Week.fromRow).toList();
  }

  /// Monday of the week containing [date].
  static DateTime mondayOf(DateTime date) {
    final d = DateTime(date.year, date.month, date.day);
    return d.subtract(Duration(days: d.weekday - 1));
  }

  /// Creates a week for the Monday–Friday containing [anyDayInWeek], plus its
  /// five work days and a zero-count row for every currently active staff
  /// member on every day. Throws [StateError] if the week already exists.
  Week createWeekFor(DateTime anyDayInWeek) {
    final monday = mondayOf(anyDayInWeek);
    final existing = findByStartDate(monday);
    if (existing != null) {
      throw StateError(
        'A week starting ${_displayDate(monday)} already exists.',
      );
    }

    final staff = StaffRepository(_db).getActive();
    final now = DateTime.now().toIso8601String();

    late Week week;
    _db.transaction(() {
      _db.raw.execute(
        'INSERT INTO weeks (start_date, end_date, created_at, updated_at) '
        'VALUES (?, ?, ?, ?);',
        [
          _dateKey(monday),
          _dateKey(monday.add(const Duration(days: 4))),
          now,
          now,
        ],
      );
      week = Week.fromRow(
        _db.raw.select('SELECT * FROM weeks WHERE start_date = ?;', [
          _dateKey(monday),
        ]).first,
      );

      for (var i = 0; i < 5; i++) {
        final day = monday.add(Duration(days: i));
        _db.raw.execute(
          'INSERT INTO work_days (week_id, date, weekday) VALUES (?, ?, ?);',
          [week.id, _dateKey(day), day.weekday],
        );
      }
    });

    if (staff.isNotEmpty) {
      addStaffToWeek(week.id, staff.map((s) => s.id).toList());
    }
    return week;
  }

  /// Adds zero ClientCount rows for [staffIds] across every day of the week
  /// (used at creation and when staff join mid-week).
  void addStaffToWeek(int weekId, List<int> staffIds) {
    if (staffIds.isEmpty) return;
    final days = _db.raw.select(
      'SELECT id FROM work_days WHERE week_id = ? ORDER BY date;',
      [weekId],
    );
    final now = DateTime.now().toIso8601String();
    _db.transaction(() {
      for (final day in days) {
        final dayId = day['id']! as int;
        for (final staffId in staffIds) {
          _db.raw.execute(
            'INSERT OR IGNORE INTO client_counts '
            '(day_id, staff_id, new_clients, existing_clients, updated_at) '
            'VALUES (?, ?, 0, 0, ?);',
            [dayId, staffId, now],
          );
        }
      }
    });
  }

  /// Returns true when everyday day cells show `0` instead of a blank
  /// placeholder. Defaults to the stored value; weeks created before this
  /// feature (or with no row) return true (hidden zeros — the current app
  /// behaviour), so existing weeks stay exactly as the user saw them.
  bool showZeros(int weekId) {
    final row = _db.raw.select('SELECT show_zeros FROM weeks WHERE id = ?;', [
      weekId,
    ]);
    return row.isEmpty || (row.first['show_zeros']! as int) != 0;
  }

  /// Sets the per-week show-zeros preference. Persisted to the database.
  void setShowZeros(int weekId, bool on) {
    _db.raw.execute(
      'UPDATE weeks SET show_zeros = ?, updated_at = ? WHERE id = ?;',
      [on ? 1 : 0, DateTime.now().toIso8601String(), weekId],
    );
  }

  /// Deletes an entire week (UI must confirm first). Days and counts cascade.
  void deleteWeek(int weekId) {
    _db.raw.execute('PRAGMA foreign_keys = ON;');
    _db.raw.execute('DELETE FROM weeks WHERE id = ?;', [weekId]);
  }

  // ---- Days ----------------------------------------------------------------

  List<WorkDay> getDays(int weekId) {
    final rows = _db.raw.select(
      'SELECT * FROM work_days WHERE week_id = ? ORDER BY date;',
      [weekId],
    );
    return rows.map(WorkDay.fromRow).toList();
  }

  WorkDay getDay(int dayId) {
    return WorkDay.fromRow(
      _db.raw.select('SELECT * FROM work_days WHERE id = ?;', [dayId]).first,
    );
  }
}

String _dateKey(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

String _displayDate(DateTime d) => '${d.day}/${d.month}/${d.year}';
