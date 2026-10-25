package com.skyforged.network;

import com.skyforged.Skyforged;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: shout number {@code shout} is now on cooldown for {@code ticks} ticks. */
public record CooldownPayload(int shout, int ticks) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Skyforged.MOD_ID, "cooldown");
	public static final CustomPacketPayload.Type<CooldownPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CooldownPayload::shout,
		ByteBufCodecs.VAR_INT, CooldownPayload::ticks,
		CooldownPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
