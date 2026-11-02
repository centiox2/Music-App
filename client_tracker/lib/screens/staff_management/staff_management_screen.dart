import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../design/app_theme.dart';
import '../../models/staff.dart';
import '../../repositories/client_count_repository.dart';
import '../../state/app_state.dart';
import '../../widgets/editorial.dart';

class StaffManagementScreen extends ConsumerStatefulWidget {
  const StaffManagementScreen({super.key});

  @override
  ConsumerState<StaffManagementScreen> createState() =>
      _StaffManagementScreenState();
}

class _StaffManagementScreenState extends ConsumerState<StaffManagementScreen> {
  // Explicit controllers: the table and the manage panel are two vertical
  // scrollables and must not share the primary one.
  final _tableController = ScrollController();
  final _panelController = ScrollController();

  @override
  void dispose() {
    _tableController.dispose();
    _panelController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final staff = ref.watch(staffListProvider);
    final active = staff.where((s) => s.active).toList();
    final inactive = staff.where((s) => !s.active).toList();

    // Weekly totals per staff id come from the current week (if any).
    final weekData = ref.watch(currentWeekProvider);
    final weekTotals = <int, int>{
      for (final t in weekData?.staffTotals ?? const <StaffWeekTotals>[])
        t.staffId: t.total,
    };

    return PageScaffold(
      scroll: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 36),
            child: EditorialHeader(
              cap: '${active.length} active · ${inactive.length} inactive',
              title: 'The ',
              accent: 'team',
            ),
          ),
          const SizedBox(height: Dt.s24),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                const panelWidth = 340.0;
                final showPanel = constraints.maxWidth >= 1080;
                final table = _StaffTable(
                  staff: staff,
                  weekTotals: weekTotals,
                  tableController: _tableController,
                );
                final panel = _ManagePanel(
                  inactive: inactive,
                  onAddNamed: (name) {
                    if (name.trim().isEmpty) return;
                    ref.read(staffRepositoryProvider).add(name);
                    ref.read(dataVersionProvider.notifier).bump();
                  },
                );

                if (!showPanel) {
                  // Narrow screens: bound the table's height so its inner
                  // Expanded never sees unbounded constraints, then stack the
                  // manage panel underneath.
                  final tableHeight = (staff.length * 54 + 44)
                      .clamp(220.0, 520.0)
                      .toDouble();
                  return SingleChildScrollView(
                    controller: _panelController,
                    padding: const EdgeInsets.only(bottom: Dt.s32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        SizedBox(height: tableHeight, child: table),
                        const SizedBox(height: Dt.s32),
                        panel,
                      ],
                    ),
                  );
                }
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: table),
                    const SizedBox(width: Dt.s44),
                    SizedBox(
                      width: panelWidth,
                      child: SingleChildScrollView(
                        controller: _panelController,
                        padding: const EdgeInsets.only(bottom: Dt.s32),
                        child: panel,
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

Future<String?> _promptName(
  BuildContext context, {
  required String title,
  String? initial,
}) {
  final controller = TextEditingController(text: initial ?? '');
  return showDialog<String>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: TextField(
        controller: controller,
        autofocus: true,
        textCapitalization: TextCapitalization.words,
        decoration: const InputDecoration(labelText: 'Full name'),
        onSubmitted: (v) => Navigator.pop(context, v),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, controller.text),
          child: const Text('Save'),
        ),
      ],
    ),
  ).whenComplete(controller.dispose);
}

// ---------------------------------------------------------------- table -----

/// The numbered team table. Column layout (desktop):
/// grip 28 | # 36 | avatar 48 | name flex | status 110 | this week 120 | actions 150
class _StaffTable extends ConsumerWidget {
  const _StaffTable({
    required this.staff,
    required this.weekTotals,
    required this.tableController,
  });

  final List<Staff> staff;
  final Map<int, int> weekTotals;
  final ScrollController tableController;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final compact = MediaQuery.sizeOf(context).width < 600;

    Widget cap(String text, {bool right = false}) => Text(
      text.toUpperCase(),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: right ? TextAlign.right : TextAlign.left,
      style: microLabel(theme),
    );

    final header = Container(
      height: 40,
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
      ),
      child: Row(
        children: compact
            ? [
                SizedBox(width: 70, child: cap('Team')),
                const Expanded(child: SizedBox()),
                SizedBox(width: 52, child: cap('Week', right: true)),
                const SizedBox(width: 120),
              ]
            : [
                const SizedBox(width: 28),
                SizedBox(width: 36, child: cap('#')),
                const SizedBox(width: 48),
                Expanded(child: cap('Name')),
                SizedBox(width: 110, child: cap('Status')),
                SizedBox(width: 120, child: cap('This week', right: true)),
                const SizedBox(width: 150),
              ],
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        header,
        Expanded(
          child: Scrollbar(
            controller: tableController,
            thumbVisibility: true,
            child: ReorderableListView.builder(
              scrollController: tableController,
              padding: const EdgeInsets.only(bottom: Dt.s32),
              itemCount: staff.length,
              shrinkWrap: true,
              buildDefaultDragHandles: false,
              proxyDecorator: (child, index, animation) => AnimatedBuilder(
                animation: animation,
                builder: (context, built) {
                  final t = Curves.easeOut.transform(animation.value);
                  return Container(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(Dt.rMd),
                      border: Border.all(
                        color: Color.lerp(
                          Theme.of(context).colorScheme.outline,
                          Theme.of(context).colorScheme.onSurface,
                          t,
                        )!,
                      ),
                    ),
                    child: built,
                  );
                },
                child: child,
              ),
              onReorderItem: (oldIndex, newIndex) {
                final ids = staff.map((s) => s.id).toList();
                ids.insert(newIndex, ids.removeAt(oldIndex));
                ref.read(staffRepositoryProvider).reorder(ids);
                ref.read(dataVersionProvider.notifier).bump();
              },
              itemBuilder: (context, i) {
                final member = staff[i];
                return _StaffRow(
                  key: ValueKey('staff-${member.id}'),
                  member: member,
                  index: i,
                  compact: compact,
                  weekTotal: weekTotals[member.id] ?? 0,
                  onEdit: () => _renameStaff(context, ref, member),
                  onToggleActive: () {
                    ref
                        .read(staffRepositoryProvider)
                        .setActive(member.id, !member.active);
                    ref.read(dataVersionProvider.notifier).bump();
                  },
                  onDelete: () => _confirmDelete(context, ref, member),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _renameStaff(
    BuildContext context,
    WidgetRef ref,
    Staff member,
  ) async {
    final name = await _promptName(
      context,
      title: 'Rename ${member.name}',
      initial: member.name,
    );
    if (name != null && name.trim().isNotEmpty) {
      ref.read(staffRepositoryProvider).rename(member.id, name);
      ref.read(dataVersionProvider.notifier).bump();
    }
  }

  Future<void> _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    Staff member,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete ${member.name}?'),
        content: Text(
          'This permanently removes ${member.name} and all their recorded '
          'numbers from every week. If ${member.name} has left, prefer '
          '"Deactivate" — past weeks keep their name and history intact.',
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
            child: const Text('Delete permanently'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      ref.read(staffRepositoryProvider).delete(member.id);
      ref.read(dataVersionProvider.notifier).bump();
    }
  }
}

/// One numbered team row (54px tall, hairline bottom rule).
class _StaffRow extends StatelessWidget {
  const _StaffRow({
    super.key,
    required this.member,
    required this.index,
    required this.weekTotal,
    required this.compact,
    required this.onEdit,
    required this.onToggleActive,
    required this.onDelete,
  });

  final Staff member;
  final int index;
  final int weekTotal;
  final bool compact;
  final VoidCallback onEdit;
  final VoidCallback onToggleActive;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;

    Widget act({
      required IconData icon,
      required String tooltip,
      required VoidCallback onTap,
      bool danger = false,
    }) {
      return Tooltip(
        message: tooltip,
        waitDuration: const Duration(milliseconds: 400),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(Dt.rPill),
          child: Container(
            height: 36,
            padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(Dt.rPill),
            ),
            child: Icon(
              icon,
              size: 16,
              color: danger ? theme.colorScheme.error : Dt.textMuted(theme),
            ),
          ),
        ),
      );
    }

    if (compact) {
      return Container(
        height: 62,
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(color: theme.colorScheme.outlineVariant),
          ),
        ),
        child: Row(
          children: [
            ReorderableDragStartListener(
              index: index,
              child: MouseRegion(
                cursor: SystemMouseCursors.grab,
                child: SizedBox(
                  width: 28,
                  height: 62,
                  child: Icon(
                    Icons.drag_indicator,
                    size: 14,
                    color: Dt.textFaint(theme),
                  ),
                ),
              ),
            ),
            SizedBox(
              width: 42,
              child: Container(
                width: 32,
                height: 32,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: theme.colorScheme.outline),
                ),
                child: Text(
                  member.name.isEmpty ? '?' : member.name[0].toUpperCase(),
                  style: Dt.display(
                    context,
                    size: 18,
                    italic: true,
                    color: Dt.newGreen(theme.brightness),
                  ),
                ),
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(left: Dt.s8, right: Dt.s4),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      member.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w500,
                        color: member.active ? null : Dt.textMuted(theme),
                      ),
                    ),
                    const SizedBox(height: 3),
                    Row(
                      children: [
                        MeaningDot(
                          member.active
                              ? Dt.statusGreen(theme.brightness)
                              : theme.colorScheme.outline,
                          size: 6,
                        ),
                        const SizedBox(width: 5),
                        Text(
                          member.active ? 'Active' : 'Inactive',
                          style: TextStyle(
                            fontSize: 11,
                            color: dark
                                ? theme.colorScheme.onSurfaceVariant
                                : Dt.textMuted(theme),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(
              width: 52,
              child: Text(
                '$weekTotal',
                textAlign: TextAlign.right,
                style: Dt.numeral(context, size: 20),
              ),
            ),
            SizedBox(
              width: 120,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  act(
                    icon: Icons.edit_outlined,
                    tooltip: 'Rename',
                    onTap: onEdit,
                  ),
                  act(
                    icon: member.active
                        ? Icons.do_not_disturb_on_outlined
                        : Icons.add_circle_outline,
                    tooltip: member.active
                        ? 'Mark inactive (keeps history)'
                        : 'Reactivate',
                    onTap: onToggleActive,
                  ),
                  act(
                    icon: Icons.delete_outline,
                    tooltip: 'Delete permanently',
                    onTap: onDelete,
                    danger: true,
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      height: 54,
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
      ),
      child: Row(
        children: [
          // Grip — the drag handle, in-row so nothing can overlap it.
          ReorderableDragStartListener(
            index: index,
            child: MouseRegion(
              cursor: SystemMouseCursors.grab,
              child: SizedBox(
                width: 28,
                height: 54,
                child: Icon(
                  Icons.drag_indicator,
                  size: 14,
                  color: Dt.textFaint(theme),
                ),
              ),
            ),
          ),
          SizedBox(
            width: 36,
            child: Text(
              (index + 1).toString().padLeft(2, '0'),
              style: Dt.mono(context, size: 13, color: Dt.textFaint(theme)),
            ),
          ),
          // Serif-initial avatar.
          SizedBox(
            width: 48,
            child: Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: theme.colorScheme.outline),
              ),
              child: Text(
                member.name.isEmpty ? '?' : member.name[0].toUpperCase(),
                style: Dt.display(
                  context,
                  size: 19,
                  italic: true,
                  color: Dt.newGreen(theme.brightness),
                ),
              ),
            ),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(left: Dt.s12, right: Dt.s8),
              child: Text(
                member.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w500,
                  color: member.active ? null : Dt.textMuted(theme),
                ),
              ),
            ),
          ),
          SizedBox(
            width: 110,
            child: Row(
              children: [
                MeaningDot(
                  member.active
                      ? Dt.statusGreen(theme.brightness)
                      : theme.colorScheme.outline,
                  size: 7,
                ),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    member.active ? 'Active' : 'Inactive',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      color: dark
                          ? theme.colorScheme.onSurfaceVariant
                          : Dt.textMuted(theme),
                    ),
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            width: 120,
            child: Text(
              '$weekTotal',
              textAlign: TextAlign.right,
              style: Dt.numeral(context, size: 24),
            ),
          ),
          SizedBox(
            width: 150,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                act(
                  icon: Icons.edit_outlined,
                  tooltip: 'Rename',
                  onTap: onEdit,
                ),
                act(
                  icon: member.active
                      ? Icons.do_not_disturb_on_outlined
                      : Icons.add_circle_outline,
                  tooltip: member.active
                      ? 'Mark inactive (keeps history)'
                      : 'Reactivate',
                  onTap: onToggleActive,
                ),
                act(
                  icon: Icons.delete_outline,
                  tooltip: 'Delete permanently',
                  onTap: onDelete,
                  danger: true,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- panel -----

/// The right-hand panel: add-staff form (inline field, Enter or button
/// submits), Inactive list summary, button legend.
class _ManagePanel extends StatefulWidget {
  const _ManagePanel({required this.inactive, required this.onAddNamed});

  final List<Staff> inactive;
  final ValueChanged<String> onAddNamed;

  @override
  State<_ManagePanel> createState() => _ManagePanelState();
}

class _ManagePanelState extends State<_ManagePanel> {
  final _nameController = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _nameController.text.trim();
    if (name.isEmpty) return;
    widget.onAddNamed(name);
    _nameController.clear();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final inactive = widget.inactive;

    Widget legend(IconData icon, String text) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Icon(icon, size: 16, color: Dt.textMuted(theme)),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('Add a staff member', style: Dt.display(context, size: 30)),
        const SizedBox(height: Dt.s16),
        Text('NAME', style: microLabel(theme)),
        const SizedBox(height: Dt.s8),
        TextField(
          controller: _nameController,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(hintText: 'e.g. Faith Wanjiru'),
          onSubmitted: (_) => _submit(),
        ),
        const SizedBox(height: Dt.s12),
        FilledButton.icon(
          onPressed: _submit,
          icon: const Icon(Icons.add, size: 16),
          label: const Text('Add to the team'),
        ),
        const SizedBox(height: Dt.s12),
        Text(
          'New staff appear at the bottom of the Week table. Drag the handle '
          'on any row to change the order.',
          style: theme.textTheme.bodySmall?.copyWith(
            color: Dt.textMuted(theme),
            height: 1.5,
          ),
        ),
        const SizedBox(height: Dt.s32),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: Text('Inactive', style: Dt.display(context, size: 30)),
            ),
            Text(
              '${inactive.length}',
              style: Dt.mono(context, size: 13, color: Dt.textMuted(theme)),
            ),
          ],
        ),
        const SizedBox(height: Dt.s12),
        Container(
          padding: const EdgeInsets.only(bottom: 8),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(color: theme.colorScheme.outline),
            ),
          ),
        ),
        const SizedBox(height: Dt.s12),
        Text(
          inactive.isEmpty
              ? 'No one is inactive. Marking someone inactive hides them from '
                    'new weeks but keeps their past numbers.'
              : inactive.map((s) => s.name).join(', '),
          style: theme.textTheme.bodyMedium?.copyWith(
            color: Dt.textMuted(theme),
            height: 1.5,
          ),
        ),
        const SizedBox(height: Dt.s32),
        Container(
          padding: const EdgeInsets.only(bottom: 8),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(color: theme.colorScheme.outline),
            ),
          ),
          child: Text(
            'WHAT THE BUTTONS DO'.toUpperCase(),
            style: microLabel(theme),
          ),
        ),
        const SizedBox(height: Dt.s8),
        legend(Icons.edit_outlined, 'Rename'),
        legend(
          Icons.do_not_disturb_on_outlined,
          'Mark inactive (keeps history)',
        ),
        legend(Icons.delete_outline, 'Delete permanently'),
      ],
    );
  }
}
