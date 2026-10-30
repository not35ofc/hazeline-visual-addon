package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.text.OrderedText;

import com.hazeline.module.modules.render.NameTag;

/**
 * Name Tag: animated colours in chat. {@code ChatHud$1} (the line consumer created in
 * {@code ChatHud#render(Backend, ...)}) passes {@code ChatHudLine$Visible#content()} to
 * {@code Backend#text} for every visible line - both for drawing and for finding the text under the
 * mouse. The line is wrapped so the name letters get this frame's colours (widths unchanged).
 * Verified in the 1.21.11 bytecode (one content() call in {@code accept}).
 */
@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$1")
public abstract class NameTagChatLineMixin {
	@ModifyExpressionValue(method = "accept", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;content()Lnet/minecraft/text/OrderedText;"))
	private OrderedText hazeline$nameTag(OrderedText original) {
		return NameTag.chatDraw(original);
	}
}
