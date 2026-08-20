package com.example.manhunt;

import com.example.manhunt.command.ManhuntCommands;
import com.example.manhunt.event.PlayerEventHandler;
import com.example.manhunt.item.ModItems;
import com.example.manhunt.item.HunterCompassItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.example.manhunt.data.StateSaverAndLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManhuntMod implements ModInitializer {
    public static final String MOD_ID = "manhunt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Manhunt Mod!");

        ModItems.register();
        ManhuntCommands.register();
        PlayerEventHandler.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StateSaverAndLoader.getServerState(server);
            LOGGER.info("Manhunt data loaded!");
        });

        // 每 tick 更新猎人的指南针指向
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            HunterCompassItem.updateAllCompasses(server);
        });
    }
}
