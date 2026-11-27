package com.autofarma;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenDashboardPayload() implements CustomPayload {
    public static final Id<OpenDashboardPayload> ID =
            new Id<>(Identifier.of(AutoFarmMod.MOD_ID, "open_dashboard"));
    public static final PacketCodec<RegistryByteBuf, OpenDashboardPayload> CODEC =
            PacketCodec.unit(new OpenDashboardPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
