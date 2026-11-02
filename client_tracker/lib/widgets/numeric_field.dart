import 'package:flutter/material.dart';
import 'package:flutter/services.dart'
    show KeyDownEvent, LogicalKeyboardKey, FilteringTextInputFormatter;

import '../design/app_theme.dart';

/// A fast numeric field for cumulative client counts.
///
/// - Non-negative integers only; empty counts as 0.
/// - Content is fully selected on focus so typing replaces it instantly.
/// - Enter/Tab moves to the next field in [focusOrder] where practical.
///
/// Editorial styling hooks: [accent] tints the focus border (and adds a soft
/// glow), [valueColor] colors entered digits, [placeholder] shows in empty
/// fields (the mockups use an en dash).
class NumericField extends StatefulWidget {
  const NumericField({
    super.key,
    required this.value,
    required this.onChanged,
    this.focusNode,
    this.nextFocusNode,
    this.onSubmitted,
    this.width = 72,
    this.height = 30,
    this.fontSize = 13,
    this.autofocus = false,
    this.accent,
    this.valueColor,
    this.placeholder = '',
    this.showZero = false,
    this.onClear,
  });

  final int value;
  final ValueChanged<int> onChanged;
  final FocusNode? focusNode;
  final FocusNode? nextFocusNode;
  final VoidCallback? onSubmitted;
  final double width;
  final double height;
  final double fontSize;
  final bool autofocus;

  /// Focus border + glow color (defaults to the scheme primary).
  final Color? accent;

  /// Text color for the entered value (defaults to onSurface).
  final Color? valueColor;

  /// Hint shown when the field is empty (e.g. '–').
  final String placeholder;

  /// When true, a value of 0 is displayed as `0` instead of the blank
  /// placeholder. Used by the per-week "show zeros" preference.
  final bool showZero;

  /// Called when the user clears this field (Backspace on an empty field, or
  /// a dedicated Clear affordance). The caller should restore the previously
  /// committed value (via the undo buffer) rather than leaving a blank.
  final VoidCallback? onClear;

  @override
  State<NumericField> createState() => _NumericFieldState();
}

class _NumericFieldState extends State<NumericField> {
  late final TextEditingController _controller;
  late final FocusNode _focus;
  bool _suppress = false;
  bool _focused = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: _display(widget.value));
    _focus = widget.focusNode ?? FocusNode();
    _focus.addListener(_onFocusChanged);
  }

  @override
  void didUpdateWidget(covariant NumericField old) {
    super.didUpdateWidget(old);
    // Refresh text when the value changes externally (e.g. refresh/reload),
    // but never while the user is editing this exact field. A show-zeros
    // preference change is different: it changes only the presentation of a
    // zero, so apply it even if this field retained focus after the toggle was
    // clicked. Otherwise focused cells can keep showing stale zeros/dashes.
    final zeroDisplayChanged =
        old.showZero != widget.showZero && widget.value == 0;
    final valueChanged =
        widget.value != int.tryParse(_controller.text.replaceAll(',', ''));
    if (zeroDisplayChanged || (!_focus.hasFocus && valueChanged)) {
      _controller.text = _display(widget.value);
    }
  }

  void _onFocusChanged() {
    setState(() => _focused = _focus.hasFocus);
    if (_focus.hasFocus) {
      // Select all so the first keystroke replaces the current value.
      _controller.selection = TextSelection(
        baseOffset: 0,
        extentOffset: _controller.text.length,
      );
    }
  }

  String _display(int v) => widget.showZero ? '$v' : (v == 0 ? '' : '$v');

  void _handleChange(String text) {
    if (_suppress) return;
    final cleaned = text.replaceAll(RegExp(r'[^0-9]'), '');
    final parsed = cleaned.isEmpty ? 0 : int.parse(cleaned);
    if (parsed != widget.value) {
      widget.onChanged(parsed);
    }
    // Keep the field showing the canonical value (strips junk chars).
    final display = _display(parsed);
    if (display != _controller.text) {
      _suppress = true;
      _controller.text = display;
      _controller.selection = TextSelection.collapsed(offset: display.length);
      _suppress = false;
    }
  }

  void _handleSubmitted() {
    widget.onSubmitted?.call();
    if (widget.nextFocusNode != null) {
      widget.nextFocusNode!.requestFocus();
    } else {
      _focus.unfocus();
    }
  }

  @override
  void dispose() {
    if (widget.focusNode == null) _focus.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final accent = widget.accent ?? scheme.primary;
    final dark = theme.brightness == Brightness.dark;

    return Focus(
      // Keep this key-handler node separate from the TextField's node. The
      // EditableText inside TextField also installs a Focus widget for
      // `focusNode`; reusing `_focus` here attaches the same node twice in a
      // parent/child relationship and breaks the web focus tree when the
      // weekly grid mounts all of its inputs.
      autofocus: widget.autofocus,
      onKeyEvent: _handleKey,
      child: SizedBox(
        // Expanded cells pass infinity to mean "take the width my parent
        // gives me". Forwarding infinity as a tight SizedBox constraint
        // triggers Flutter's BoxConstraints assertion and paints the yellow
        // error stripe over every tracker field.
        width: widget.width.isFinite ? widget.width : null,
        height: widget.height,
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(Dt.rSm),
            boxShadow: _focused
                ? [
                    BoxShadow(
                      color: accent.withValues(alpha: dark ? 0.25 : 0.18),
                      blurRadius: 8,
                    ),
                  ]
                : null,
          ),
          child: TextField(
            controller: _controller,
            focusNode: _focus,
            autofocus: widget.autofocus,
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            textAlignVertical: TextAlignVertical.center,
            cursorWidth: 1,
            style: Dt.mono(
              context,
              size: widget.fontSize,
              weight: FontWeight.w600,
              color: widget.valueColor ?? scheme.onSurface,
            ),
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: InputDecoration(
              isDense: true,
              filled: true,
              fillColor: _focused
                  ? scheme.surfaceContainerLowest
                  : scheme.surfaceContainerLowest.withValues(
                      alpha: dark ? 1 : 0.75,
                    ),
              hintStyle: Dt.mono(
                context,
                size: widget.fontSize,
                weight: FontWeight.w400,
                color: Dt.textFaint(theme),
              ),
              hintText: widget.placeholder,
              contentPadding: EdgeInsets.zero,
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(Dt.rSm),
                borderSide: BorderSide(
                  color: scheme.outlineVariant.withValues(
                    alpha: dark ? 1 : 0.9,
                  ),
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(Dt.rSm),
                borderSide: BorderSide(color: accent, width: 1.4),
              ),
            ),
            onChanged: _handleChange,
            onSubmitted: (_) => _handleSubmitted(),
          ),
        ),
      ),
    );
  }

  /// A Backspace on an already-empty field is treated as a clear gesture:
  /// the caller's [onClear] should restore the previously committed value
  /// (via the undo buffer) rather than leaving the field blank.
  KeyEventResult _handleKey(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        event.logicalKey == LogicalKeyboardKey.backspace &&
        _controller.text.isEmpty) {
      widget.onClear?.call();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }
}
