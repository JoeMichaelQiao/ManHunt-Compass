package com.example.manhunt.event;

import com.example.manhunt.data.StateSaverAndLoader;
import com.example.manhunt.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerEventHandler {

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            giveCompassIfHunter(newPlayer);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            StateSaverAndLoader state = StateSaverAndLoader.getServerState(server);
            if (state.isHunter(player.getUuid())) {
                giveCompassIfHunter(player);
            }
        });
    }

    private static void giveCompassIfHunter(ServerPlayerEntity player) {
        StateSaverAndLoader state = StateSaverAndLoader.getServerState(player.getServer());
        if (!state.isHunter(player.getUuid())) return;

        boolean hasCompass = false;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() == ModItems.HUNTER_COMPASS) {
                hasCompass = true;
                break;
            }
        }
        if (!hasCompass) {
            player.getInventory().offerOrDrop(new ItemStack(ModItems.HUNTER_COMPASS));
            player.sendMessage(net.minecraft.text.Text.literal("你获得了猎人指南针！").formatted(net.minecraft.util.Formatting.GOLD), false);
        }
    }
}
