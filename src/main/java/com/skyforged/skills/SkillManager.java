package com.skyforged.skills;

import com.skyforged.Skyforged;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SkillManager {
	private static final Identifier COMBAT_MODIFIER = Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "combat_skill");
	private static final Identifier MINING_MODIFIER = Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "mining_skill");

	private SkillManager() {
	}

	public static Skills get(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.SKILLS, Skills.EMPTY);
	}

	public static void addXp(ServerPlayer player, SkillType type, int amount) {
		if (amount <= 0) {
			return;
		}

		Skills before = get(player);
		int oldLevel = before.level(type);
		int newXp = (int) Math.min(Integer.MAX_VALUE, (long) before.xp(type) + amount);
		Skills after = before.withXp(type, newXp);
		player.setAttached(ModAttachments.SKILLS, after);

		int newLevel = after.level(type);
		if (newLevel > oldLevel) {
			player.sendSystemMessage(
				Component.literal(type.displayName() + " increased to " + newLevel + "!").withStyle(ChatFormatting.GOLD)
			);
			applyBonuses(player);
		}
	}

	/**
	 * Combat: +1% attack damage per level above 1.
	 * Mining: +1.5% block break speed per level above 1.
	 * (Voice has no attribute: it shortens shout cooldowns and strengthens them, see Shouts.)
	 */
	public static void applyBonuses(ServerPlayer player) {
		Skills skills = get(player);
		setModifier(player, Attributes.ATTACK_DAMAGE, COMBAT_MODIFIER, 0.01 * (skills.level(SkillType.COMBAT) - 1));
		setModifier(player, Attributes.BLOCK_BREAK_SPEED, MINING_MODIFIER, 0.015 * (skills.level(SkillType.MINING) - 1));
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.addOrReplacePermanentModifier(
			new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
		);
	}
}
