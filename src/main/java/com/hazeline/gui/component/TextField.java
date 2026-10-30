package com.hazeline.gui.component;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

import net.minecraft.client.util.InputUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import com.hazeline.gui.Theme;

/**
 * Minimal single-line text input drawn with {@link Theme}: typing, Backspace, Enter (submit),
 * Escape (cancel), Ctrl+V paste, Ctrl+C copy, Ctrl+A clear. Long text scrolls so the end
 * stays visible. Used by the hex field of the colour picker and by {@link TextFieldComponent}.
 */
public class TextField {
	private final int maxLength;
	private final IntPredicate allowedChar;
	private final Consumer<String> onSubmit;
	private String text = "";
	private boolean focused;

	public TextField(int maxLength, IntPredicate allowedChar, Consumer<String> onSubmit) {
		this.maxLength = maxLength;
		this.allowedChar = allowedChar;
		this.onSubmit = onSubmit;
	}

	public boolean isFocused() {
		return focused;
	}

	public String getText() {
		return text;
	}

	public void focus(String initialText) {
		focused = true;
		text = initialText == null ? "" : initialText;
	}

	/** Leaves the field, submitting the current text. */
	public void submit() {
		if (focused) {
			focused = false;
			onSubmit.accept(text);
		}
	}

	public void cancel() {
		focused = false;
	}

	public void render(DrawContext g, int x, int y, int w, int h, String unfocusedText) {
		render(g, x, y, w, h, unfocusedText, 0);
	}

	/**
	 * Rounded input box. {@code leftInset} leaves room for an icon drawn by the caller. While not
	 * focused, {@code unfocusedText} is shown (value or placeholder) in the secondary colour.
	 */
	public void render(DrawContext g, int x, int y, int w, int h, String unfocusedText, int leftInset) {
		int r = Math.min(Theme.cornerRadius(), h / 2);
		Theme.roundedRect(g, x, y, x + w, y + h, r, Theme.inset());
		Theme.roundedOutline(g, x, y, w, h, r, focused ? Theme.accent() : Theme.border());
		int tx = x + Theme.px(5) + leftInset;
		int innerW = w - Theme.px(10) - leftInset;
		String shown;
		if (focused) {
			boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
			shown = text + (caret ? "_" : " ");
			// Scroll: show the tail that fits.
			while (shown.length() > 1 && Theme.widthOf(shown, Theme.SMALL, false) > innerW) {
				shown = shown.substring(1);
			}
		} else {
			shown = Theme.fitOf(unfocusedText, innerW, Theme.SMALL, false);
		}
		Theme.boxTextOf(g, shown, tx, y, h, focused ? Theme.text() : Theme.textSecondary(), Theme.SMALL, false);
	}

	public boolean keyPressed(KeyInput event) {
		if (!focused) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		int key = event.key();
		if (event.isPaste()) {
			append(mc.keyboard.getClipboard());
		} else if (event.isCopy()) {
			mc.keyboard.setClipboard(text);
		} else if (event.isSelectAll()) {
			text = "";
		} else if (key == InputUtil.GLFW_KEY_ENTER || key == InputUtil.GLFW_KEY_KP_ENTER) {
			submit();
		} else if (key == InputUtil.GLFW_KEY_ESCAPE) {
			cancel();
		} else if (key == InputUtil.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
			text = text.substring(0, text.length() - 1);
		}
		return true; // a focused field swallows every key
	}

	public boolean charTyped(int codepoint) {
		if (!focused) {
			return false;
		}
		append(new String(Character.toChars(codepoint)));
		return true;
	}

	private void append(String input) {
		if (input == null) {
			return;
		}
		StringBuilder sb = new StringBuilder(text);
		input.codePoints().filter(allowedChar).forEach(cp -> {
			if (sb.length() < maxLength) {
				sb.appendCodePoint(cp);
			}
		});
		text = sb.toString();
	}
}
