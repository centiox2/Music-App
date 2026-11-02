import '../database/database.dart';
import '../models/staff.dart';
import 'time_off_repository.dart';

/// Data access for staff records.
class StaffRepository {
  StaffRepository(this._db);

  final AppDatabase _db;

  /// All staff, optionally including deactivated ones, ordered by
  /// display_order (then name for stable ties).
  List<Staff> getAll({bool includeInactive = true}) {
    final rows = _db.raw.select(
      'SELECT * FROM staff ${includeInactive ? '' : 'WHERE active = 1'} '
      'ORDER BY display_order, name COLLATE NOCASE;',
    );
    return rows.map(Staff.fromRow).toList();
  }

  /// Active staff only — used when populating a brand-new week.
  List<Staff> getActive() => getAll(includeInactive: false);

  Staff getById(int id) {
    final rows = _db.raw.select('SELECT * FROM staff WHERE id = ?;', [id]);
    if (rows.isEmpty) {
      throw StateError('Staff #$id not found');
    }
    return Staff.fromRow(rows.first);
  }

  /// Returns the new staff id.
  int add(String name) {
    final trimmed = name.trim();
    if (trimmed.isEmpty) {
      throw ArgumentError('Staff name cannot be empty.');
    }
    final order = _db.raw
        .select('SELECT COALESCE(MAX(display_order), -1) + 1 AS next '
            'FROM staff;')
        .first['next']! as int;
    _db.raw.execute(
      'INSERT INTO staff (name, active, display_order, created_at) '
      'VALUES (?, 1, ?, ?);',
      [trimmed, order, DateTime.now().toIso8601String()],
    );
    return _db.raw.lastInsertRowId;
  }

  void rename(int id, String name) {
    final trimmed = name.trim();
    if (trimmed.isEmpty) {
      throw ArgumentError('Staff name cannot be empty.');
    }
    _db.raw.execute('UPDATE staff SET name = ? WHERE id = ?;',
        [trimmed, id]);
  }

  void setActive(int id, bool active) {
    _db.raw.execute('UPDATE staff SET active = ? WHERE id = ?;',
        [active ? 1 : 0, id]);
  }

  /// Permanently deletes a staff member. Client counts keep the staff name
  /// (denormalized) via [deleteWithHistory] handling — the UI must confirm
  /// before calling this.
  void delete(int id) {
    _db.transaction(() {
      TimeOffRepository(_db).deleteForStaff(id);
      _db.raw.execute('DELETE FROM client_counts WHERE staff_id = ?;', [id]);
      _db.raw.execute('DELETE FROM staff WHERE id = ?;', [id]);
    });
  }

  /// Persists a full new ordering of staff by id.
  void reorder(List<int> orderedIds) {
    _db.transaction(() {
      for (var i = 0; i < orderedIds.length; i++) {
        _db.raw.execute(
          'UPDATE staff SET display_order = ? WHERE id = ?;',
          [i, orderedIds[i]],
        );
      }
    });
  }

  void moveUp(Staff staff) {
    final all = getAll();
    final index = all.indexWhere((s) => s.id == staff.id);
    if (index <= 0) return;
    final reordered = [...all]..swap(index, index - 1);
    reorder(reordered.map((s) => s.id).toList());
  }

  void moveDown(Staff staff) {
    final all = getAll();
    final index = all.indexWhere((s) => s.id == staff.id);
    if (index < 0 || index >= all.length - 1) return;
    final reordered = [...all]..swap(index, index + 1);
    reorder(reordered.map((s) => s.id).toList());
  }
}

extension _ListSwap<T> on List<T> {
  void swap(int i, int j) {
    final tmp = this[i];
    this[i] = this[j];
    this[j] = tmp;
  }
}
