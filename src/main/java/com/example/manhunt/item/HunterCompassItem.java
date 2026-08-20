package com.example.manhunt.item;

import com.example.manhunt.data.StateSaverAndLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CompassItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HunterCompassItem extends CompassItem {

    public HunterCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return ActionResult.SUCCESS;

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(player.getServer());

        if (!state.isHunter(player.getUuid())) {
            player.sendMessage(Text.literal("只有猎人可以使用这个指南针！").formatted(Formatting.RED));
            return ActionResult.FAIL;
        }

        ItemStack stack = player.getStackInHand(hand);
        UUID targetUuid = getTargetUuid(stack);

        // 如果未设置目标或目标已离线，选择第一个速通者
        if (targetUuid == null || player.getServer().getPlayerManager().getPlayer(targetUuid) == null) {
            if (!state.getSpeedrunners().isEmpty()) {
                targetUuid = state.getSpeedrunners().keySet().iterator().next();
                setTargetUuid(stack, targetUuid);
            } else {
                player.sendMessage(Text.literal("没有速通者！").formatted(Formatting.RED));
                return ActionResult.FAIL;
            }
        }

        // 切换目标（如果多个速通者）
        List<UUID> speedrunnerList = List.copyOf(state.getSpeedrunners().keySet());
        if (speedrunnerList.size() > 1) {
            int currentIndex = speedrunnerList.indexOf(targetUuid);
            int nextIndex = (currentIndex + 1) % speedrunnerList.size();
            targetUuid = speedrunnerList.get(nextIndex);
            setTargetUuid(stack, targetUuid);
            ServerPlayerEntity targetPlayer = player.getServer().getPlayerManager().getPlayer(targetUuid);
            if (targetPlayer != null) {
                player.sendMessage(Text.literal("正在追踪: " + targetPlayer.getName().getString()).formatted(Formatting.GREEN), true);
            }
        } else {
            ServerPlayerEntity targetPlayer = player.getServer().getPlayerManager().getPlayer(targetUuid);
            player.sendMessage(Text.literal("追踪目标: " + (targetPlayer != null ? targetPlayer.getName().getString() : "离线"))
                .formatted(Formatting.GREEN), true);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.literal("右键切换追踪的速通者").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("只有猎人可以使用").formatted(Formatting.DARK_RED));
    }

    // ---------- NBT 读写 ----------
    private static final String TARGET_UUID_KEY = "TargetUuid";

    private UUID getTargetUuid(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.containsUuid(TARGET_UUID_KEY)) {
            return nbt.getUuid(TARGET_UUID_KEY);
        }
        return null;
    }

    private void setTargetUuid(ItemStack stack, UUID uuid) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putUuid(TARGET_UUID_KEY, uuid);
    }

    // ---------- 静态更新方法 ----------
    public static void updateAllCompasses(MinecraftServer server) {
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(server);
        if (state.getSpeedrunners().isEmpty()) return;

        for (ServerPlayerEntity hunter : server.getPlayerManager().getPlayerList()) {
            if (!state.isHunter(hunter.getUuid())) continue;

            for (ItemStack stack : hunter.getInventory().main) {
                if (stack.getItem() instanceof HunterCompassItem) {
                    UUID targetUuid = ((HunterCompassItem) stack.getItem()).getTargetUuid(stack);
                    if (targetUuid == null) {
                        if (!state.getSpeedrunners().isEmpty()) {
                            targetUuid = state.getSpeedrunners().keySet().iterator().next();
                            ((HunterCompassItem) stack.getItem()).setTargetUuid(stack, targetUuid);
                        } else continue;
                    }
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
                    if (target == null) continue;

                    // 设置指南针指向目标位置
                    var pos = target.getBlockPos();
                    // LodestoneTrackerComponent 需要 Optional<GlobalPos>
                    var lodestone = new LodestoneTrackerComponent(
                        Optional.of(GlobalPos.create(target.getWorld().getRegistryKey(), pos)),
                        false
                    );
                    stack.set(DataComponentTypes.LODESTONE_TRACKER, lodestone);
                }
            }
        }
    }
}