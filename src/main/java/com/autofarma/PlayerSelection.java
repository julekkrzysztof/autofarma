package com.autofarma;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record PlayerSelection(BlockPos first, BlockPos second, ServerWorld world) {
    public BlockPos minCorner() {
        if (first == null) return null;
        return new BlockPos(
                first.getX(),
                second == null ? first.getY() : Math.min(first.getY(), second.getY()),
                first.getZ()
        );
    }

    public boolean isComplete() {
        return first != null && second != null;
    }

    public boolean isSameChunk() {
        return isComplete()
                && (first.getX() >> 4) == (second.getX() >> 4)
                && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    public boolean hasRequiredHeight(int floors) {
        if (!isSameChunk()) return false;
        int buildY = minCorner().getY();
        return buildY >= world.getBottomY()
                && buildY + CactusScheme.height(floors) <= world.getBottomY() + world.getHeight();
    }
}
