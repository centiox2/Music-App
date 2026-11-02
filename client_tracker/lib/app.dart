import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'design/app_theme.dart';
import 'screens/dashboard/dashboard_screen.dart';
import 'screens/history/history_screen.dart';
import 'screens/settings/settings_screen.dart';
import 'screens/staff_management/staff_management_screen.dart';
import 'screens/weekly_tracker/weekly_tracker_screen.dart';
import 'state/app_state.dart';
import 'screens/weekly_tracker/new_week_dialog.dart';

class ClientTrackerApp extends ConsumerWidget {
  const ClientTrackerApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    return MaterialApp(
      title: 'Client Tracker',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(Brightness.light),
      darkTheme: buildAppTheme(Brightness.dark),
      themeMode: themeMode,
      home: const HomeShell(),
    );
  }
}

class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  final _focusNode = FocusNode();

  static const _tabs = <_AppTabInfo>[
    _AppTabInfo(AppTab.home, 'Overview', Icons.space_dashboard_outlined),
    _AppTabInfo(
      AppTab.week,
      'Weekly tracker',
      Icons.calendar_view_week_outlined,
    ),
    _AppTabInfo(AppTab.history, 'History', Icons.history),
    _AppTabInfo(AppTab.staff, 'Staff', Icons.groups_2_outlined),
    _AppTabInfo(AppTab.settings, 'Settings', Icons.settings_outlined),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ref.read(shellContextProvider.notifier).capture(context);
      _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _handleKey(KeyEvent event) {
    if (event is! KeyDownEvent) return;
    final modifier =
        HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed;
    if (!modifier) return;
    if (event.logicalKey == LogicalKeyboardKey.keyZ) {
      final undone = ref.read(appActionsProvider).undoLastEdit();
      if (undone != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Undo ${undone.isNew ? 'New' : 'Existing'} · ${undone.value}',
            ),
            duration: const Duration(milliseconds: 1400),
          ),
        );
      }
    }
    final keys = <LogicalKeyboardKey>[
      LogicalKeyboardKey.digit1,
      LogicalKeyboardKey.digit2,
      LogicalKeyboardKey.digit3,
      LogicalKeyboardKey.digit4,
      LogicalKeyboardKey.digit5,
    ];
    final index = keys.indexOf(event.logicalKey);
    if (index >= 0) ref.read(navTabProvider.notifier).set(_tabs[index].tab);
  }

  Widget _page(AppTab tab) => switch (tab) {
    AppTab.home => const DashboardScreen(),
    AppTab.week => const WeeklyTrackerScreen(),
    AppTab.history => const HistoryScreen(),
    AppTab.staff => const StaffManagementScreen(),
    AppTab.settings => const SettingsScreen(),
  };

  @override
  Widget build(BuildContext context) {
    final tab = ref.watch(navTabProvider);
    final width = MediaQuery.sizeOf(context).width;
    final desktop = width >= 1050;
    final theme = Theme.of(context);

    final content = Focus(
      focusNode: _focusNode,
      autofocus: true,
      onKeyEvent: (_, event) {
        _handleKey(event);
        return KeyEventResult.ignored;
      },
      child: _page(tab),
    );

    if (!desktop) {
      return Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              const _MobileTopBar(),
              _ContextBar(tab: tab),
              Expanded(child: content),
            ],
          ),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tabs.indexWhere((item) => item.tab == tab),
          onDestinationSelected: (index) =>
              ref.read(navTabProvider.notifier).set(_tabs[index].tab),
          destinations: [
            for (final item in _tabs)
              NavigationDestination(
                icon: Icon(item.icon),
                selectedIcon: Icon(item.icon),
                label: item.label,
              ),
          ],
        ),
      );
    }

    return Scaffold(
      body: Row(
        children: [
          _AppSidebar(
            tabs: _tabs,
            selected: tab,
            onSelect: (value) => ref.read(navTabProvider.notifier).set(value),
          ),
          VerticalDivider(
            width: 1,
            thickness: 1,
            color: theme.colorScheme.outline,
          ),
          Expanded(
            child: Column(
              children: [
                _TopBar(),
                _HorizontalTabs(
                  tabs: _tabs,
                  selected: tab,
                  onSelect: (value) =>
                      ref.read(navTabProvider.notifier).set(value),
                ),
                _ContextBar(tab: tab),
                Expanded(child: content),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AppTabInfo {
  const _AppTabInfo(this.tab, this.label, this.icon);
  final AppTab tab;
  final String label;
  final IconData icon;
}

class _AppSidebar extends StatelessWidget {
  const _AppSidebar({
    required this.tabs,
    required this.selected,
    required this.onSelect,
  });
  final List<_AppTabInfo> tabs;
  final AppTab selected;
  final ValueChanged<AppTab> onSelect;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final mainTabs = tabs.where((tab) => tab.tab != AppTab.settings).toList();
    final settings = tabs.firstWhere((tab) => tab.tab == AppTab.settings);

    Widget navItem(_AppTabInfo item) {
      final active = selected == item.tab;
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 1),
        child: Material(
          color: active ? scheme.surfaceContainerHigh : Colors.transparent,
          borderRadius: BorderRadius.circular(Dt.rSm),
          child: InkWell(
            borderRadius: BorderRadius.circular(Dt.rSm),
            onTap: () => onSelect(item.tab),
            child: SizedBox(
              height: 34,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Row(
                  children: [
                    Icon(
                      item.icon,
                      size: 16,
                      color: active
                          ? scheme.onSurface
                          : scheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        item.label,
                        style: theme.textTheme.labelMedium?.copyWith(
                          fontWeight: active
                              ? FontWeight.w600
                              : FontWeight.w400,
                          color: active
                              ? scheme.onSurface
                              : scheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                    if (item.tab == AppTab.week)
                      Icon(
                        Icons.chevron_right,
                        size: 15,
                        color: scheme.onSurfaceVariant,
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }

    return SizedBox(
      width: 232,
      child: ColoredBox(
        color: theme.scaffoldBackgroundColor,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
              child: Row(
                children: [
                  const _BrandMark(),
                  const SizedBox(width: 9),
                  Expanded(
                    child: Text(
                      'Centiox2020@gmail.com',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Icon(
                    Icons.unfold_more,
                    size: 15,
                    color: scheme.onSurfaceVariant,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 15),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: SizedBox(
                height: 34,
                child: TextField(
                  readOnly: true,
                  decoration: InputDecoration(
                    hintText: 'Quick search…',
                    prefixIcon: const Icon(Icons.search, size: 16),
                    suffixIcon: Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: Center(
                        widthFactor: 1,
                        child: Text(
                          'Ctrl K',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ),
                    contentPadding: EdgeInsets.zero,
                    prefixIconConstraints: const BoxConstraints(minWidth: 34),
                    suffixIconConstraints: const BoxConstraints(minWidth: 52),
                    filled: true,
                    fillColor: scheme.surfaceContainerLow,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(Dt.rSm),
                      borderSide: BorderSide(color: scheme.outline),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(Dt.rSm),
                      borderSide: BorderSide(color: scheme.outline),
                    ),
                  ),
                  style: theme.textTheme.labelSmall,
                ),
              ),
            ),
            const SizedBox(height: 14),
            const _SidebarCaption('WORKSPACE'),
            for (final item in mainTabs) navItem(item),
            const SizedBox(height: 13),
            const Divider(height: 1),
            const Spacer(),
            const Divider(height: 1),
            const SizedBox(height: 8),
            navItem(settings),
            const SizedBox(height: 10),
          ],
        ),
      ),
    );
  }
}

class _SidebarCaption extends StatelessWidget {
  const _SidebarCaption(this.label);
  final String label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 0, 12, 6),
    child: Text(
      label,
      style: Theme.of(context).textTheme.labelSmall?.copyWith(
        fontSize: 10,
        letterSpacing: 0.4,
        color: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
    ),
  );
}

class _TopBar extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.onSurfaceVariant;
    return SizedBox(
      height: 46,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              Icon(Icons.cloud_outlined, size: 16, color: color),
              const SizedBox(width: 8),
              Text(
                'Client Tracker',
                style: theme.textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(width: 12),
              Icon(Icons.chevron_right, size: 14, color: color),
              const SizedBox(width: 8),
              Text(
                'Workspace',
                style: theme.textTheme.labelSmall?.copyWith(color: color),
              ),
              const Spacer(),
              Tooltip(
                message: 'AI assistance is not available in this local app.',
                child: TextButton.icon(
                  onPressed: null,
                  icon: const Icon(Icons.auto_awesome, size: 14),
                  label: const Text('Ask AI'),
                ),
              ),
              IconButton(
                tooltip: 'Support is not configured for this local app.',
                onPressed: null,
                icon: const Icon(Icons.help_outline, size: 17),
              ),
              const SizedBox(width: 4),
              CircleAvatar(
                radius: 13,
                backgroundColor: theme.colorScheme.surfaceContainerHigh,
                child: Icon(Icons.person, size: 15, color: color),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MobileTopBar extends ConsumerWidget {
  const _MobileTopBar();

  @override
  Widget build(BuildContext context, WidgetRef ref) => SizedBox(
    height: 46,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14),
      child: Row(
        children: [
          const _BrandMark(),
          const SizedBox(width: 8),
          Text('Client Tracker', style: Theme.of(context).textTheme.titleSmall),
          const Spacer(),
          PopupMenuButton<String>(
            tooltip: 'More options',
            onSelected: (action) {
              if (action == 'settings') {
                ref.read(appActionsProvider).selectTab(AppTab.settings);
              } else if (action == 'refresh') {
                ref.read(dataVersionProvider.notifier).bump();
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'refresh', child: Text('Refresh data')),
              PopupMenuItem(value: 'settings', child: Text('Settings')),
            ],
            icon: const Icon(Icons.more_horiz),
          ),
        ],
      ),
    ),
  );
}

class _BrandMark extends StatelessWidget {
  const _BrandMark();

  @override
  Widget build(BuildContext context) => Container(
    width: 25,
    height: 25,
    decoration: BoxDecoration(
      color: Dt.brandOrange,
      borderRadius: BorderRadius.circular(Dt.rSm),
    ),
    child: const Icon(Icons.cloud, size: 16, color: Colors.white),
  );
}

class _HorizontalTabs extends StatelessWidget {
  const _HorizontalTabs({
    required this.tabs,
    required this.selected,
    required this.onSelect,
  });
  final List<_AppTabInfo> tabs;
  final AppTab selected;
  final ValueChanged<AppTab> onSelect;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 42,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
        ),
        child: ListView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          children: [
            for (final item in tabs)
              _TopTab(
                item: item,
                selected: item.tab == selected,
                onTap: () => onSelect(item.tab),
              ),
          ],
        ),
      ),
    );
  }
}

class _TopTab extends StatelessWidget {
  const _TopTab({
    required this.item,
    required this.selected,
    required this.onTap,
  });
  final _AppTabInfo item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(right: 4, top: 5),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Dt.rSm),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: selected
                ? theme.colorScheme.surfaceContainerHigh
                : Colors.transparent,
            borderRadius: BorderRadius.circular(Dt.rSm),
            border: Border.all(
              color: selected ? theme.colorScheme.outline : Colors.transparent,
            ),
          ),
          alignment: Alignment.center,
          child: Text(
            item.label,
            style: theme.textTheme.labelSmall?.copyWith(
              fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
              color: selected
                  ? theme.colorScheme.onSurface
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ),
    );
  }
}

class _ContextBar extends ConsumerWidget {
  const _ContextBar({required this.tab});
  final AppTab tab;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final compact = MediaQuery.sizeOf(context).width < 600;
    final week = ref.watch(currentWeekProvider)?.week;
    final range = week == null
        ? 'No active week'
        : '${week.startDate.day}/${week.startDate.month}/${week.startDate.year}  –  '
              '${week.endDate.day}/${week.endDate.month}/${week.endDate.year}';
    final label = tab == AppTab.home ? range : _tabLabel(tab);
    return SizedBox(
      height: 48,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              if (compact)
                Expanded(
                  child: Row(
                    children: [
                      Flexible(
                        child: Text(
                          label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelMedium?.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      const Icon(Icons.arrow_drop_down, size: 17),
                    ],
                  ),
                )
              else ...[
                Text(
                  label,
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const Icon(Icons.arrow_drop_down, size: 17),
                const Spacer(),
              ],
              if (compact)
                IconButton(
                  tooltip: 'Refresh',
                  visualDensity: VisualDensity.compact,
                  onPressed: () =>
                      ref.read(dataVersionProvider.notifier).bump(),
                  icon: const Icon(Icons.refresh, size: 16),
                )
              else
                TextButton.icon(
                  onPressed: () =>
                      ref.read(dataVersionProvider.notifier).bump(),
                  icon: const Icon(Icons.refresh, size: 14),
                  label: const Text('Refresh'),
                ),
              const SizedBox(width: 6),
              OutlinedButton.icon(
                onPressed: () => showNewWeekDialog(context, ref),
                icon: const Icon(Icons.add, size: 14),
                label: Text(compact ? 'New' : 'New week'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _tabLabel(AppTab tab) => switch (tab) {
  AppTab.home => 'Overview',
  AppTab.week => 'Weekly tracker',
  AppTab.history => 'History',
  AppTab.staff => 'Staff',
  AppTab.settings => 'Settings',
};
