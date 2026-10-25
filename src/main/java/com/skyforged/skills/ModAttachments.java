package com.skyforged.skills;

import com.skyforged.Skyforged;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

public final class ModAttachments {
	private ModAttachments() {
	}

	/** Skill XP stored on the player: saved to disk, kept after death, synced to that player's client. */
	public static final AttachmentType<Skills> SKILLS = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "skills"),
		builder -> builder
			.initializer(() -> Skills.EMPTY)
			.persistent(Skills.CODEC)
			.copyOnDeath()
			.syncWith(Skills.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
	);

	/** Called from the mod initializer so this class (and the static field above) is loaded. */
	public static void init() {
	}
}
