package com.datev.dbui.tui;

import com.datev.dbui.config.KeyBinding;
import com.datev.dbui.config.KeyBindings;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/** Thin adapter from validated configuration to terminal keys. */
public record EditorKeys(Binding send, Binding newline, Binding orientationCycle, Binding orientationFallback,
		Binding complete, Binding completeAlternate) {

	@FunctionalInterface
	public interface Binding {
		boolean matches(KeyEvent key);
	}

	public static EditorKeys from(KeyBindings bindings) {
		return new EditorKeys(key(new KeyBinding("enter")), key(bindings.newline()), key(bindings.orientationCycle()),
			key(bindings.orientationFallback()), key(bindings.complete()), key(bindings.completeAlternate()));
	}

	public boolean requestsCompletion(KeyEvent key) {
		return complete.matches(key) || completeAlternate.matches(key);
	}

	private static Binding key(KeyBinding binding) {
		boolean ctrl = binding.modifier() == KeyBinding.Modifier.CTRL;
		boolean alt = binding.modifier() == KeyBinding.Modifier.ALT;
		boolean shift = binding.modifier() == KeyBinding.Modifier.SHIFT;
		String base = binding.key();
		KeyTrigger trigger = switch (base) {
			case "tab" -> KeyTrigger.key(KeyCode.TAB, ctrl, alt, shift);
			case "enter" -> KeyTrigger.key(KeyCode.ENTER, ctrl, alt, shift);
			default -> {
				char character = base.equals("space") ? ' ' : base.charAt(0);
				yield ctrl ? KeyTrigger.ctrl(character) : KeyTrigger.alt(character);
			}
		};
		// Tab and Enter distinguish Shift explicitly. Characters may need Shift on a non-US keyboard;
		// retain KeyTrigger's shifted-character matching (for example German Alt+Shift+7 for Alt-/).
		boolean exactShift = base.equals("tab") || base.equals("enter");
		return key -> (!exactShift || key.hasShift() == shift)
			&& !key.hasSuper() && !key.hasHyper() && !key.hasMeta() && trigger.matches(key);
	}

	public static EditorKeys defaults() {
		return from(KeyBindings.defaults());
	}
}
