package com.autofarma;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class BuildRuntime {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos origin;
    private final int floors;
    private final BuildScheme scheme;
    private BuildStatus status = BuildStatus.STOPPED;
    private int placementIndex;
    private int ticksUntilSync;
    private String lastMessage = "Ready";

    public BuildRuntime(ServerPlayerEntity player, ServerWorld world, BlockPos origin, int floors) {
        this(player, world, origin, floors, BuildScheme.cactus(floors));
    }

    public BuildRuntime(ServerPlayerEntity player, ServerWorld world, BlockPos origin, int floors, BuildScheme scheme) {
        this.player = player;
        this.world = world;
        this.origin = origin;
        this.floors = floors;
        this.scheme = scheme;
    }

    public ServerPlayerEntity player() { return player; }
    public ServerWorld world() { return world; }
    public BlockPos origin() { return origin; }
    public int floors() { return floors; }
    public BuildScheme scheme() { return scheme; }
    public List<CactusScheme.Placement> placements() { return scheme.placements(); }
    public BuildStatus status() { return status; }
    public int placementIndex() { return placementIndex; }
    public int ticksUntilSync() { return ticksUntilSync; }
    public String lastMessage() { return lastMessage; }
    public void setStatus(BuildStatus status) { this.status = status; }
    public void setPlacementIndex(int placementIndex) { this.placementIndex = placementIndex; }
    public void setTicksUntilSync(int ticksUntilSync) { this.ticksUntilSync = ticksUntilSync; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
}
