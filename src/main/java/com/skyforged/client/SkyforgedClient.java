package com.skyforged.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyforged.Skyforged;
import com.skyforged.network.CooldownPayload;
import com.skyforged.network.ShoutPayload;
import com.skyforged.shouts.Shouts;
import com.skyforged.skills.ModAttachments;
import com.skyforged.skills.SkillType;
import com.skyforged.skills.Skills;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class SkyforgedClient implements ClientModInitializer {
	private static final int SHOUT_COUNT = Shouts.Shout.values().length;
	private static final String[] KEY_NAMES = {"gale", "ember", "blink"};
	private static final int[] DEFAULT_KEYS = {InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C};

	private static final KeyMapping[] KEYS = new KeyMapping[SHOUT_COUNT];
	// Cooldown bookkeeping, measured in client ticks.
	private static final long[] READY_AT = new long[SHOUT_COUNT];
	private static final int[] COOLDOWN_TOTAL = new int[SHOUT_COUNT];
	private static long clientTick = 0;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "main"));

		for (int i = 0; i < SHOUT_COUNT; i++) {
			KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + Skyforged.MOD_ID + "." + KEY_NAMES[i],
				InputConstants.Type.KEYSYM,
				DEFAULT_KEYS[i],
				category
			));
		}

		// Key presses: send each one to the server, which decides whether the shout fires.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTick++;
			if (client.player == null) {
				return;
			}
			for (int i = 0; i < SHOUT_COUNT; i++) {
				while (KEYS[i].consumeClick()) {
					ClientPlayNetworking.send(new ShoutPayload(i));
				}
			}
		});

		// The server tells us when a shout went on cooldown.
		ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE, (payload, context) -> {
			int index = payload.shout();
			if (index >= 0 && index < SHOUT_COUNT) {
				READY_AT[index] = clientTick + payload.ticks();
				COOLDOWN_TOTAL[index] = Math.max(1, payload.ticks());
			}
		});

		// HUD: skill levels at top-left, shout cooldowns at bottom-left.
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "hud"),
			(graphics, tickCounter) -> {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player == null) {
					return;
				}

				// --- Skills ---
				Skills skills = mc.player.getAttachedOrElse(ModAttachments.SKILLS, Skills.EMPTY);
				int x = 6;
				int y = 6;
				for (SkillType type : SkillType.values()) {
					graphics.text(mc.font, type.displayName() + " " + skills.level(type), x, y, 0xFFFFFFFF, true);
					int barX = x;
					int barY = y + 10;
					int barWidth = 60;
					graphics.fill(barX, barY, barX + barWidth, barY + 3, 0xAA000000);
					graphics.fill(barX, barY, barX + (int) (barWidth * skills.progress(type)), barY + 3, 0xFFE8B84A);
					y += 18;
				}

				// --- Shouts ---
				int boxWidth = 38;
				int boxHeight = 24;
				int baseY = mc.getWindow().getGuiScaledHeight() - boxHeight - 8;
				for (int i = 0; i < SHOUT_COUNT; i++) {
					int boxX = 6 + i * (boxWidth + 4);
					long remaining = READY_AT[i] - clientTick;
					boolean ready = remaining <= 0;

					graphics.fill(boxX, baseY, boxX + boxWidth, baseY + boxHeight, 0x99000000);
					if (!ready) {
						float fraction = Math.min(1.0f, remaining / (float) COOLDOWN_TOTAL[i]);
						int overlay = (int) (boxHeight * fraction);
						graphics.fill(boxX, baseY + boxHeight - overlay, boxX + boxWidth, baseY + boxHeight, 0xAA3A3A3A);
					}
					int accent = ready ? 0xFF55CC66 : 0xFF888888;
					graphics.fill(boxX, baseY, boxX + boxWidth, baseY + 2, accent);

					String key = KEYS[i].getTranslatedKeyMessage().getString();
					graphics.text(mc.font, key, boxX + 3, baseY + 5, 0xFFFFFFFF, true);
					String label = ready
						? Shouts.Shout.values()[i].displayName.split(" ")[0]
						: String.format("%.1fs", remaining / 20.0);
					graphics.text(mc.font, label, boxX + 3, baseY + 14, ready ? 0xFFAAFFAA : 0xFFFFFFFF, false);
				}
			}
		);
	}
}
