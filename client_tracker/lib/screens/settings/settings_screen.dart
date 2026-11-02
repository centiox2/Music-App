import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../design/app_theme.dart';
import '../../models/staff.dart';
import '../../services/file_delivery.dart';
import '../../state/app_state.dart';
import '../../widgets/editorial.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  int? _exportWeekId;
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final weeks = ref.watch(historyProvider);
    final themeMode = ref.watch(themeModeProvider);
    final theme = Theme.of(context);
    final gutter = editorialGutter(context);
    // The export dropdown follows the week list until the user picks one.
    final effectiveWeekId =
        _exportWeekId ?? (weeks.isNotEmpty ? weeks.first.week.id : null);

    return Scrollbar(
      thumbVisibility: true,
      child: ListView(
        padding: EdgeInsets.fromLTRB(gutter, 36, gutter, Dt.s44),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: Dt.contentMaxWidth),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  EditorialHeader(
                    cap: 'Client Tracker · Local · SQLite',
                    title: '',
                    accent: 'Settings',
                    italicAll: true,
                    bottomBorder: true,
                  ),
                  const SizedBox(height: Dt.s8),

                  // ---- Appearance ---------------------------------------
                  _SettingsRow(
                    title: 'Appearance',
                    description: 'Choose a theme, or follow your computer\u2019s setting.',
                    child: SegmentedButton<ThemeMode>(
                      segments: const [
                        ButtonSegment(
                          value: ThemeMode.light,
                          label: Text('Light'),
                        ),
                        ButtonSegment(
                          value: ThemeMode.system,
                          label: Text('System'),
                        ),
                        ButtonSegment(
                          value: ThemeMode.dark,
                          label: Text('Dark'),
                        ),
                      ],
                      selected: {themeMode},
                      showSelectedIcon: false,
                      onSelectionChanged: (s) =>
                          ref.read(themeModeProvider.notifier).set(s.first),
                    ),
                  ),

                  // ---- Export -------------------------------------------
                  _SettingsRow(
                    title: 'Export to Excel',
                    description:
                        'Exports a week exactly like the old spreadsheet: '
                        'Monday\u2013Friday sections with Staff / New / Existing, '
                        'daily totals, weekly totals and daily average.',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        DropdownButtonFormField<int>(
                          initialValue: effectiveWeekId,
                          decoration: const InputDecoration(
                            labelText: 'Week to export',
                          ),
                          items: [
                            for (final w in weeks)
                              DropdownMenuItem(
                                value: w.week.id,
                                child: Text(_weekLabel(w.week)),
                              ),
                          ],
                          onChanged: (v) => setState(() => _exportWeekId = v),
                        ),
                        const SizedBox(height: Dt.s12),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: FilledButton.icon(
                            onPressed: effectiveWeekId == null || _busy
                                ? null
                                : _exportWeek,
                            icon: _busy
                                ? const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.download_outlined, size: 16),
                            label: const Text('Export .xlsx'),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // ---- Backup & restore ----------------------------------
                  _SettingsRow(
                    title: 'Backup & restore',
                    description:
                        'A backup is one portable .json file containing all '
                        'staff, weeks and client counts. Copy it to another '
                        'computer and use Restore there.',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          spacing: Dt.s8,
                          runSpacing: Dt.s8,
                          children: [
                            OutlinedButton.icon(
                              onPressed: _busy ? null : _backup,
                              icon: _busy
                                  ? const SizedBox(
                                      width: 14,
                                      height: 14,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.upload_outlined, size: 16),
                              label: const Text('Back up data'),
                            ),
                            OutlinedButton.icon(
                              onPressed: _busy ? null : _restore,
                              icon: _busy
                                  ? const SizedBox(
                                      width: 14,
                                      height: 14,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.restore, size: 16),
                              label: const Text('Restore data'),
                            ),
                          ],
                        ),
                        const SizedBox(height: Dt.s12),
                        Text(
                          'The app also keeps rolling copies of its database '
                          '(last 5 sessions) and up to 10 automatic backups in '
                          'the app data folder.',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: Dt.textMuted(theme),
                          ),
                        ),
                      ],
                    ),
                  ),

                  // ---- About ---------------------------------------------
                  _SettingsRow(
                    title: 'About',
                    description: '',
                    last: true,
                    child: Text(
                      'Client Tracker replaces the weekly Excel tracking '
                      'spreadsheet. All data is stored locally in SQLite; '
                      'totals are always calculated live from the raw entries.',
                      style: theme.textTheme.bodyLarge?.copyWith(
                        height: 1.6,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// "October 5 – 9, 2026 (05/10 – 09/10)" — the mockup's dropdown label.
  String _weekLabel(Week w) =>
      '${DateFormat('MMMM d').format(w.startDate)} – '
      '${DateFormat('MMMM d, y').format(w.endDate)} '
      '(${DateFormat('dd/MM').format(w.startDate)} – '
      '${DateFormat('dd/MM').format(w.endDate)})';

  Future<void> _busyWrap(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _exportWeek() => _busyWrap(() async {
    final weekId =
        _exportWeekId ??
        (ref.read(historyProvider).isNotEmpty
            ? ref.read(historyProvider).first.week.id
            : null);
    if (weekId == null) return;
    final week = ref.read(weekRepositoryProvider).getWeek(weekId);
    if (week == null) return;

    final result = ref.read(exportServiceProvider).exportWeek(week);
    final defaultName =
        'weekly_clients_${DateFormat('yyyyMMdd').format(week.startDate)}.xlsx';
    final saved = await saveFileBytes(
      dialogTitle: 'Export week to Excel',
      defaultName: defaultName,
      extensions: ['xlsx'],
      bytes: result.bytes,
    );
    if (saved == null) return; // user cancelled
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Exported to $saved')));
    }
  });

  Future<void> _backup() => _busyWrap(() async {
    final result = ref.read(backupServiceProvider).backup();
    final saved = await saveFileBytes(
      dialogTitle: 'Backup data',
      defaultName: result.suggestedName,
      extensions: ['json'],
      // UTF-8: codeUnits would silently corrupt non-ASCII staff names.
      bytes: Uint8List.fromList(utf8.encode(result.json)),
    );
    if (saved == null) return;
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Backup saved to $saved')));
    }
  });

  Future<void> _restore() => _busyWrap(() async {
    final bytes = await openFileBytes(
      dialogTitle: 'Restore from backup',
      extensions: ['json'],
    );
    if (bytes == null) return;
    final json = utf8.decode(bytes);
    if (!mounted) return;

    // Double-confirm: restore replaces everything.
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Replace all data?'),
        content: const Text(
          'Restoring will overwrite ALL current staff, weeks and counts '
          'with the backup contents. A safety copy of the current data '
          'is saved first.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Restore'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    ref.read(backupServiceProvider).restoreJson(json);
    ref.read(dataVersionProvider.notifier).bump();
    // The restored database has different week ids — clear the selection
    // so the dashboard/tracker fall back to the newest week instead of
    // pointing at an id that may no longer exist.
    ref.read(currentWeekIdProvider.notifier).set(null);
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Data restored.')));
    }
  });
}

/// One editorial settings row: serif title + description on the left, control
/// on the right; hairline rule below (except on the last row).
class _SettingsRow extends StatelessWidget {
  const _SettingsRow({
    required this.title,
    required this.description,
    required this.child,
    this.last = false,
  });

  final String title;
  final String description;
  final Widget child;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: Dt.s32),
      decoration: last
          ? null
          : BoxDecoration(
              border: Border(
                bottom: BorderSide(color: theme.colorScheme.outlineVariant),
              ),
            ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          // Stack below ~830px so the 340px title column never overflows the
          // window (same spirit as the dashboard's 1080 breakpoint).
          final stacked = constraints.maxWidth < 830;
          final titleBlock = SizedBox(
            width: stacked ? double.infinity : 340,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Dt.display(context, size: 30)),
                if (description.isNotEmpty) ...[
                  const SizedBox(height: Dt.s8),
                  Text(
                    description,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: Dt.textMuted(theme),
                      height: 1.55,
                    ),
                  ),
                ],
              ],
            ),
          );
          if (stacked) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                titleBlock,
                const SizedBox(height: Dt.s16),
                child,
              ],
            );
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              titleBlock,
              const SizedBox(width: Dt.s44),
              Expanded(child: child),
            ],
          );
        },
      ),
    );
  }
}
