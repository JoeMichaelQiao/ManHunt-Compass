package com.joemichaelqiao.manhuntcompass;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.PersistentState;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 主入口：注册物品、命令、事件、周期更新逻辑
 *
 * 数据持久化已改为使用世界存档（PersistentState），存储在世界数据中以实现多存档隔离。
 */
public class ManhuntCompassMod implements ModInitializer {
    public static final String MODID = "manhuntcompass";
    public static Item TRACKER_COMPASS;

    // 用于周期更新
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        // 注册自定义物品（简单的无特殊行为 Item；我们通过 NBT 控制指向）
        TRACKER_COMPASS = new Item(new Item.Settings().group(ItemGroup.TOOLS).maxCount(1));
        Registry.register(Registry.ITEM, new Identifier(MODID, "tracker_compass"), TRACKER_COMPASS);

        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("manhunt")
                .then(CommandManager.literal("set_speedrunner")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            RoleState rs = getRoleState(ctx.getSource().getServer());
                            rs.speedrunner = p.getUuid();
                            rs.markDirty();
                            ctx.getSource().sendFeedback(new LiteralText("已设定速通者为 " + p.getName().getString()), true);
                            return 1;
                        }))
                )
                .then(CommandManager.literal("clear_roles")
                    .executes(ctx -> {
                        RoleState rs = getRoleState(ctx.getSource().getServer());
                        rs.speedrunner = null;
                        rs.hunters.clear();
                        rs.markDirty();
                        ctx.getSource().sendFeedback(new LiteralText("已清空所有角色"), true);
                        return 1;
                    })
                )
                .then(CommandManager.literal("add_hunter")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            RoleState rs = getRoleState(ctx.getSource().getServer());
                            rs.hunters.add(p.getUuid());
                            rs.markDirty();
                            ctx.getSource().sendFeedback(new LiteralText("已将 " + p.getName().getString() + " 标记为猎人"), true);
                            return 1;
                        }))
                )
                .then(CommandManager.literal("remove_hunter")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            RoleState rs = getRoleState(ctx.getSource().getServer());
                            rs.hunters.remove(p.getUuid());
                            rs.markDirty();
                            ctx.getSource().sendFeedback(new LiteralText("已将 " + p.getName().getString() + " 从猎人移除"), true);
                            return 1;
                        }))
                )
                .then(CommandManager.literal("list_roles")
                    .executes(ctx -> {
                        listRoles(ctx.getSource());
                        return 1;
                    })
                )
                .then(CommandManager.literal("select_random_speedrunner")
                    .then(CommandManager.argument("seed", net.minecraft.command.argument.IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            int seed = net.minecraft.command.argument.IntegerArgumentType.getInteger(ctx, "seed");
                            selectRandomSpeedrunner(ctx.getSource().getServer(), seed, ctx.getSource());
                            return 1;
                        }))
                    .executes(ctx -> {
                        selectRandomSpeedrunner(ctx.getSource().getServer(), (int) System.currentTimeMillis(), ctx.getSource());
                        return 1;
                    })
                )
                .then(CommandManager.literal("auto_assign_hunters")
                    .then(CommandManager.argument("count", net.minecraft.command.argument.IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int cnt = net.minecraft.command.argument.IntegerArgumentType.getInteger(ctx, "count");
                            autoAssignHunters(ctx.getSource().getServer(), cnt, ctx.getSource());
                            return 1;
                        }))
                )
            );
        });

        // 给猎人重生时发放指南针（COPY 在玩家重生/复制时触发）
        ServerPlayerEvents.COPY.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer == null) return;
            RoleState rs = getRoleState(newPlayer.getServer());
            if (rs.hunters.contains(newPlayer.getUuid())) {
                giveTrackerCompassOnRespawn(newPlayer);
            }
        });

        // 周期性更新猎人背包中 tracker_compass 的 lodestone NBT，使指南针指向当前速通者（每 10 tick）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 10) return;
            tickCounter = 0;

            RoleState rs = getRoleState(server);
            if (rs.speedrunner == null) return;

            ServerPlayerEntity sr = server.getPlayerManager().getPlayer(rs.speedrunner);
            if (sr == null) return;

            BlockPos targetPos = sr.getBlockPos();
            RegistryKey<World> dim = sr.world.getRegistryKey();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!rs.hunters.contains(player.getUuid())) continue;
                updateCompassesInInventory(player, targetPos, dim);
            }
        });
    }

    // 将 RoleState 存储在 Overworld 的 PersistentStateManager 中，实现按存档隔离
    private static RoleState getRoleState(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        return overworld.getPersistentStateManager().getOrCreate(RoleState::fromNbt, RoleState::new, "manhuntcompass_roles");
    }

    private void listRoles(ServerCommandSource src) {
        RoleState rs = getRoleState(src.getServer());
        StringBuilder sb = new StringBuilder();
        sb.append("速通者: ");
        if (rs.speedrunner != null) {
            ServerPlayerEntity sp = src.getServer().getPlayerManager().getPlayer(rs.speedrunner);
            if (sp != null) sb.append(sp.getName().getString()).append(" (").append(rs.speedrunner).append(")");
            else sb.append(rs.speedrunner.toString());
        } else {
            sb.append("未设定");
        }
        sb.append("\n猎人: ");
        if (rs.hunters.isEmpty()) {
            sb.append("无");
        } else {
            List<String> names = new ArrayList<>();
            for (UUID u : rs.hunters) {
                ServerPlayerEntity p = src.getServer().getPlayerManager().getPlayer(u);
                if (p != null) names.add(p.getName().getString());
                else names.add(u.toString());
            }
            sb.append(String.join(", ", names));
        }
        src.sendFeedback(new LiteralText(sb.toString()), false);
    }

    private void selectRandomSpeedrunner(MinecraftServer server, int seed, ServerCommandSource src) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            src.sendFeedback(new LiteralText("没有在线玩家可供选择。"), false);
            return;
        }
        int idx = Math.abs(seed) == 0 ? ThreadLocalRandom.current().nextInt(players.size()) : Math.floorMod(seed, players.size());
        ServerPlayerEntity chosen = players.get(idx);
        RoleState rs = getRoleState(server);
        rs.speedrunner = chosen.getUuid();
        rs.markDirty();
        src.sendFeedback(new LiteralText("随机选中速通者: " + chosen.getName().getString()), true);
    }

    private void autoAssignHunters(MinecraftServer server, int count, ServerCommandSource src) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.size() <= 1) {
            src.sendFeedback(new LiteralText("在线玩家不足以分配猎人。"), false);
            return;
        }
        RoleState rs = getRoleState(server);
        // 如果没有速通者，随机选择一个
        if (rs.speedrunner == null) {
            selectRandomSpeedrunner(server, (int) System.currentTimeMillis(), src);
            rs = getRoleState(server);
        }

        // 构建候选（去掉速通者）
        List<ServerPlayerEntity> candidates = players.stream()
            .filter(p -> !p.getUuid().equals(rs.speedrunner))
            .collect(Collectors.toList());

        Collections.shuffle(candidates);
        rs.hunters.clear();
        int assignCount = Math.min(count, candidates.size());
        for (int i = 0; i < assignCount; i++) {
            rs.hunters.add(candidates.get(i).getUuid());
        }
        rs.markDirty();
        src.sendFeedback(new LiteralText("已自动分配 " + assignCount + " 名猎人。"), true);
    }

    // 在玩家重生时发放 tracker_compass（如果没有）
    private void giveTrackerCompassOnRespawn(ServerPlayerEntity player) {
        boolean has = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == TRACKER_COMPASS) {
                has = true;
                break;
            }
        }
        if (!has) {
            ItemStack stack = new ItemStack(TRACKER_COMPASS);
            // NBT 会在下一个 tick 的周期更新中被覆盖（如果设置了速通者）
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
        }
    }

    // 更新玩家物品栏内所有 tracker_compass 的 Lodestone NBT
    private void updateCompassesInInventory(ServerPlayerEntity player, BlockPos pos, RegistryKey<World> dim) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() != TRACKER_COMPASS) continue;

            NbtCompound nbt = stack.getOrCreateNbt();

            NbtCompound lodestonePos = new NbtCompound();
            lodestonePos.putInt("x", pos.getX());
            lodestonePos.putInt("y", pos.getY());
            lodestonePos.putInt("z", pos.getZ());
            nbt.put("LodestonePos", lodestonePos);

            nbt.putString("LodestoneDimension", dim.getValue().toString());
            nbt.putBoolean("LodestoneTracked", false);

            stack.setNbt(nbt);
        }
    }

    // RoleState 存储在世界存档里，实现多存档隔离
    public static class RoleState extends PersistentState {
        public UUID speedrunner = null;
        public Set<UUID> hunters = new HashSet<>();

        public RoleState() {
            super();
        }

        public static RoleState fromNbt(NbtCompound nbt) {
            RoleState rs = new RoleState();
            if (nbt.contains("speedrunner")) {
                try {
                    rs.speedrunner = UUID.fromString(nbt.getString("speedrunner"));
                } catch (Exception ignored) {}
            }
            if (nbt.contains("hunters")) {
                NbtList list = nbt.getList("hunters", 8); // 8 = TAG_String
                for (int i = 0; i < list.size(); i++) {
                    try {
                        rs.hunters.add(UUID.fromString(list.getString(i)));
                    } catch (Exception ignored) {}
                }
            }
            return rs;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            if (this.speedrunner != null) {
                nbt.putString("speedrunner", this.speedrunner.toString());
            }
            NbtList list = new NbtList();
            for (UUID u : this.hunters) {
                list.add(NbtString.of(u.toString()));
            }
            nbt.put("hunters", list);
            return nbt;
        }
    }
}
