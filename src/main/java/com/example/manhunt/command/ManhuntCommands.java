package com.example.manhunt.command;

import com.example.manhunt.data.Role;
import com.example.manhunt.data.StateSaverAndLoader;
import com.example.manhunt.item.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.*;

public class ManhuntCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ManhuntCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("manhunt")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("set")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("role", StringArgumentType.word())
                                        .executes(context -> {
                                            String playerName = StringArgumentType.getString(context, "player");
                                            String roleName = StringArgumentType.getString(context, "role");
                                            return setRole(context.getSource(), playerName, roleName);
                                        })
                                )
                        )
                )
                .then(literal("remove")
                        .then(argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    return removeRole(context.getSource(), playerName);
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> listPlayers(context.getSource()))
                )
                .then(literal("random")
                        .executes(context -> randomAssign(context.getSource()))
                )
                .then(literal("clear")
                        .executes(context -> clearAll(context.getSource()))
                )
        );
    }

    private static int setRole(ServerCommandSource source, String playerName, String roleName) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            source.sendError(Text.literal("找不到玩家: " + playerName));
            return 0;
        }
        Role role;
        try {
            role = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("无效角色: " + roleName + "，可用: NONE, SPEEDRUNNER, HUNTER"));
            return 0;
        }
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(source.getServer());
        state.setPlayerRole(target.getUuid(), role);
        source.sendMessage(Text.literal("已设置 " + playerName + " 为 " + role.name()).formatted(Formatting.GREEN));
        target.sendMessage(Text.literal("你被分配为 " + role.name()).formatted(Formatting.GOLD));
        if (role == Role.HUNTER) {
            target.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(ModItems.HUNTER_COMPASS));
        }
        return 1;
    }

    private static int removeRole(ServerCommandSource source, String playerName) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            source.sendError(Text.literal("找不到玩家: " + playerName));
            return 0;
        }
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(source.getServer());
        state.setPlayerRole(target.getUuid(), Role.NONE);
        source.sendMessage(Text.literal("已移除 " + playerName + " 的角色").formatted(Formatting.GREEN));
        target.sendMessage(Text.literal("你的角色已被移除").formatted(Formatting.YELLOW));
        return 1;
    }

    private static int listPlayers(ServerCommandSource source) {
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(source.getServer());
        Collection<ServerPlayerEntity> allPlayers = source.getServer().getPlayerManager().getPlayerList();
        if (allPlayers.isEmpty()) {
            source.sendMessage(Text.literal("没有在线玩家").formatted(Formatting.GRAY));
            return 0;
        }
        source.sendMessage(Text.literal("=== 玩家角色列表 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("【速通者】").formatted(Formatting.GREEN));
        boolean hasSpeedrunner = false;
        for (ServerPlayerEntity player : allPlayers) {
            if (state.isSpeedrunner(player.getUuid())) {
                source.sendMessage(Text.literal("  - " + player.getName().getString()).formatted(Formatting.GREEN));
                hasSpeedrunner = true;
            }
        }
        if (!hasSpeedrunner) source.sendMessage(Text.literal("  (无)").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("【猎人】").formatted(Formatting.RED));
        boolean hasHunter = false;
        for (ServerPlayerEntity player : allPlayers) {
            if (state.isHunter(player.getUuid())) {
                source.sendMessage(Text.literal("  - " + player.getName().getString()).formatted(Formatting.RED));
                hasHunter = true;
            }
        }
        if (!hasHunter) source.sendMessage(Text.literal("  (无)").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("【未分配】").formatted(Formatting.GRAY));
        for (ServerPlayerEntity player : allPlayers) {
            if (!state.getPlayerRole(player.getUuid()).isAssigned()) {
                source.sendMessage(Text.literal("  - " + player.getName().getString()).formatted(Formatting.GRAY));
            }
        }
        return 1;
    }

    private static int randomAssign(ServerCommandSource source) {
        Collection<ServerPlayerEntity> allPlayers = source.getServer().getPlayerManager().getPlayerList();
        if (allPlayers.size() < 2) {
            source.sendError(Text.literal("至少需要2名玩家才能开始游戏！"));
            return 0;
        }
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(source.getServer());
        state.clearAllRoles();
        List<ServerPlayerEntity> playerList = new ArrayList<>(allPlayers);
        Collections.shuffle(playerList);
        ServerPlayerEntity speedrunner = playerList.get(0);
        state.setPlayerRole(speedrunner.getUuid(), Role.SPEEDRUNNER);
        for (int i = 1; i < playerList.size(); i++) {
            ServerPlayerEntity hunter = playerList.get(i);
            state.setPlayerRole(hunter.getUuid(), Role.HUNTER);
            hunter.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(ModItems.HUNTER_COMPASS));
        }
        source.sendMessage(Text.literal("=== 角色分配完成 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("速通者: " + speedrunner.getName().getString()).formatted(Formatting.GREEN));
        for (ServerPlayerEntity player : allPlayers) {
            if (state.isHunter(player.getUuid())) {
                player.sendMessage(Text.literal("你是猎人！追踪速通者 " + speedrunner.getName().getString()).formatted(Formatting.RED));
            } else if (state.isSpeedrunner(player.getUuid())) {
                player.sendMessage(Text.literal("你是速通者！避开所有猎人！").formatted(Formatting.GREEN));
            }
        }
        return 1;
    }

    private static int clearAll(ServerCommandSource source) {
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(source.getServer());
        state.clearAllRoles();
        source.sendMessage(Text.literal("已清除所有角色").formatted(Formatting.YELLOW));
        return 1;
    }
}