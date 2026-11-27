package com.autofarma;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class AutoFarmMod implements ModInitializer {
    public static final String MOD_ID = "autofarm";
    private static MinecraftServer server;
    private static AutoFarmManager manager;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SchematicUploadPayload.ID, SchematicUploadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDashboardPayload.ID, OpenDashboardPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BuildStatePayload.ID, BuildStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SchematicUploadPayload.ID, (payload, context) ->
                context.server().execute(() ->
                        manager.importScheme(context.player(), payload.fileName(), payload.json())));

        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
            server = startedServer;
            manager = new AutoFarmManager(startedServer);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> {
            server = null;
            manager = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(startedServer -> {
            if (manager != null) manager.tick();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = literal("autofarm")
                    .requires(source -> source.getEntity() instanceof ServerPlayerEntity);
            root.then(literal("set")
                    .then(argument("corner", IntegerArgumentType.integer(1, 2))
                            .then(argument("position", BlockPosArgumentType.blockPos())
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        int corner = IntegerArgumentType.getInteger(context, "corner");
                                        BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(context, "position");
                                        manager.setCorner(player, corner, pos);
                                        return 1;
                                    }))));
            root.then(literal("give")
                    .then(argument("floors", IntegerArgumentType.integer(1, 30))
                            .executes(context -> {
                                manager.setFloorCount(context.getSource().getPlayer(),
                                        IntegerArgumentType.getInteger(context, "floors"));
                                return 1;
                            })));
            root.then(literal("speed")
                    .then(argument("ticks", IntegerArgumentType.integer(1, 20))
                            .executes(context -> {
                                manager.setPlacementDelay(context.getSource().getPlayer(),
                                        IntegerArgumentType.getInteger(context, "ticks"));
                                return 1;
                            })));
            root.then(literal("schema")
                    .then(literal("load")
                            .then(argument("name", StringArgumentType.word())
                                    .executes(context -> {
                                        manager.loadScheme(context.getSource().getPlayer(),
                                                StringArgumentType.getString(context, "name"));
                                        return 1;
                                    }))));
            root.then(literal("start").executes(context -> {
                manager.start(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("pause").executes(context -> {
                manager.pause(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("stop").executes(context -> {
                manager.stop(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("delete").executes(context -> {
                manager.deleteBuild(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("delate").executes(context -> {
                manager.deleteBuild(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("check").executes(context -> {
                manager.checkBlocks(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("info").executes(context -> {
                manager.sendInfo(context.getSource().getPlayer());
                return 1;
            }));
            root.then(literal("gui").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                ServerPlayNetworking.send(player, new OpenDashboardPayload());
                return 1;
            }));

            dispatcher.register(root);
        });
    }

    public static MinecraftServer server() {
        return server;
    }

    public static AutoFarmManager manager() {
        return manager;
    }
}
