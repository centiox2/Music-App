import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../design/app_theme.dart';
import '../../repositories/client_count_repository.dart';
import '../../state/app_state.dart'
    show
        AppActions,
        CurrentWeekData,
        currentWeekProvider,
        currentWeekIdProvider,
        appActionsProvider,
        shellContextProvider;
import '../../state/providers.dart';
import '../../widgets/editorial.dart';
import '../../widgets/empty_state.dart';
import '../../widgets/numeric_field.dart';
import 'new_week_dialog.dart';
import 'time_off_dialog.dart';

class WeeklyTrackerScreen extends ConsumerStatefulWidget {
  const WeeklyTrackerScreen({super.key, this.initialWeekId});

  final int? initialWeekId;

  @override
  ConsumerState<WeeklyTrackerScreen> createState() =>
      _WeeklyTrackerScreenState();
}

class _WeeklyTrackerScreenState extends ConsumerState<WeeklyTrackerScreen> {
  int? _selectedDayId;

  @override
  void initState() {
    super.initState();
    if (widget.initialWeekId != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(currentWeekIdProvider.notifier).set(widget.initialWeekId);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = ref.watch(currentWeekProvider);

    if (data == null) {
      return EmptyState(
        icon: Icons.calendar_today,
        title: 'No week selected',
        message: 'Create a new week or open one from History.',
        actionLabel: 'New Week',
        onAction: () => showNewWeekDialog(context, ref),
      );
    }

    // Keep the full week visible on a wide viewport; switch to the focused
    // one-day layout on phones and narrow tablets.
    final isDesktop = MediaQuery.sizeOf(context).width >= 920;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: isDesktop
              ? _EditorialBoard(data: data)
              : _MobileWeekView(
                  data: data,
                  selectedDayId: _selectedDayId,
                  onSelectDay: (id) => setState(() => _selectedDayId = id),
                ),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------- header ----

/// Reusable "New week" toolbar button (used by tracker + dashboard).
class NewWeekButton extends ConsumerWidget {
  const NewWeekButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return FilledButton.icon(
      onPressed: () => showNewWeekDialog(context, ref),
      icon: const Icon(Icons.add, size: 16),
      label: const Text('New week'),
    );
  }
}

/// Moves to the previous/next week, creating it on demand — same
/// semantics as the New Week dialog (duplicates open the existing week).
void jumpWeeks(WidgetRef ref, CurrentWeekData data, int delta) {
  final repo = ref.read(weekRepositoryProvider);
  final monday = data.week.startDate.add(Duration(days: 7 * delta));
  final existing = repo.findByStartDate(monday);
  if (existing != null) {
    ref.read(currentWeekIdProvider.notifier).set(existing.id);
    return;
  }
  try {
    final week = ref.read(appActionsProvider).createWeek(monday);
    ref.read(currentWeekIdProvider.notifier).set(week.id);
    if (delta > 0) {
      // A future week starts empty — say so instead of presenting what
      // looks like silently lost data.
      final ctx = ref.read(shellContextProvider);
      if (ctx != null && ctx.mounted) {
        ScaffoldMessenger.of(ctx).showSnackBar(
          const SnackBar(content: Text('New week created — it starts empty.')),
        );
      }
    }
  } on StateError {
    final week = repo.findByStartDate(monday);
    if (week != null) {
      ref.read(currentWeekIdProvider.notifier).set(week.id);
    }
  }
}

// ---------------------------------------------------------------- board -----

/// The desktop "editorial board": compact serif header, then the full
/// staff × day grid — sized to fit the viewport so names and every cell are
/// visible (and editable) without scrolling — with a pinned DAY TOTAL footer
/// and the analytics rail (by day / most clients / client mix) on the right.
class _EditorialBoard extends ConsumerStatefulWidget {
  const _EditorialBoard({required this.data});

  final CurrentWeekData data;

  @override
  ConsumerState<_EditorialBoard> createState() => _EditorialBoardState();
}

class _EditorialBoardState extends ConsumerState<_EditorialBoard> {
  final Map<String, FocusNode> _focusNodes = {};

  // Explicit controllers: the board has two vertical scrollables (staff rows
  // + analytics rail) and they must not fight over the primary one.
  final _rowsController = ScrollController();
  final _railController = ScrollController();

  FocusNode _nodeFor(int staffId, int dayIndex, bool isNew) {
    final key = '$staffId-$dayIndex-${isNew ? 'n' : 'e'}';
    return _focusNodes.putIfAbsent(key, FocusNode.new);
  }

  FocusNode _nextAfter(int staffId, int dayIndex, bool isNew) {
    if (!isNew) return _nodeFor(staffId, dayIndex + 1, true);
    return _nodeFor(staffId, dayIndex, false);
  }

  @override
  void dispose() {
    for (final node in _focusNodes.values) {
      node.dispose();
    }
    _rowsController.dispose();
    _railController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.data;
    final theme = Theme.of(context);
    final now = DateTime.now();

    final summary =
        '${data.totals.newClients} new · '
        '${data.totals.existingClients} existing · '
        '${data.totals.total} total · '
        'avg ${data.totals.dailyAverage.toStringAsFixed(1)}';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 8, 14, 8),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(Dt.rSm),
                ),
                child: Text(
                  'WEEK ${_isoWeekNumber(data.week.startDate)}',
                  style: theme.textTheme.labelSmall?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '${DateFormat('MMM d').format(data.week.startDate)} – ${DateFormat('MMM d, y').format(data.week.endDate)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              _RoundIconButton(
                icon: Icons.chevron_left,
                tooltip: 'Previous week',
                onPressed: () => jumpWeeks(ref, data, -1),
              ),
              const SizedBox(width: 5),
              _RoundIconButton(
                icon: Icons.chevron_right,
                tooltip: 'Next week',
                onPressed: () => jumpWeeks(ref, data, 1),
              ),
              const SizedBox(width: 8),
              const _ShowZerosToggle(),
            ],
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: _boardMaxWidth),
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    const railWidth = 270.0;
                    const railGap = 14.0;
                    const headerH = 48.0;
                    const footerH = 40.0;
                    // Give the entry grid the available width on ordinary
                    // laptop screens. The analytics rail moves below it until
                    // there is enough room for both without compressing cells.
                    final showRail = constraints.maxWidth >= 1380;
                    final staffCount = data.days.isEmpty
                        ? 0
                        : data.days.first.rows.length;
                    // Fit the board to the viewport: split the rows region
                    // between the staff and size the fields to match. Below the
                    // 34px floor the rows scroll instead of becoming cramped.
                    final rowsH = (constraints.maxHeight - headerH - footerH)
                        .clamp(0.0, double.infinity)
                        .toDouble();
                    final rowHeight =
                        (staffCount == 0 ? 42.0 : rowsH / staffCount)
                            .clamp(34.0, 48.0)
                            .toDouble();
                    final fieldHeight = (rowHeight - 6)
                        .clamp(26.0, 34.0)
                        .toDouble();
                    const fieldFontSize = 13.0;
                    final board = Container(
                      clipBehavior: Clip.antiAlias,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerLow,
                        border: Border.all(color: theme.colorScheme.outline),
                        borderRadius: BorderRadius.circular(Dt.rMd),
                      ),
                      child: _BoardTable(
                        data: data,
                        nodeFor: _nodeFor,
                        nextAfter: _nextAfter,
                        rowsController: _rowsController,
                        rowHeight: rowHeight,
                        fieldHeight: fieldHeight,
                        fieldFontSize: fieldFontSize,
                        needsScroll:
                            staffCount > 0 && rowsH / staffCount < 34.0,
                      ),
                    );
                    final rail = _AnalyticsRail(data: data, now: now);

                    if (!showRail) {
                      // The board contains `Expanded`s, so it needs a bounded
                      // height inside the ListView. The board draws its own
                      // header/footer, so it takes the full height here (the
                      // rowHeight math above already accounts for them); the
                      // analytics rail scrolls below.
                      return ListView(
                        children: [
                          SizedBox(height: constraints.maxHeight, child: board),
                          const SizedBox(height: Dt.s32),
                          rail,
                        ],
                      );
                    }
                    return Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(child: board),
                        const SizedBox(width: railGap),
                        SizedBox(
                          width: railWidth,
                          child: Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.surfaceContainerLow,
                              border: Border.all(
                                color: theme.colorScheme.outline,
                              ),
                              borderRadius: BorderRadius.circular(Dt.rMd),
                            ),
                            child: ScrollConfiguration(
                              // The app theme forces scrollbar thumbs visible.
                              // Override the behavior itself here so Flutter
                              // cannot insert a desktop scrollbar over this
                              // narrow rail; wheel and trackpad scrolling stay
                              // enabled.
                              behavior: const _RailScrollBehavior(),
                              child: SingleChildScrollView(
                                controller: _railController,
                                child: rail,
                              ),
                            ),
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(14, 5, 14, 7),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: _boardMaxWidth),
              child: Wrap(
                spacing: Dt.s12,
                runSpacing: Dt.s4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  // Live totals — moved up from the removed stat strip so the
                  // board itself always fits the viewport without scrolling.
                  Text(
                    summary,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Dt.textMuted(theme),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Text(
                    'Tab moves to the next box',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Dt.textMuted(theme),
                    ),
                  ),
                  Text(
                    'Blanks count as 0',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Dt.textMuted(theme),
                    ),
                  ),
                  Text(
                    'Tap a name to set time off',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Dt.textMuted(theme),
                    ),
                  ),
                  Text(
                    'Saves as you type',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Dt.textMuted(theme),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Scroll behavior for the analytics rail, which intentionally has no painted
/// scrollbar because the values are aligned against the panel's right edge.
class _RailScrollBehavior extends MaterialScrollBehavior {
  const _RailScrollBehavior();

  @override
  Widget buildScrollbar(
    BuildContext context,
    Widget child,
    ScrollableDetails details,
  ) => child;
}

/// Small round hairline icon button (the chevron pair in the header).
class _RoundIconButton extends StatelessWidget {
  const _RoundIconButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      waitDuration: const Duration(milliseconds: 400),
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(30, 30),
          maximumSize: const Size(30, 30),
          padding: EdgeInsets.zero,
          shape: const CircleBorder(),
        ),
        child: Icon(icon, size: 16),
      ),
    );
  }
}

/// Pill toggle in the board header for the per-week "show zeros" preference.
/// When on, zero values are drawn as `0` instead of the blank en-dash
/// placeholder.
class _ShowZerosToggle extends ConsumerWidget {
  const _ShowZerosToggle();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final week = ref.watch(currentWeekProvider);
    final theme = Theme.of(context);
    final showZeros = week?.week.showZeros ?? false;
    final label = showZeros ? 'Hide zeros' : 'Show zeros';
    return Padding(
      padding: const EdgeInsets.only(left: Dt.s8),
      child: InkWell(
        onTap: week == null
            ? null
            : () => ref.read(appActionsProvider).toggleShowZeros(week.week.id),
        borderRadius: BorderRadius.circular(Dt.rPill),
        child: Container(
          height: 28,
          padding: const EdgeInsets.symmetric(horizontal: Dt.s12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(Dt.rPill),
            border: Border.all(color: theme.colorScheme.outlineVariant),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                showZeros ? Icons.visibility : Icons.visibility_off,
                size: 14,
                color: showZeros
                    ? theme.colorScheme.primary
                    : Dt.textMuted(theme),
              ),
              const SizedBox(width: Dt.s8),
              Text(
                label,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: showZeros
                      ? theme.colorScheme.primary
                      : Dt.textMuted(theme),
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------- table -----

/// Grid geometry shared by header / rows / footer. Day columns flex.
const double _nameWidth = 168;
const double _totalWidth = 66;

/// The board is wider than the reading cap — the mockup runs it at 1440.
const double _boardMaxWidth = 1440;

class _BoardTable extends ConsumerWidget {
  const _BoardTable({
    required this.data,
    required this.nodeFor,
    required this.nextAfter,
    required this.rowsController,
    required this.rowHeight,
    required this.fieldHeight,
    required this.fieldFontSize,
    required this.needsScroll,
  });

  final CurrentWeekData data;
  final FocusNode Function(int, int, bool) nodeFor;
  final FocusNode Function(int, int, bool) nextAfter;
  final ScrollController rowsController;
  final double rowHeight;
  final double fieldHeight;
  final double fieldFontSize;

  /// True when the team is too long for the viewport even at the 34px
  /// minimum row height — rows scroll as before.
  final bool needsScroll;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = this.data;
    final theme = Theme.of(context);
    final now = DateTime.now();
    final staffRows = data.days.isEmpty
        ? const <NamedClientCount>[]
        : data.days.first.rows;
    final border = BorderSide(color: theme.colorScheme.outline);
    final dark = theme.brightness == Brightness.dark;

    // ---- pinned day header ---------------------------------------------
    Widget dayHeaderCell(DayWithCounts d) {
      final isToday = _sameDay(d.day.date, now);
      final accent = Dt.newGreen(theme.brightness);
      return Expanded(
        child: Container(
          decoration: BoxDecoration(
            border: Border(left: border),
            color: isToday
                ? Dt.newGreen(theme.brightness)
                      .withValues(alpha: dark ? 0.07 : 0.06)
                : null,
          ),
          padding: const EdgeInsets.fromLTRB(0, 6, 0, 5),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Flexible(
                    child: Text(
                      DateFormat('EEE').format(d.day.date),
                      maxLines: 1,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: isToday ? accent : theme.colorScheme.onSurface,
                      ),
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    isToday ? 'Today' : DateFormat('MMM d').format(d.day.date),
                    style: TextStyle(fontSize: 11, color: Dt.textMuted(theme)),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  Expanded(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        MeaningDot(accent, size: 6),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            'New',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: _subLabel(theme, accent),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        MeaningDot(Dt.existingBlue(theme.brightness), size: 6),
                        const SizedBox(width: 4),
                        Flexible(
                          child: Text(
                            'Exist.',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: _subLabel(
                              theme,
                              Dt.existingBlue(theme.brightness),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    }

    final headerRow = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: _nameWidth,
              child: Padding(
                padding: const EdgeInsets.only(left: Dt.s12),
                child: Text(
                  'STAFF',
                  maxLines: 1,
                  style: microLabel(theme).copyWith(height: 2.2),
                ),
              ),
            ),
            for (final d in data.days) dayHeaderCell(d),
            Container(
              width: _totalWidth,
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                'TOTAL',
                textAlign: TextAlign.center,
                maxLines: 1,
                style: microLabel(theme),
              ),
            ),
          ],
        ),
        Container(height: 1, color: theme.colorScheme.outline),
      ],
    );

    // ---- pinned totals footer -------------------------------------------
    Widget dayFooterCell(DayWithCounts d) {
      final isToday = _sameDay(d.day.date, now);
      // Time-off marks: only staff actually working count here.
      final working = d.workingRows;
      final n = working.fold<int>(0, (a, r) => a + r.newClients);
      final e = working.fold<int>(0, (a, r) => a + r.existingClients);
      final logged = working.any((r) => r.total > 0);
      return Expanded(
        child: Container(
          decoration: BoxDecoration(
            border: Border(left: border),
            color: isToday
                ? Dt.newGreen(theme.brightness)
                      .withValues(alpha: dark ? 0.07 : 0.06)
                : null,
          ),
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                logged ? '$n' : '–',
                style: Dt.mono(
                  context,
                  size: 13,
                  color: Dt.newGreen(theme.brightness),
                ),
              ),
              Text(
                ' + ',
                style: TextStyle(color: Dt.textFaint(theme), fontSize: 12),
              ),
              Text(
                logged ? '$e' : '–',
                style: Dt.mono(
                  context,
                  size: 13,
                  color: Dt.existingBlue(theme.brightness),
                ),
              ),
              Text(
                ' = ',
                style: TextStyle(color: Dt.textFaint(theme), fontSize: 12),
              ),
              Text(
                logged ? '${n + e}' : '–',
                style: Dt.numeral(context, size: 22),
              ),
            ],
          ),
        ),
      );
    }

    final footerRow = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(height: 1, color: theme.colorScheme.outline),
        Row(
          children: [
            SizedBox(
              width: _nameWidth,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(Dt.s12, 12, Dt.s8, 12),
                child: Text('DAY TOTAL', style: microLabel(theme)),
              ),
            ),
            for (final d in data.days) dayFooterCell(d),
            Container(
              width: _totalWidth,
              padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
              child: Text(
                '${data.totals.total}',
                textAlign: TextAlign.center,
                style: Dt.numeral(
                  context,
                  size: 26,
                  color: Dt.newGreen(theme.brightness),
                ),
              ),
            ),
          ],
        ),
      ],
    );

    // ---- layout: pinned header / rows / pinned footer -------------------
    Widget staffRow(int i) => Container(
      // In the scroll branch rows need a fixed height; in the fit branch
      // the row is wrapped in Expanded below and fills the REAL available
      // space (the rowHeight estimate only sizes the input fields).
      height: needsScroll ? rowHeight : null,
      color: i.isOdd
          ? theme.colorScheme.surfaceContainerLow.withValues(
              alpha: dark ? 0.55 : 0.5,
            )
          : null,
      child: _BoardStaffRow(
        key: ValueKey('staff-${staffRows[i].staffId}'),
        data: data,
        staff: staffRows[i],
        nodeFor: nodeFor,
        nextAfter: nextAfter,
        rowHeight: rowHeight,
        fieldHeight: fieldHeight,
        fieldFontSize: fieldFontSize,
      ),
    );

    final rowsBlock = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (needsScroll)
          for (var i = 0; i < staffRows.length; i++) staffRow(i)
        else
          for (var i = 0; i < staffRows.length; i++)
            Expanded(child: staffRow(i)),
      ],
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        headerRow,
        Expanded(
          child: needsScroll
              ? Scrollbar(
                  controller: rowsController,
                  thumbVisibility: true,
                  child: SingleChildScrollView(
                    controller: rowsController,
                    child: rowsBlock,
                  ),
                )
              : rowsBlock,
        ),
        footerRow,
      ],
    );
  }

  TextStyle _subLabel(ThemeData theme, Color color) => TextStyle(
    fontSize: 10,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.5,
    color: color,
  );
}

/// One staff row: name + 5 day cell-pairs + serif weekly TOTAL.
class _BoardStaffRow extends ConsumerWidget {
  const _BoardStaffRow({
    super.key,
    required this.data,
    required this.staff,
    required this.nodeFor,
    required this.nextAfter,
    required this.rowHeight,
    required this.fieldHeight,
    required this.fieldFontSize,
  });

  final CurrentWeekData data;
  final NamedClientCount staff;
  final FocusNode Function(int, int, bool) nodeFor;
  final FocusNode Function(int, int, bool) nextAfter;
  final double rowHeight;
  final double fieldHeight;
  final double fieldFontSize;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final actions = ref.read(appActionsProvider);
    final theme = Theme.of(context);
    final border = BorderSide(color: theme.colorScheme.outline);
    final dark = theme.brightness == Brightness.dark;

    final offDayLabels = <String>[
      for (final d in data.days)
        if (d.isOff(staff.staffId)) DateFormat('EEE').format(d.day.date),
    ];
    final isOffSomewhere = offDayLabels.isNotEmpty;

    // Weekly total counts only the days this staff member is working.
    var weekN = 0, weekE = 0;
    for (final d in data.days) {
      if (d.isOff(staff.staffId)) continue;
      final c = d.countFor(staff.staffId);
      weekN += c.newClients;
      weekE += c.existingClients;
    }

    return SizedBox(
      height: rowHeight,
      child: Row(
        children: [
          SizedBox(
            width: _nameWidth,
            child: Tooltip(
              message: 'Tap to set time off',
              waitDuration: const Duration(milliseconds: 500),
              child: InkWell(
                onTap: () => showTimeOffDialog(
                  context,
                  ref,
                  staffId: staff.staffId,
                  staffName: staff.staffName,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: Dt.s12),
                  child: Row(
                    children: [
                      Flexible(
                        child: Text(
                          staff.staffName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyLarge?.copyWith(
                            fontWeight: FontWeight.w500,
                            color: isOffSomewhere
                                ? Dt.textMuted(theme)
                                : theme.colorScheme.onSurface,
                          ),
                        ),
                      ),
                      if (isOffSomewhere) ...[
                        const SizedBox(width: 6),
                        Tooltip(
                          message: 'Off: ${offDayLabels.join(" · ")}',
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 5,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(Dt.rSm),
                              color: theme.colorScheme.surfaceContainerHighest
                                  .withValues(alpha: dark ? 0.9 : 1),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.event_busy,
                                  size: 11,
                                  color: Dt.textMuted(theme),
                                ),
                                const SizedBox(width: 3),
                                Text(
                                  '${offDayLabels.length}',
                                  style: Dt.mono(
                                    context,
                                    size: 11,
                                    color: Dt.textMuted(theme),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
          for (var i = 0; i < data.days.length; i++)
            Expanded(
              child: data.days[i].isOff(staff.staffId)
                  ? Container(
                      decoration: BoxDecoration(border: Border(left: border)),
                      alignment: Alignment.center,
                      child: Text(
                        'Off',
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontStyle: FontStyle.italic,
                          color: Dt.textMuted(theme),
                        ),
                      ),
                    )
                  : Container(
                      decoration: BoxDecoration(border: Border(left: border)),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 3,
                        vertical: 4,
                      ),
                      child: Row(
                        children: [
                          for (final isNew in [true, false])
                            Expanded(
                              child: NumericField(
                                value: isNew
                                    ? data.days[i]
                                          .countFor(staff.staffId)
                                          .newClients
                                    : data.days[i]
                                          .countFor(staff.staffId)
                                          .existingClients,
                                focusNode: nodeFor(staff.staffId, i, isNew),
                                nextFocusNode: nextAfter(
                                  staff.staffId,
                                  i,
                                  isNew,
                                ),
                                width: double.infinity,
                                height: fieldHeight,
                                fontSize: fieldFontSize,
                                accent: isNew
                                    ? Dt.newGreen(theme.brightness)
                                    : Dt.existingBlue(theme.brightness),
                                valueColor: isNew
                                    ? Dt.newGreenBright(theme.brightness)
                                    : Dt.existingBlueBright(theme.brightness),
                                showZero: data.week.showZeros,
                                placeholder: '–',
                                onClear: () => actions.undoCount(
                                  data.days[i].day.id,
                                  staff.staffId,
                                  isNew,
                                ),
                                onChanged: (v) => actions.saveCount(
                                  dayId: data.days[i].day.id,
                                  staffId: staff.staffId,
                                  newClients: isNew
                                      ? v
                                      : data.days[i]
                                            .countFor(staff.staffId)
                                            .newClients,
                                  existingClients: isNew
                                      ? data.days[i]
                                            .countFor(staff.staffId)
                                            .existingClients
                                      : v,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
            ),
          Container(
            width: _totalWidth,
            decoration: BoxDecoration(border: Border(left: border)),
            alignment: Alignment.center,
            child: Text(
              '${weekN + weekE}',
              style: Dt.numeral(context, size: 24),
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- rail ------

/// The right-hand analytics column: BY DAY chart, MOST CLIENTS leaderboard,
/// CLIENT MIX bar.
class _AnalyticsRail extends ConsumerWidget {
  const _AnalyticsRail({required this.data, required this.now});

  final CurrentWeekData data;
  final DateTime now;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final staffRows = data.days.isEmpty
        ? const <NamedClientCount>[]
        : data.days.first.rows;

    final chart = [
      for (final d in data.days)
        DayBarData(
          label: DateFormat('EEE').format(d.day.date),
          isToday: _sameDay(d.day.date, now),
          newClients: d.workingRows.fold<int>(0, (a, r) => a + r.newClients),
          existingClients: d.workingRows.fold<int>(
            0,
            (a, r) => a + r.existingClients,
          ),
        ),
    ];

    // Weekly totals per staff (off days contribute nothing), sorted.
    int effectiveTotal(NamedClientCount row) => data.days.fold<int>(
      0,
      (a, d) => a + (d.isOff(row.staffId) ? 0 : d.countFor(row.staffId).total),
    );
    final leaders = <({String name, int total})>[
      for (final row in staffRows)
        (name: row.staffName, total: effectiveTotal(row)),
    ]..sort((a, b) => b.total.compareTo(a.total));
    final top5 = leaders.take(5).toList();
    final topTotal = top5.isEmpty ? 0 : top5.first.total;

    // ---- week facts -------------------------------------------------------
    DayWithCounts? bestDay;
    var bestTotal = 0;
    for (final d in data.days) {
      final t = d.workingRows.fold<int>(0, (a, r) => a + r.total);
      if (t > bestTotal) {
        bestTotal = t;
        bestDay = d;
      }
    }
    final spanText = data.days.isEmpty
        ? '–'
        : '${DateFormat('d MMM').format(data.days.first.day.date)} – '
              "${DateFormat('d MMM yyyy').format(data.days.last.day.date)}";
    final offMarks = data.days.fold<int>(0, (a, d) => a + d.offStaffIds.length);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const AsideCaption('This week'),
        const SizedBox(height: Dt.s8),
        _FactRow(label: 'Spanning', value: spanText),
        _FactRow(label: 'Staff on board', value: '${staffRows.length}'),
        _FactRow(label: 'Off days', value: offMarks == 0 ? '–' : '$offMarks'),
        _FactRow(
          label: 'Best day',
          value: bestDay == null
              ? '–'
              : "${DateFormat('EEE').format(bestDay.day.date)} · $bestTotal",
          last: true,
        ),
        const SizedBox(height: Dt.s28),
        const AsideCaption('By day'),
        const SizedBox(height: Dt.s16),
        StackedDayBars(days: chart),
        const SizedBox(height: Dt.s28),
        const AsideCaption('Most clients this week'),
        const SizedBox(height: Dt.s8),
        if (topTotal == 0)
          Text(
            'No numbers yet this week.',
            style: theme.textTheme.bodySmall?.copyWith(
              color: Dt.textMuted(theme),
            ),
          )
        else ...[
          for (var i = 0; i < top5.length; i++)
            LeaderRow(
              rank: i + 1,
              name: top5[i].name,
              value: top5[i].total,
              pct: topTotal == 0 ? 0 : (top5[i].total / topTotal * 100).round(),
            ),
        ],
        const SizedBox(height: Dt.s28),
        const AsideCaption('Client mix'),
        const SizedBox(height: Dt.s12),
        MixBar(
          newCount: data.totals.newClients,
          existingCount: data.totals.existingClients,
        ),
      ],
    );
  }
}

/// One label/value line of the week-facts block (hairline separators).
class _FactRow extends StatelessWidget {
  const _FactRow({required this.label, required this.value, this.last = false});

  final String label;
  final String value;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 7),
      decoration: last
          ? null
          : BoxDecoration(
              border: Border(
                bottom: BorderSide(color: theme.colorScheme.outline),
              ),
            ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Expanded(
            child: Text(
              label,
              style: theme.textTheme.bodySmall?.copyWith(
                color: Dt.textMuted(theme),
              ),
            ),
          ),
          const SizedBox(width: Dt.s12),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.right,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Dt.tab(theme.textTheme.bodyMedium!)
                  .copyWith(fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- mobile ----

class _MobileWeekView extends StatefulWidget {
  const _MobileWeekView({
    required this.data,
    required this.selectedDayId,
    required this.onSelectDay,
  });

  final CurrentWeekData data;
  final int? selectedDayId;
  final ValueChanged<int> onSelectDay;

  @override
  State<_MobileWeekView> createState() => _MobileWeekViewState();
}

class _MobileWeekViewState extends State<_MobileWeekView> {
  int? _resolvedDayId;

  @override
  Widget build(BuildContext context) {
    final days = widget.data.days;
    final theme = Theme.of(context);
    final today = DateTime.now();
    final todayEntry = days
        .where((d) => _sameDay(d.day.date, today))
        .toList()
        .firstOrNull;

    var selectedId = widget.selectedDayId ?? _resolvedDayId;
    selectedId ??= todayEntry?.day.id ?? days.first.day.id;
    if (!days.any((d) => d.day.id == selectedId)) {
      selectedId = days.first.day.id;
    }
    final index = days.indexWhere((d) => d.day.id == selectedId);
    final day = days[index];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: Dt.s16,
            vertical: Dt.s8,
          ),
          // Horizontal scroll on very narrow screens so day labels never wrap.
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SegmentedButton<int>(
              segments: [
                for (var i = 0; i < days.length; i++)
                  ButtonSegment(
                    value: i,
                    label: Text(
                      DateFormat('EEE').format(days[i].day.date),
                      maxLines: 1,
                    ),
                  ),
              ],
              selected: {index},
              onSelectionChanged: (selection) {
                setState(() {
                  _resolvedDayId = days[selection.first].day.id;
                });
                widget.onSelectDay(days[selection.first].day.id);
              },
            ),
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(Dt.s16, Dt.s16, Dt.s16, Dt.s32),
            children: [
              Text(
                '${DateFormat('EEEE').format(day.day.date)} · ${DateFormat('d MMMM y').format(day.day.date)}',
                style: Dt.display(context, size: 26),
              ),
              const SizedBox(height: Dt.s4),
              Text(
                'Tap a name to set time off',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: Dt.textMuted(theme),
                ),
              ),
              const SizedBox(height: Dt.s12),
              for (final row in day.rows)
                _MobileStaffRow(
                  key: ValueKey('mobile-${day.day.id}-${row.staffId}'),
                  day: day,
                  row: row,
                  showZero: widget.data.week.showZeros,
                ),
              const SizedBox(height: Dt.s8),
              _daySummaryCard(context, day),
            ],
          ),
        ),
      ],
    );
  }

  Widget _daySummaryCard(BuildContext context, DayWithCounts day) {
    final theme = Theme.of(context);
    final working = day.workingRows;
    final n = working.fold<int>(0, (a, r) => a + r.newClients);
    final e = working.fold<int>(0, (a, r) => a + r.existingClients);
    return Container(
      padding: const EdgeInsets.all(Dt.s12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(Dt.rMd),
        border: Border.all(color: theme.colorScheme.outline),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _sum(context, 'New Total', '$n', Dt.newGreen(theme.brightness)),
          _sum(
            context,
            'Existing Total',
            '$e',
            Dt.existingBlue(theme.brightness),
          ),
          _sum(context, 'Day Total', '${n + e}', null),
        ],
      ),
    );
  }

  Widget _sum(BuildContext context, String label, String value, Color? color) {
    final theme = Theme.of(context);
    return Column(
      children: [
        Text(label, style: microLabel(theme)),
        const SizedBox(height: Dt.s4),
        Text(value, style: Dt.numeral(context, size: 26, color: color)),
      ],
    );
  }
}

bool _sameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

/// One compact labelled count field used by the mobile staff row.
class InlineCountPair extends StatelessWidget {
  const InlineCountPair({
    super.key,
    required this.isNew,
    required this.newClients,
    required this.existingClients,
    required this.showZero,
    required this.actions,
    required this.day,
    required this.row,
    required this.newColor,
    required this.newColorBright,
    required this.existingColor,
    required this.existingColorBright,
    required this.context,
    required this.theme,
  });

  final bool isNew;
  final int newClients;
  final int existingClients;
  final bool showZero;
  final AppActions actions;
  final DayWithCounts day;
  final NamedClientCount row;
  final Color newColor;
  final Color newColorBright;
  final Color existingColor;
  final Color existingColorBright;
  final BuildContext context;
  final ThemeData theme;

  @override
  Widget build(BuildContext context) {
    final accent = isNew ? newColor : existingColor;
    final bright = isNew ? newColorBright : existingColorBright;
    final v = isNew ? newClients : existingClients;
    final label = isNew ? 'New' : 'Exist.';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Row(
          children: [
            MeaningDot(accent),
            const SizedBox(width: Dt.s4),
            Text(label, style: microLabel(theme)),
          ],
        ),
        const SizedBox(height: Dt.s4),
        NumericField(
          value: v,
          showZero: showZero,
          width: 64,
          accent: accent,
          valueColor: bright,
          placeholder: '–',
          onClear: () => actions.undoCount(day.day.id, row.staffId, isNew),
          onChanged: (nv) => actions.saveCount(
            dayId: day.day.id,
            staffId: row.staffId,
            newClients: isNew ? nv : newClients,
            existingClients: isNew ? existingClients : nv,
          ),
        ),
      ],
    );
  }
}

class _MobileStaffRow extends ConsumerWidget {
  const _MobileStaffRow({
    super.key,
    required this.day,
    required this.row,
    required this.showZero,
  });

  final DayWithCounts day;
  final NamedClientCount row;
  final bool showZero;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final actions = ref.read(appActionsProvider);
    final theme = Theme.of(context);
    final isOff = day.isOff(row.staffId);

    return Container(
      margin: const EdgeInsets.only(bottom: Dt.s8),
      padding: const EdgeInsets.symmetric(horizontal: Dt.s12, vertical: Dt.s12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(Dt.rMd),
        border: Border.all(color: theme.colorScheme.outline),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 3,
            child: InkWell(
              onTap: () => showTimeOffDialog(
                context,
                ref,
                staffId: row.staffId,
                staffName: row.staffName,
              ),
              child: Text(
                row.staffName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w500,
                  color: isOff
                      ? Dt.textMuted(theme)
                      : theme.colorScheme.onSurface,
                ),
              ),
            ),
          ),
          const SizedBox(width: Dt.s8),
          if (isOff)
            Text(
              'Off',
              style: theme.textTheme.bodySmall?.copyWith(
                fontStyle: FontStyle.italic,
                color: Dt.textMuted(theme),
              ),
            )
          else ...[
            InlineCountPair(
              isNew: true,
              newClients: row.newClients,
              existingClients: row.existingClients,
              showZero: showZero,
              actions: actions,
              day: day,
              row: row,
              newColor: Dt.newGreen(theme.brightness),
              newColorBright: Dt.newGreenBright(theme.brightness),
              existingColor: Dt.existingBlue(theme.brightness),
              existingColorBright: Dt.existingBlueBright(theme.brightness),
              context: context,
              theme: theme,
            ),
            const SizedBox(width: Dt.s8),
            InlineCountPair(
              isNew: false,
              newClients: row.newClients,
              existingClients: row.existingClients,
              showZero: showZero,
              actions: actions,
              day: day,
              row: row,
              newColor: Dt.newGreen(theme.brightness),
              newColorBright: Dt.newGreenBright(theme.brightness),
              existingColor: Dt.existingBlue(theme.brightness),
              existingColorBright: Dt.existingBlueBright(theme.brightness),
              context: context,
              theme: theme,
            ),
            const SizedBox(width: Dt.s8),
            Text('${row.total}', style: Dt.numeral(context, size: 22)),
          ],
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- helpers ---

/// ISO-8601 week number (the spreadsheet's "Week 41" convention).
int _isoWeekNumber(DateTime date) {
  final ordinal = date.difference(DateTime(date.year, 1, 1)).inDays + 1;
  return ((ordinal - date.weekday + 10) ~/ 7);
}
