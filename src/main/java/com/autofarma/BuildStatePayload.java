package com.autofarma;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record BuildStatePayload(
        BlockPos origin,
        int floors,
        int placementIndex,
        int totalPlacements,
        int placementDelay,
        int status,
        List<CactusScheme.Placement> placements
) implements CustomPayload {
    public static final Id<BuildStatePayload> ID = new Id<>(Identifier.of(AutoFarmMod.MOD_ID, "build_state"));
    public static final PacketCodec<RegistryByteBuf, BuildStatePayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeBlockPos(payload.origin());
                buf.writeVarInt(payload.floors());
                buf.writeVarInt(payload.placementIndex());
                buf.writeVarInt(payload.totalPlacements());
                buf.writeVarInt(payload.placementDelay());
                buf.writeVarInt(payload.status());
                buf.writeVarInt(payload.placements().size());
                for (CactusScheme.Placement placement : payload.placements()) {
                    buf.writeVarInt(placement.x());
                    buf.writeVarInt(placement.y());
                    buf.writeVarInt(placement.z());
                    buf.writeIdentifier(Registries.BLOCK.getId(placement.block()));
                }
            },
            buf -> {
                BlockPos origin = buf.readBlockPos();
                int floors = buf.readVarInt();
                int placementIndex = buf.readVarInt();
                int totalPlacements = buf.readVarInt();
                int placementDelay = buf.readVarInt();
                int status = buf.readVarInt();
                int size = buf.readVarInt();
                if (size < 0 || size > 32768) throw new IllegalArgumentException("Invalid AutoFarm scheme size");
                List<CactusScheme.Placement> placements = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    int x = buf.readVarInt();
                    int y = buf.readVarInt();
                    int z = buf.readVarInt();
                    Identifier blockId = buf.readIdentifier();
                    if (!Registries.BLOCK.containsId(blockId)) {
                        throw new IllegalArgumentException("Unknown block in AutoFarm scheme: " + blockId);
                    }
                    Block block = Registries.BLOCK.get(blockId);
                    placements.add(new CactusScheme.Placement(x, y, z, block));
                }
                return new BuildStatePayload(
                        origin, floors, placementIndex, totalPlacements, placementDelay, status, placements
                );
            }
    );

    public BuildStatePayload {
        placements = List.copyOf(placements);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
