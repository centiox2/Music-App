import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/staff.dart';
import '../repositories/client_count_repository.dart';
import 'providers.dart';

export 'providers.dart';

/// Bumps whenever any data changes so dependent providers refresh.
final dataVersionProvider = NotifierProvider<DataVersionNotifier, int>(
  DataVersionNotifier.new,
);

class DataVersionNotifier extends Notifier<int> {
  @override
  int build() => 0;

  void bump() => state++;
}

/// All staff (active and inactive) in display order.
final staffListProvider = Provider<List<Staff>>((ref) {
  ref.watch(dataVersionProvider);
  return ref.read(staffRepositoryProvider).getAll();
});

/// Navigation sections, shared by sidebar (desktop) and bottom bar (mobile).
enum AppTab { home, week, history, staff, settings }

/// The shell's context, captured by [HomeShell] so helper functions outside
/// the widget tree (e.g. [jumpWeeks]) can show snackbars. Null until the
/// shell has mounted.
final shellContextProvider =
    NotifierProvider<ShellContextNotifier, BuildContext?>(
      ShellContextNotifier.new,
    );

class ShellContextNotifier extends Notifier<BuildContext?> {
  @override
  BuildContext? build() => null;

  /// Captures the shell's context (called once from [HomeShell]).
  void capture(BuildContext context) => state = context;
}

/// The currently visible tab.
final navTabProvider = NotifierProvider<NavTabNotifier, AppTab>(
  NavTabNotifier.new,
);

class NavTabNotifier extends Notifier<AppTab> {
  @override
  AppTab build() => AppTab.home;

  void set(AppTab tab) => state = tab;
}

/// The week the user is currently working with (dashboard + tracker).
final currentWeekIdProvider = NotifierProvider<CurrentWeekIdNotifier, int?>(
  CurrentWeekIdNotifier.new,
);

class CurrentWeekIdNotifier extends Notifier<int?> {
  @override
  int? build() => null;

  void set(int? id) => state = id;
}

class CurrentWeekData {
  CurrentWeekData({
    required this.week,
    required this.days,
    required this.totals,
    required this.staffTotals,
  });

  final Week week;
  final List<DayWithCounts> days;
  final WeekTotals totals;
  final List<StaffWeekTotals> staffTotals;

  DayWithCounts? dayFor(DateTime date) {
    for (final d in days) {
      if (_sameDay(d.day.date, date)) return d;
    }
    return null;
  }

  static bool _sameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;
}

/// Hydrated data for the week being worked with. Uses the explicitly
/// selected week, falling back to the most recent one — so after a restart
/// the dashboard/tracker immediately show the current week.
final currentWeekProvider = Provider<CurrentWeekData?>((ref) {
  ref.watch(dataVersionProvider);
  final weeks = ref.read(weekRepositoryProvider);
  final counts = ref.read(clientCountRepositoryProvider);
  final stats = ref.read(statisticsServiceProvider);

  CurrentWeekData? build(Week week) {
    final days = weeks
        .getDays(week.id)
        .map((d) => counts.getDayWithCounts(d.id))
        .toList();
    return CurrentWeekData(
      week: week,
      days: days,
      totals: stats.weekTotals(week),
      staffTotals: stats.staffWeekTotals(week),
    );
  }

  final id = ref.watch(currentWeekIdProvider);
  if (id != null) {
    final week = weeks.getWeek(id);
    if (week != null) return build(week);
  }
  final latest = weeks.getAllWeeks().firstOrNull;
  return latest == null ? null : build(latest);
});

/// Month-to-date totals (derived from day data, never stored).
final monthTotalsProvider = Provider<MonthTotals>((ref) {
  ref.watch(dataVersionProvider);
  return ref.read(statisticsServiceProvider).monthTotals(DateTime.now());
});

/// All weeks for the History screen (newest first) with their totals.
class WeekSummary {
  WeekSummary({required this.week, required this.totals});

  final Week week;
  final WeekTotals totals;
}

final historyProvider = Provider<List<WeekSummary>>((ref) {
  ref.watch(dataVersionProvider);
  final weeks = ref.read(weekRepositoryProvider);
  final stats = ref.read(statisticsServiceProvider);
  return weeks
      .getAllWeeks()
      .map((w) => WeekSummary(week: w, totals: stats.weekTotals(w)))
      .toList();
});

/// The app's most recent week, if any (dashboard "current week" logic).
final latestWeekProvider = Provider<Week?>((ref) {
  ref.watch(dataVersionProvider);
  final weeks = ref.read(weekRepositoryProvider).getAllWeeks();
  return weeks.isEmpty ? null : weeks.first;
});

/// Theme mode (light / dark / system), persisted via shared_preferences.
final themeModeProvider = NotifierProvider<ThemeModeNotifier, ThemeMode>(
  ThemeModeNotifier.new,
);

class ThemeModeNotifier extends Notifier<ThemeMode> {
  bool _loaded = false;

  @override
  ThemeMode build() {
    _load();
    return ThemeMode.dark;
  }

  Future<void> _load() async {
    if (_loaded) return;
    _loaded = true;

    final prefs = await SharedPreferences.getInstance();
    final index = prefs.getInt('themeMode') ?? ThemeMode.dark.index;
    if (index >= 0 && index < ThemeMode.values.length) {
      state = ThemeMode.values[index];
    }
  }

  void set(ThemeMode mode) {
    state = mode;
    SharedPreferences.getInstance().then(
      (p) => p.setInt('themeMode', mode.index),
    );
  }
}

/// Actions shared by screens (keeps business logic out of widgets).
class AppActions {
  AppActions(this._ref);

  final Ref _ref;

  /// Switches the visible section; can open a specific week in the tracker.
  void selectTab(AppTab tab, {int? weekId}) {
    if (weekId != null) {
      _ref.read(currentWeekIdProvider.notifier).set(weekId);
    }
    _ref.read(navTabProvider.notifier).set(tab);
  }

  /// Creates the week for the Monday–Friday containing [anyDay]. Returns the
  /// new week, or throws [StateError] if it already exists.
  Week createWeek(DateTime anyDay) {
    final week = _ref.read(weekRepositoryProvider).createWeekFor(anyDay);
    _touch();
    return week;
  }

  /// Saves cumulative New/Existing counts for one staff/day. The stored value
  /// is the new cumulative total (3 → 5 stores 5, not 8).
  ///
  /// Before overwriting, the previous value is remembered in the undo buffer
  /// so the user can revert a mistaken edit (Clear or Ctrl+Z restore it).
  void saveCount({
    required int dayId,
    required int staffId,
    required int newClients,
    required int existingClients,
  }) {
    final repo = _ref.read(clientCountRepositoryProvider);
    final existing = repo.getDayWithCounts(dayId).countFor(staffId);
    rememberCount(dayId, staffId, true, existing.newClients);
    rememberCount(dayId, staffId, false, existing.existingClients);
    repo.saveCount(
      dayId: dayId,
      staffId: staffId,
      newClients: newClients,
      existingClients: existingClients,
    );
    _touch();
  }

  /// Marks (or unmarks) one staff member off on one working day. Numbers
  /// already entered are kept but excluded from every total.
  void setStaffDayOff({
    required int dayId,
    required int staffId,
    required bool off,
  }) {
    _ref
        .read(timeOffRepositoryProvider)
        .setDay(dayId: dayId, staffId: staffId, off: off);
    _touch();
  }

  /// Marks (or unmarks) a staff member off on every working day of a week.
  void setStaffWeekOff({
    required int weekId,
    required int staffId,
    required bool off,
  }) {
    _ref
        .read(timeOffRepositoryProvider)
        .setAllWeekDays(weekId: weekId, staffId: staffId, off: off);
    _touch();
  }

  /// Sets whether everyday day cells show `0` instead of a blank placeholder.
  void setShowZeros(int weekId, bool on) {
    _ref.read(weekRepositoryProvider).setShowZeros(weekId, on);
    _touch();
  }

  /// Toggles the persisted preference instead of relying on a value captured
  /// by the last widget build. This keeps rapid consecutive taps from
  /// applying the same stale value twice before Riverpod rebuilds the button.
  void toggleShowZeros(int weekId) {
    final week = _ref.read(weekRepositoryProvider).getWeek(weekId);
    if (week == null) return;
    setShowZeros(weekId, !week.showZeros);
  }

  void _touch() => _ref.read(dataVersionProvider.notifier).bump();

  /// Remembers the last committed value for one count cell so the user can
  /// undo a mistaken edit (the Clear affordance and Ctrl+Z both restore it).
  void rememberCount(int dayId, int staffId, bool isNew, int value) {
    final key = '$dayId:$staffId:${isNew ? "n" : "e"}';
    _ref.read(undoProvider.notifier).remember(key, value);
  }

  /// Restores the previous value for one count cell, if one was remembered.
  /// Returns the restored value, or `null` when there is nothing to undo.
  /// The restored value is written to the database *without* re-memorizing,
  /// so the undo buffer for the other cell in the same row is preserved.
  int? undoCount(int dayId, int staffId, bool isNew) {
    final key = '$dayId:$staffId:${isNew ? "n" : "e"}';
    final restored = _ref.read(undoProvider.notifier).pop(key);
    if (restored == null) return null;
    _ref
        .read(clientCountRepositoryProvider)
        .saveCount(
          dayId: dayId,
          staffId: staffId,
          newClients: isNew ? restored : _currentNew(dayId, staffId),
          existingClients: isNew ? _currentExisting(dayId, staffId) : restored,
        );
    _ref.read(dataVersionProvider.notifier).bump();
    return restored;
  }

  /// The currently stored New value for one cell.
  int _currentNew(int dayId, int staffId) {
    return _ref
        .read(clientCountRepositoryProvider)
        .getDayWithCounts(dayId)
        .countFor(staffId)
        .newClients;
  }

  /// The currently stored Existing value for one cell.
  int _currentExisting(int dayId, int staffId) {
    return _ref
        .read(clientCountRepositoryProvider)
        .getDayWithCounts(dayId)
        .countFor(staffId)
        .existingClients;
  }

  /// Undoes the most recent count edit, regardless of which cell it was in.
  /// Returns the cell identity and restored value, or `null` when the buffer
  /// is empty. Used by the global Ctrl+Z / Cmd+Z shortcut.
  ({int dayId, int staffId, bool isNew, int value})? undoLastEdit() {
    final popped = _ref.read(undoProvider.notifier).popMostRecent();
    if (popped == null) return null;
    final (key, previous) = popped;
    final parts = key.split(':');
    final dayId = int.parse(parts[0]);
    final staffId = int.parse(parts[1]);
    final isNew = parts[2] == 'n';
    _ref
        .read(clientCountRepositoryProvider)
        .saveCount(
          dayId: dayId,
          staffId: staffId,
          newClients: isNew ? previous : _currentNew(dayId, staffId),
          existingClients: isNew ? _currentExisting(dayId, staffId) : previous,
        );
    _ref.read(dataVersionProvider.notifier).bump();
    return (dayId: dayId, staffId: staffId, isNew: isNew, value: previous);
  }
}

final appActionsProvider = Provider<AppActions>((ref) => AppActions(ref));
