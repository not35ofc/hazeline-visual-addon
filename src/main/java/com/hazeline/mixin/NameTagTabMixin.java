package com.hazeline.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import com.hazeline.module.modules.render.NameTag;

/**
 * Name Tag: your name in the tab list. {@code PlayerListHud#getPlayerName(PlayerListEntry)} is called
 * every frame by {@code render} (once for the column width, once to draw), so the swapped name is
 * measured and drawn the same way (verified in the 1.21.11 bytecode). Only your own entry changes.
 */
@Mixin(PlayerListHud.class)
public abstract class NameTagTabMixin {
	@ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"), require = 0)
	private Text hazeline$nameTag(Text original, PlayerListEntry entry) {
		return NameTag.tabName(entry, original);
	}
}
