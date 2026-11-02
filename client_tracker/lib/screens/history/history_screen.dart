import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../design/app_theme.dart';
import '../../state/app_state.dart';
import '../../widgets/editorial.dart';
import '../../widgets/empty_state.dart';
import '../../widgets/week_label.dart';
import '../weekly_tracker/new_week_dialog.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  String _query = '';
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final weeks = ref.watch(historyProvider);
    final filtered = _query.isEmpty
        ? weeks
        : weeks.where((w) => weekMatchesQuery(w, _query)).toList();
    final now = DateTime.now();
    final theme = Theme.of(context);
    final gutter = editorialGutter(context);

    if (weeks.isEmpty) {
      return EmptyState(
        icon: Icons.history,
        title: 'No weeks yet',
        message: 'Create your first week to see it here.',
        actionLabel: 'New Week',
        onAction: () => showNewWeekDialog(context, ref),
      );
    }

    // Group by the month each week *starts* in (the mockup's month sections).
    final groups = <({String month, List<WeekSummary> weeks})>[];
    for (final w in filtered) {
      final month = DateFormat('MMMM y').format(w.week.startDate);
      final last = groups.isEmpty ? null : groups.last;
      if (last != null && last.month == month) {
        last.weeks.add(w);
      } else {
        groups.add((month: month, weeks: [w]));
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: EdgeInsets.fromLTRB(gutter, 36, gutter, 0),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Dt.contentMaxWidth),
              child: EditorialHeader(
                cap: '${weeks.length} weeks recorded',
                title: 'Past ',
                accent: 'weeks',
                actions: [
                  _SearchField(
                    controller: _searchController,
                    query: _query,
                    resultLabel: _query.isEmpty
                        ? null
                        : '${filtered.length} of ${weeks.length}',
                    onChanged: (v) => setState(() => _query = v),
                    onClear: () {
                      _searchController.clear();
                      setState(() => _query = '');
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: Dt.s24),
        Expanded(
          child: Scrollbar(
            thumbVisibility: true,
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(gutter, 0, gutter, Dt.s44),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: Dt.contentMaxWidth,
                  ),
                  child: filtered.isEmpty
                      ? Padding(
                          padding: const EdgeInsets.only(top: Dt.s32),
                          child: Text(
                            'No weeks match "$_query".',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: Dt.textMuted(theme),
                            ),
                          ),
                        )
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            _TableHeader(),
                            for (final g in groups) ...[
                              Padding(
                                padding: const EdgeInsets.fromLTRB(0, 22, 0, 8),
                                child: Text(
                                  g.month,
                                  style: Dt.display(
                                    context,
                                    size: 26,
                                    italic: true,
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                ),
                              ),
                              for (final w in g.weeks)
                                _WeekRow(
                                  key: ValueKey('week-${w.week.id}'),
                                  summary: w,
                                  isCurrent: w.week.containsDate(now),
                                  onOpen: () => _openWeek(w.week.id),
                                  onDelete: () => _confirmDelete(w.week.id),
                                ),
                            ],
                          ],
                        ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  void _openWeek(int weekId) {
    ref.read(appActionsProvider).selectTab(AppTab.week, weekId: weekId);
  }

  Future<void> _confirmDelete(int weekId) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete week?'),
        content: const Text(
          'This permanently removes the week, its five days and all recorded '
          'client numbers. This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      ref.read(weekRepositoryProvider).deleteWeek(weekId);
      ref.read(dataVersionProvider.notifier).bump();
    }
  }
}

/// Matches a week summary against a search query.
///
/// Weeks are searchable by their month-name label ("September") AND by any
/// date written as DD/MM/YYYY, D/M/YYYY or DD/MM (year inferred) — matching
/// the format shown in the app and promised by the search field's hint.
bool weekMatchesQuery(WeekSummary summary, String rawQuery) {
  final query = rawQuery.trim().toLowerCase();
  if (query.isEmpty) return true;
  final label = weekRangeLabel(
    summary.week.startDate,
    summary.week.endDate,
  ).toLowerCase();
  if (label.contains(query)) return true;

  // Date search: accept DD/MM/YYYY, D/M/YYYY or DD/MM (current year).
  final datePart = query.split(RegExp(r'\s+')).last;
  final m = RegExp(r'^(\d{1,2})/(\d{1,2})(?:/(\d{4}))?$').firstMatch(datePart);
  if (m != null) {
    final day = int.parse(m.group(1)!);
    final month = int.parse(m.group(2)!);
    final year = m.group(3) != null
        ? int.parse(m.group(3)!)
        : DateTime.now().year;
    if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
      final start = summary.week.startDate;
      final end = summary.week.endDate;
      // Iterate the actual dates so a week crossing New Year matches searches
      // for both calendar years instead of requiring every date to share the
      // week's start year.
      final searchYear = m.group(3) == null ? DateTime.now().year : year;
      for (var d = start; !d.isAfter(end); d = d.add(const Duration(days: 1))) {
        if (d.day == day && d.month == month && d.year == searchYear) {
          return true;
        }
      }
    }
  }
  return false;
}

// ---------------------------------------------------------------- widgets ---

/// Pill search field with magnifier + clear button (the header's search).
class _SearchField extends StatelessWidget {
  const _SearchField({
    required this.controller,
    required this.query,
    required this.onChanged,
    required this.onClear,
    this.resultLabel,
  });

  final TextEditingController controller;
  final String query;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;
  final String? resultLabel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 420),
      child: Row(
        children: [
          Expanded(
            child: SizedBox(
              height: 44,
              child: TextField(
                controller: controller,
                style: theme.textTheme.bodyMedium,
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.search, size: 17),
                  prefixIconConstraints: const BoxConstraints(
                    minWidth: 44,
                    minHeight: 44,
                  ),
                  suffixIcon: query.isEmpty
                      ? null
                      : IconButton(
                          tooltip: 'Clear search',
                          visualDensity: VisualDensity.compact,
                          icon: const Icon(Icons.close, size: 16),
                          onPressed: onClear,
                        ),
                  hintText:
                      'Search by month or date, e.g. September or 28/09/2026',
                ),
                onChanged: onChanged,
              ),
            ),
          ),
          if (resultLabel != null) ...[
            const SizedBox(width: Dt.s12),
            Text(
              resultLabel!,
              style: Dt.mono(context, size: 12, color: Dt.textMuted(theme)),
            ),
          ],
        ],
      ),
    );
  }
}

/// The WEEK / NEW / EXISTING / TOTAL / MON–FRI table caption row.
class _TableHeader extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget cap(String text, {Widget? leading, bool right = false}) {
      final label = Text(
        text.toUpperCase(),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: microLabel(theme),
      );
      return Row(
        mainAxisAlignment: right
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          ?leading,
          if (leading != null) const SizedBox(width: Dt.s8),
          Flexible(child: label),
        ],
      );
    }

    return Container(
      height: 40,
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
      ),
      child: Row(
        children: [
          Expanded(flex: 6, child: cap('Week')),
          Expanded(
            flex: 2,
            child: cap(
              'New',
              right: true,
              leading: MeaningDot(Dt.newGreen(theme.brightness), size: 8),
            ),
          ),
          Expanded(
            flex: 2,
            child: cap(
              'Existing',
              right: true,
              leading: MeaningDot(Dt.existingBlue(theme.brightness), size: 8),
            ),
          ),
          Expanded(flex: 2, child: cap('Total', right: true)),
          Expanded(
            flex: 4,
            child: Padding(
              padding: const EdgeInsets.only(left: Dt.s24),
              child: cap('Mon – Fri'),
            ),
          ),
          Expanded(flex: 3, child: const SizedBox()),
        ],
      ),
    );
  }
}

/// One week row (title + range, duotone serif figures, day bars, actions).
/// Collapses to a stacked card below ~880px.
class _WeekRow extends ConsumerWidget {
  const _WeekRow({
    super.key,
    required this.summary,
    required this.isCurrent,
    required this.onOpen,
    required this.onDelete,
  });

  final WeekSummary summary;
  final bool isCurrent;
  final VoidCallback onOpen;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);

    // Per-day totals in Mon–Fri order for the mini bars.
    final days = ref.watch(weekRepositoryProvider).getDays(summary.week.id);
    final perDay = <int?>[
      for (final d in days) summary.totals.perDay[d.id]?.total,
    ];

    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 760;
        final title = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                weekRangeLabel(summary.week.startDate, summary.week.endDate),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Dt.display(context, size: 26),
              ),
            ),
          ],
        );
        final range = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                '${displayDate(summary.week.startDate)} – '
                '${displayDate(summary.week.endDate)}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Dt.mono(context, size: 12, color: Dt.textMuted(theme)),
              ),
            ),
            if (isCurrent) ...[
              const SizedBox(width: 10),
              const PillChip('This week'),
            ],
          ],
        );

        final newStat = Text(
          '${summary.totals.newClients}',
          textAlign: TextAlign.right,
          style: Dt.numeral(
            context,
            size: 30,
            color: Dt.newGreen(theme.brightness),
          ),
        );
        final existingStat = Text(
          '${summary.totals.existingClients}',
          textAlign: TextAlign.right,
          style: Dt.numeral(
            context,
            size: 30,
            color: Dt.existingBlue(theme.brightness),
          ),
        );
        final totalStat = Text(
          '${summary.totals.total}',
          textAlign: TextAlign.right,
          style: Dt.numeral(context, size: 30),
        );
        final miniBars = MiniBars(values: perDay);
        final actions = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            OutlinedButton(
              onPressed: onOpen,
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(0, 36),
                padding: const EdgeInsets.symmetric(horizontal: 14),
                textStyle: const TextStyle(fontSize: 13),
              ),
              child: const Text('Open'),
            ),
            IconButton(
              tooltip: 'Delete week',
              icon: Icon(
                Icons.delete_outline,
                size: 18,
                color: Dt.textMuted(theme),
              ),
              onPressed: onDelete,
            ),
          ],
        );

        if (wide) {
          return InkWell(
            onTap: onOpen,
            child: Container(
              constraints: const BoxConstraints(minHeight: 76),
              decoration: BoxDecoration(
                border: Border(
                  bottom: BorderSide(color: theme.colorScheme.outline),
                ),
              ),
              padding: const EdgeInsets.symmetric(vertical: 14),
              child: Row(
                children: [
                  Expanded(
                    flex: 6,
                    child: Padding(
                      padding: const EdgeInsets.only(right: Dt.s16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [title, const SizedBox(height: 4), range],
                      ),
                    ),
                  ),
                  Expanded(
                    flex: 2,
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: newStat,
                    ),
                  ),
                  Expanded(
                    flex: 2,
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: existingStat,
                    ),
                  ),
                  Expanded(
                    flex: 2,
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: totalStat,
                    ),
                  ),
                  Expanded(
                    flex: 4,
                    child: Padding(
                      padding: const EdgeInsets.only(left: Dt.s24),
                      child: miniBars,
                    ),
                  ),
                  Expanded(
                    flex: 3,
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: actions,
                    ),
                  ),
                ],
              ),
            ),
          );
        }

        // Narrow layout.
        return Container(
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(color: theme.colorScheme.outline),
            ),
          ),
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              title,
              const SizedBox(height: 4),
              range,
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: newStat,
                    ),
                  ),
                  const SizedBox(width: Dt.s16),
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: existingStat,
                    ),
                  ),
                  const SizedBox(width: Dt.s16),
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: totalStat,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              miniBars,
              const SizedBox(height: 10),
              Align(alignment: Alignment.centerRight, child: actions),
            ],
          ),
        );
      },
    );
  }
}
