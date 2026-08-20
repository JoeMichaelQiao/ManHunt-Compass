package com.example.manhunt.item;

import com.example.manhunt.ManhuntMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item HUNTER_COMPASS = new HunterCompassItem(new Item.Settings().maxCount(1));

    public static void register() {
        Registry.register(Registries.ITEM, Identifier.of(ManhuntMod.MOD_ID, "hunter_compass"), HUNTER_COMPASS);
    }
}
