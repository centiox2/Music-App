import 'package:flutter/material.dart';

import '../design/app_theme.dart';

// ---------------------------------------------------------------------------
// Page scaffold + editorial header
// ---------------------------------------------------------------------------

/// Centers [child] at [Dt.contentMaxWidth] inside the 44px editorial gutter.
class PageScaffold extends StatelessWidget {
  const PageScaffold({super.key, required this.child, this.scroll = true});

  final Widget child;
  final bool scroll;

  @override
  Widget build(BuildContext context) {
    final gutter = editorialGutter(context);
    final content = Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: Dt.contentMaxWidth),
        child: child,
      ),
    );
    if (!scroll) return content;
    return SingleChildScrollView(
      padding: EdgeInsets.fromLTRB(gutter, 36, gutter, Dt.s44),
      child: content,
    );
  }
}

/// Shared horizontal page gutter that preserves the desktop rhythm without
/// squeezing phone layouts and their controls.
double editorialGutter(BuildContext context) {
  final width = MediaQuery.sizeOf(context).width;
  if (width < 600) return Dt.s16;
  if (width < 900) return Dt.s24;
  return Dt.s44;
}

/// The editorial page header: uppercase cap line, then the big Instrument
/// Serif headline with one peach italic accent word, optional actions at the
/// baseline's end.
class EditorialHeader extends StatelessWidget {
  const EditorialHeader({
    super.key,
    required this.cap,
    required this.title,
    this.accent,
    this.actions,
    this.italicAll = false,
    this.bottomBorder = false,
    this.titleSize = 60,
  });

  final String cap;

  /// Headline text *before* the accent word. Pass empty for an all-accent
  /// title ("Settings" renders fully italic).
  final String title;

  /// The italic peach word(s) appended to [title].
  final String? accent;

  /// Actions shown at the end of the header row (buttons, search…).
  final List<Widget>? actions;

  final bool italicAll;
  final bool bottomBorder;
  final double titleSize;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final headline = Dt.display(
      context,
      size: titleSize,
      height: 1.0,
      letterSpacing: -0.5,
    );

    Widget buildTitle() {
      final accentStyle = headline.copyWith(
        fontStyle: FontStyle.italic,
        color: Dt.newGreen(theme.brightness),
      );
      if (italicAll) {
        // The headline may be carried by [accent] alone (Settings renders
        // title:'' + accent:'Settings') — fall back to accent so the title
        // never paints empty.
        return RichText(
          text: TextSpan(
            children: [
              if (title.isNotEmpty) TextSpan(text: title, style: accentStyle),
              if (accent != null) TextSpan(text: accent, style: accentStyle),
            ],
          ),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        );
      }
      return RichText(
        text: TextSpan(
          children: [
            if (title.isNotEmpty) TextSpan(text: title, style: headline),
            if (accent != null) TextSpan(text: accent, style: accentStyle),
          ],
        ),
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      );
    }

    final header = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          cap,
          style: microLabel(theme),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: Dt.s8),
        FittedBox(
          fit: BoxFit.scaleDown,
          alignment: Alignment.centerLeft,
          child: buildTitle(),
        ),
      ],
    );

    final actionsList = actions;
    return LayoutBuilder(
      builder: (context, constraints) {
        final stacked = constraints.maxWidth < 720;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (actionsList == null || actionsList.isEmpty)
              header
            else if (stacked) ...[
              header,
              const SizedBox(height: Dt.s16),
              Wrap(spacing: Dt.s8, runSpacing: Dt.s8, children: actionsList),
            ] else
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(child: header),
                  const SizedBox(width: Dt.s24),
                  Wrap(
                    spacing: Dt.s8,
                    runSpacing: Dt.s8,
                    alignment: WrapAlignment.end,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: actionsList,
                  ),
                ],
              ),
            if (bottomBorder) ...[
              const SizedBox(height: Dt.s20),
              Container(height: 1, color: theme.colorScheme.outline),
            ],
          ],
        );
      },
    );
  }
}

// ---------------------------------------------------------------------------
// Stat strips
// ---------------------------------------------------------------------------

/// "● NEW" — 8px meaning-dot + uppercase micro-label.
class DotCaption extends StatelessWidget {
  const DotCaption(this.label, {super.key, this.color});

  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dot = color;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (dot != null) ...[
          MeaningDot(dot, size: 8),
          const SizedBox(width: Dt.s8),
        ],
        Flexible(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: microLabel(theme),
          ),
        ),
      ],
    );
  }
}

/// One cell of a [StatStrip]: caption + big serif numeral.
class StatCell extends StatelessWidget {
  const StatCell({
    super.key,
    required this.label,
    required this.value,
    this.dotColor,
    this.valueColor,
    this.valueSize = 56,
    this.first = false,
    this.trailing,
  });

  final String label;
  final String value;
  final Color? dotColor;
  final Color? valueColor;
  final double valueSize;

  /// Set on the last cell to drop its right padding (strip edge).
  final bool first;

  /// Optional widget below the value (e.g. a progress bar).
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        first ? 0 : Dt.s24,
        18,
        first ? Dt.s24 : 0,
        18,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          DotCaption(label, color: dotColor),
          const SizedBox(height: Dt.s4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Flexible(
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  alignment: Alignment.centerLeft,
                  child: Text(
                    value,
                    style: Dt.numeral(
                      context,
                      size: valueSize,
                      color: valueColor,
                    ),
                  ),
                ),
              ),
              ?trailing,
            ],
          ),
        ],
      ),
    );
  }
}

/// The hairline-ruled horizontal band of stat cells (border-top + border-bottom,
/// cells separated by vertical hairlines).
class StatStrip extends StatelessWidget {
  const StatStrip({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final border = BorderSide(color: Theme.of(context).colorScheme.outline);
    return Container(
      decoration: BoxDecoration(
        border: Border(top: border, bottom: border),
      ),
      // IntrinsicHeight keeps the vertical hairline dividers full-height while
      // the strip sits inside an unbounded (scrolling) column.
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0)
                Container(
                  width: 1,
                  color: Theme.of(context).colorScheme.outline,
                ),
              Expanded(child: children[i]),
            ],
          ],
        ),
      ),
    );
  }
}

/// Peach "THIS WEEK" chip used in History.
class PillChip extends StatelessWidget {
  const PillChip(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: Dt.newGreen(theme.brightness),
        borderRadius: BorderRadius.circular(Dt.rPill),
      ),
      child: Text(
        text.toUpperCase(),
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.7,
          color: dark ? const Color(0xFF141211) : const Color(0xFFFFFFFF),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------

/// A row of small bottom-aligned bars (History "Mon – Fri" strip).
/// Unlogged days render as a 4px stub in the border color.
class MiniBars extends StatelessWidget {
  const MiniBars({
    super.key,
    required this.values,
    this.height = 40,
    this.color,
  });

  /// Per-day totals; `null` = not logged.
  final List<int?> values;
  final double height;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // All-zero (or all-null) values would make max == 0 and NaN heights —
    // render flat 4px stubs instead.
    final max = values.fold<int>(0, (m, v) => v == null ? m : (v > m ? v : m));
    final scale = max > 0 ? (height - 2) / max : 0.0;
    final barColor =
        color ?? theme.colorScheme.onSurface.withValues(alpha: 0.78);
    return SizedBox(
      height: height,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var i = 0; i < values.length; i++) ...[
            Expanded(
              child: Container(
                height: values[i] == null || max == 0
                    ? 4.0
                    : ((values[i]! * scale).clamp(4.0, height)).toDouble(),
                decoration: BoxDecoration(
                  color: values[i] == null
                      ? theme.colorScheme.outline
                      : barColor,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Gap after every bar (position-based, not value-based — equal
            // adjacent values must still be separated).
            if (i < values.length - 1) const SizedBox(width: 6),
          ],
        ],
      ),
    );
  }
}

/// One day column of the stacked by-day chart.
/// A day is treated as "logged" when its total is greater than zero (i.e.
/// at least one working staff member entered a number). The chart derives
/// this internally, so callers no longer pass a separate logged flag.
class DayBarData {
  const DayBarData({
    required this.label,
    required this.isToday,
    this.newClients = 0,
    this.existingClients = 0,
  });

  final String label;
  final bool isToday;
  final int newClients;
  final int existingClients;

  int get total => newClients + existingClients;
  bool get logged => total > 0;
}

/// The "BY DAY" chart: serif totals above stacked peach/periwinkle segments,
/// dashed stubs for unlogged days, weekday labels underneath.
class StackedDayBars extends StatelessWidget {
  const StackedDayBars({super.key, required this.days, this.height = 170});

  final List<DayBarData> days;
  final double height;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final barMax = height - 52; // room for the serif total above
    final maxDay = days.fold<int>(
      0,
      (m, d) => d.logged && d.total > m ? d.total : m,
    );
    final todayColor = Dt.newGreen(theme.brightness);
    final muted = Dt.textMuted(theme);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: height,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              for (final d in days) ...[
                Expanded(
                  child: _DayBarColumn(
                    data: d,
                    scale: maxDay == 0 ? 0.0 : barMax / maxDay,
                    barMax: barMax,
                    newColor: Dt.newGreen(theme.brightness),
                    existingColor: Dt.existingBlue(theme.brightness),
                    stubColor: theme.colorScheme.outline,
                    totalStyle: Dt.numeral(context, size: 20),
                  ),
                ),
                if (d != days.last) const SizedBox(width: 14),
              ],
            ],
          ),
        ),
        const SizedBox(height: Dt.s8),
        Row(
          children: [
            for (final d in days) ...[
              Expanded(
                child: Text(
                  d.label,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 12,
                    color: d.isToday ? todayColor : muted,
                    fontWeight: d.isToday ? FontWeight.w600 : FontWeight.w400,
                  ),
                ),
              ),
              if (d != days.last) const SizedBox(width: 14),
            ],
          ],
        ),
        if (dark) const SizedBox(height: 2),
      ],
    );
  }
}

class _DayBarColumn extends StatelessWidget {
  const _DayBarColumn({
    required this.data,
    required this.scale,
    required this.barMax,
    required this.newColor,
    required this.existingColor,
    required this.stubColor,
    required this.totalStyle,
  });

  final DayBarData data;
  final double scale;
  final double barMax;
  final Color newColor;
  final Color existingColor;
  final Color stubColor;
  final TextStyle totalStyle;

  @override
  Widget build(BuildContext context) {
    final total = data.logged ? data.total : null;
    final nH = data.logged ? (data.newClients * scale) : 0.0;
    final eH = data.logged ? (data.existingClients * scale) : 0.0;
    final stubH = data.logged ? 0.0 : barMax;

    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          total?.toString() ?? '–',
          textAlign: TextAlign.center,
          style: totalStyle.copyWith(color: total == null ? stubColor : null),
        ),
        const SizedBox(height: 3),
        if (nH > 0)
          Container(
            height: nH,
            decoration: BoxDecoration(
              color: newColor,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(3),
              ),
            ),
          ),
        if (eH > 0)
          Container(
            height: eH,
            decoration: BoxDecoration(
              color: existingColor,
              borderRadius: BorderRadius.vertical(
                top: Radius.circular(nH == 0 ? 3 : 0),
                bottom: Radius.circular(3),
              ),
            ),
          ),
        if (stubH > 0)
          SizedBox(
            height: stubH,
            child: CustomPaint(painter: _DashedStubPainter(color: stubColor)),
          ),
      ],
    );
  }
}

/// Dashed open-top rectangle for unlogged day columns.
class _DashedStubPainter extends CustomPainter {
  const _DashedStubPainter({required this.color});

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;
    const dash = 4.0;
    const gap = 3.0;

    // Top edge.
    for (var x = 0.0; x < size.width; x += dash + gap) {
      canvas.drawLine(
        Offset(x, 0.5),
        Offset((x + dash).clamp(0, size.width), 0.5),
        paint,
      );
    }
    // Sides.
    for (var y = 0.0; y < size.height; y += dash + gap) {
      canvas.drawLine(
        Offset(0.5, y),
        Offset(0.5, (y + dash).clamp(0, size.height)),
        paint,
      );
      canvas.drawLine(
        Offset(size.width - 0.5, y),
        Offset(size.width - 0.5, (y + dash).clamp(0, size.height)),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _DashedStubPainter oldDelegate) =>
      oldDelegate.color != color;
}

/// One row of the "Most clients this week" leaderboard.
class LeaderRow extends StatelessWidget {
  const LeaderRow({
    super.key,
    required this.rank,
    required this.name,
    required this.value,
    required this.pct,
  });

  final int rank;
  final String name;
  final int value;

  /// 0..100 — bar fill relative to the week's top performer.
  final int pct;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          SizedBox(
            width: 20,
            child: Text(
              '$rank',
              textAlign: TextAlign.center,
              style: Dt.display(context, size: 20, color: Dt.textFaint(theme)),
            ),
          ),
          const SizedBox(width: Dt.s12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 5),
                Container(
                  height: 3,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.outline,
                    borderRadius: BorderRadius.circular(2),
                  ),
                  child: FractionallySizedBox(
                    alignment: Alignment.centerLeft,
                    widthFactor: (pct / 100).clamp(0.0, 1.0),
                    child: Container(
                      decoration: BoxDecoration(
                        color: theme.colorScheme.onSurface,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: Dt.s12),
          Text('$value', style: Dt.numeral(context, size: 22)),
        ],
      ),
    );
  }
}

/// The "Client mix" bar: peach/periwinkle split with % captions.
class MixBar extends StatelessWidget {
  const MixBar({
    super.key,
    required this.newCount,
    required this.existingCount,
  });

  final int newCount;
  final int existingCount;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = newCount + existingCount;
    final newPct = total == 0 ? 0 : (newCount / total * 100).round();
    final existingPct = 100 - newPct;
    // An empty week has no mix to show — skip the (otherwise full-width
    // "100% existing") bar entirely.
    final showBar = total > 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (showBar)
          Row(
            children: [
              Expanded(
                flex: newPct == 0 ? 0 : newPct,
                child: newPct == 0
                    ? const SizedBox()
                    : Container(
                        height: 10,
                        decoration: BoxDecoration(
                          color: Dt.newGreen(theme.brightness),
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
              ),
              if (newPct > 0 && existingPct > 0) const SizedBox(width: 3),
              Expanded(
                flex: existingPct == 0 ? 0 : existingPct,
                child: existingPct == 0
                    ? const SizedBox()
                    : Container(
                        height: 10,
                        decoration: BoxDecoration(
                          color: Dt.existingBlue(theme.brightness),
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
              ),
            ],
          ),
        const SizedBox(height: Dt.s8),
        if (!showBar)
          // Nothing logged: percent captions would mislead in the other
          // direction ("100% existing") — say so plainly instead.
          Text(
            'No clients logged yet.',
            style: theme.textTheme.bodySmall?.copyWith(
              color: Dt.textMuted(theme),
            ),
          )
        else
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '$newPct%',
                      style: Dt.tab(theme.textTheme.bodyMedium!).copyWith(
                        color: Dt.newGreen(theme.brightness),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const TextSpan(text: ' new'),
                  ],
                ),
                style: theme.textTheme.bodyMedium,
              ),
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '$existingPct%',
                      style: Dt.tab(theme.textTheme.bodyMedium!).copyWith(
                        color: Dt.existingBlue(theme.brightness),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const TextSpan(text: ' existing'),
                  ],
                ),
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ),
      ],
    );
  }
}

/// An "aside" section title (BY DAY / MOST CLIENTS / GO TO …).
class AsideCaption extends StatelessWidget {
  const AsideCaption(this.text, {super.key, this.divider = false});

  final String text;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: divider ? const EdgeInsets.only(bottom: 6) : EdgeInsets.zero,
      decoration: divider
          ? BoxDecoration(
              border: Border(
                bottom: BorderSide(color: theme.colorScheme.outline),
              ),
            )
          : null,
      child: Text(text.toUpperCase(), style: microLabel(theme)),
    );
  }
}

/// A quick-action row with icon, label and trailing arrow (dashboard "GO TO").
class QuickAction extends StatelessWidget {
  const QuickAction({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dt.rSm),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: theme.colorScheme.outline)),
        ),
        child: Row(
          children: [
            Icon(icon, size: 20, color: Dt.textMuted(theme)),
            const SizedBox(width: 14),
            Expanded(child: Text(label, style: theme.textTheme.bodyLarge)),
            Text('→', style: theme.textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}
