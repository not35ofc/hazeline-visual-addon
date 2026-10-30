package com.hazeline.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;

import com.hazeline.module.modules.render.NameTag;

/**
 * Name Tag: your name in chat. {@code ChatHud#addVisibleMessage(ChatHudLine)} wraps a message into
 * display lines with {@code ChatHudLine#breakLines}; a copy with the name swapped is wrapped instead, so
 * line widths fit the new name. The stored message ({@code messages}), the chat log and the account are
 * not changed. {@code ChatHud#reset()} (refresh) re-wraps every stored message, which the module calls
 * when its settings change. Verified in the 1.21.11 bytecode (one breakLines call in addVisibleMessage).
 */
@Mixin(ChatHud.class)
public abstract class NameTagChatMixin {
	@WrapOperation(method = "addVisibleMessage", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/ChatHudLine;breakLines(Lnet/minecraft/client/font/TextRenderer;I)Ljava/util/List;"))
	private List<OrderedText> hazeline$nameTag(ChatHudLine line, TextRenderer textRenderer, int width, Operation<List<OrderedText>> original) {
		return original.call(NameTag.chatLine(line), textRenderer, width);
	}
}
