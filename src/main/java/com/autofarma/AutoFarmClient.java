package com.autofarma;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public final class AutoFarmClient implements ClientModInitializer {
    private static BuildStatePayload buildState;
    private static List<CactusScheme.Placement> schemePlacements = List.of();
    private static int placementCooldown;
    private static int blockedTicks;
    private static int observedPlacement = -1;
    private static Vec3d previousPosition;
    private static boolean driving;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenDashboardPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new AutoFarmDashboardScreen())));
        ClientPlayNetworking.registerGlobalReceiver(BuildStatePayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!payload.placements().isEmpty()) schemePlacements = payload.placements();
                    buildState = payload;
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            buildState = null;
            schemePlacements = List.of();
            placementCooldown = 0;
            blockedTicks = 0;
            observedPlacement = -1;
            previousPosition = null;
            releaseControls(client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(AutoFarmClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(AutoFarmClient::renderHologram);
    }

    private static void tick(MinecraftClient client) {
        BuildStatePayload state = buildState;
        ClientPlayerEntity player = client.player;
        if (state == null || player == null || client.world == null
                || state.status() != BuildStatus.BUILDING.ordinal() || client.currentScreen != null) {
            releaseControls(client);
            return;
        }

        driving = true;
        if (state.placementIndex() != observedPlacement) {
            observedPlacement = state.placementIndex();
            blockedTicks = 0;
        }
        if (placementCooldown > 0) {
            placementCooldown--;
        }
        List<CactusScheme.Placement> placements = schemePlacements;
        int index = Math.min(state.placementIndex(), placements.size() - 1);
        if (index < 0 || state.totalPlacements() != placements.size()) {
            releaseControls(client);
            return;
        }
        CactusScheme.Placement placement = placements.get(index);
        BlockPos target = state.origin().add(placement.x(), placement.y(), placement.z());
        if (client.world.getBlockState(target).isOf(placement.block())) {
            placementCooldown = 0;
            releaseMovementOnly(client);
            return;
        }

        BlockPos supportPos = null;
        Direction clickedFace = null;
        double closest = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = target.offset(direction);
            BlockState support = client.world.getBlockState(candidate);
            Direction face = direction.getOpposite();
            if (!support.isSideSolidFullSquare(client.world, candidate, face)) continue;
            double distance = player.squaredDistanceTo(Vec3d.ofCenter(candidate));
            if (distance < closest) {
                closest = distance;
                supportPos = candidate;
                clickedFace = face;
            }
        }
        if (supportPos == null || clickedFace == null) {
            pauseIfStuck(client, player, false);
            return;
        }

        Item requiredItem = placement.block() == net.minecraft.block.Blocks.WATER
                ? Items.WATER_BUCKET : placement.block().asItem();
        int slot = findHotbarItem(player, requiredItem);
        if (requiredItem != Items.AIR && slot < 0) {
            releaseMovementOnly(client);
            return;
        }
        if (slot >= 0) player.getInventory().selectedSlot = slot;

        Vec3d hitPosition = Vec3d.ofCenter(supportPos).add(
                clickedFace.getOffsetX() * 0.5,
                clickedFace.getOffsetY() * 0.5,
                clickedFace.getOffsetZ() * 0.5
        );
        Vec3d standingPosition = standingPosition(supportPos, clickedFace, player);
        double dx = hitPosition.x - player.getX();
        double dy = hitPosition.y - player.getEyeY();
        double dz = hitPosition.z - player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        player.setYaw(player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - player.getYaw()), -8.0f, 8.0f));
        player.setPitch(player.getPitch()
                + MathHelper.clamp(MathHelper.wrapDegrees(targetPitch - player.getPitch()), -8.0f, 8.0f));

        double moveX = standingPosition.x - player.getX();
        double moveZ = standingPosition.z - player.getZ();
        boolean atPosition = moveX * moveX + moveZ * moveZ < 0.55 * 0.55
                && Math.abs(standingPosition.y - player.getY()) < 1.1;
        double yawRadians = Math.toRadians(player.getYaw());
        double forward = -moveX * Math.sin(yawRadians) + moveZ * Math.cos(yawRadians);
        double sideways = moveX * Math.cos(yawRadians) + moveZ * Math.sin(yawRadians);
        client.options.forwardKey.setPressed(!atPosition && forward > 0.1);
        client.options.backKey.setPressed(!atPosition && forward < -0.1);
        client.options.rightKey.setPressed(!atPosition && sideways > 0.1);
        client.options.leftKey.setPressed(!atPosition && sideways < -0.1);
        client.options.jumpKey.setPressed(!atPosition && standingPosition.y > player.getY() + 0.5);
        if (!atPosition && pauseIfStuck(client, player, true)) return;
        if (atPosition) {
            previousPosition = player.getPos();
        }

        if (!atPosition || placementCooldown > 0 || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(supportPos)
                || hit.getSide() != clickedFace) {
            if (atPosition && placementCooldown == 0) pauseIfStuck(client, player, false);
            return;
        }

        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            placementCooldown = Math.max(1, state.placementDelay());
            blockedTicks = 0;
        }
    }

    private static Vec3d standingPosition(BlockPos support, Direction face, ClientPlayerEntity player) {
        if (face.getAxis().isVertical()) {
            Direction side = Direction.getFacing(player.getX() - support.getX() - 0.5, 0,
                    player.getZ() - support.getZ() - 0.5);
            if (side.getAxis().isVertical()) side = Direction.NORTH;
            return new Vec3d(support.getX() + 0.5 + side.getOffsetX() * 1.35,
                    support.getY() + 1.0,
                    support.getZ() + 0.5 + side.getOffsetZ() * 1.35);
        }
        return new Vec3d(support.getX() + 0.5 + face.getOffsetX() * 1.35,
                support.getY() + 1.0,
                support.getZ() + 0.5 + face.getOffsetZ() * 1.35);
    }

    private static int findHotbarItem(ClientPlayerEntity player, Item item) {
        if (item == Items.AIR) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isOf(item)) return slot;
        }
        return -1;
    }

    private static void releaseMovementOnly(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
    }

    private static void releaseControls(MinecraftClient client) {
        if (driving) {
            releaseMovementOnly(client);
            driving = false;
            blockedTicks = 0;
            previousPosition = null;
        }
    }

    private static boolean pauseIfStuck(MinecraftClient client, ClientPlayerEntity player, boolean moving) {
        Vec3d current = player.getPos();
        if (!moving || (previousPosition != null && current.squaredDistanceTo(previousPosition) < 0.0004)) {
            blockedTicks++;
        } else {
            blockedTicks = 0;
        }
        previousPosition = current;
        if (blockedTicks < 100) return false;

        BuildStatePayload state = buildState;
        if (state != null && state.status() == BuildStatus.BUILDING.ordinal()) {
            buildState = new BuildStatePayload(
                    state.origin(), state.floors(), state.placementIndex(), state.totalPlacements(),
                    state.placementDelay(), BuildStatus.PAUSED.ordinal(), state.placements()
            );
            player.networkHandler.sendChatCommand("autofarm pause");
            player.sendMessage(Text.literal("§eAutoFarm: brak drogi do kolejnego bloku; budowa wstrzymana."), false);
        }
        releaseControls(client);
        return true;
    }

    private static void renderHologram(WorldRenderContext context) {
        BuildStatePayload state = buildState;
        if (state == null || context.world() == null || context.matrixStack() == null
                || context.consumers() == null || state.status() == BuildStatus.STOPPED.ordinal()) return;

        Vec3d camera = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        List<CactusScheme.Placement> placements = schemePlacements;
        int rendered = 0;
        for (int index = Math.max(0, state.placementIndex()); index < placements.size() && rendered < 1200; index++) {
            CactusScheme.Placement placement = placements.get(index);
            BlockPos pos = state.origin().add(placement.x(), placement.y(), placement.z());
            if (pos.getSquaredDistance(camera.x, camera.y, camera.z) > 48.0 * 48.0
                    || context.world().getBlockState(pos).isOf(placement.block())) continue;
            VertexRendering.drawBox(
                    context.matrixStack(),
                    lines,
                    pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    pos.getX() + 1 - camera.x, pos.getY() + 1 - camera.y, pos.getZ() + 1 - camera.z,
                    0.2f, 0.9f, 0.3f, 0.32f
            );
            rendered++;
        }
    }
}
