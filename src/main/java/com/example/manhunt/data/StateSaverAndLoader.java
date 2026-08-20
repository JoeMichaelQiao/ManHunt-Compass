package com.example.manhunt.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StateSaverAndLoader extends PersistentState {
    private static final String DATA_NAME = "manhunt_data";

    private final Map<UUID, PlayerRoleData> players = new HashMap<>();
    private final Map<UUID, PlayerRoleData> speedrunners = new HashMap<>();
    private final Map<UUID, PlayerRoleData> hunters = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList playersList = new NbtList();
        for (PlayerRoleData data : players.values()) {
            playersList.add(data.toNbt());
        }
        nbt.put("players", playersList);

        NbtList speedrunnerList = new NbtList();
        for (UUID uuid : speedrunners.keySet()) {
            speedrunnerList.add(uuid.toString());
        }
        nbt.put("speedrunners", speedrunnerList);

        NbtList hunterList = new NbtList();
        for (UUID uuid : hunters.keySet()) {
            hunterList.add(uuid.toString());
        }
        nbt.put("hunters", hunterList);

        return nbt;
    }

    public static StateSaverAndLoader createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        StateSaverAndLoader state = new StateSaverAndLoader();

        NbtList playersList = nbt.getList("players", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < playersList.size(); i++) {
            NbtCompound playerNbt = playersList.getCompound(i);
            PlayerRoleData data = PlayerRoleData.fromNbt(playerNbt);
            state.players.put(data.getPlayerUuid(), data);
            if (data.isSpeedrunner()) state.speedrunners.put(data.getPlayerUuid(), data);
            else if (data.isHunter()) state.hunters.put(data.getPlayerUuid(), data);
        }

        return state;
    }

    public static StateSaverAndLoader getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(StateSaverAndLoader::createFromNbt, StateSaverAndLoader::new, DATA_NAME);
    }

    public PlayerRoleData getPlayerData(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerRoleData::new);
    }

    public void setPlayerRole(UUID uuid, Role role) {
        PlayerRoleData data = getPlayerData(uuid);
        if (data.isSpeedrunner()) speedrunners.remove(uuid);
        if (data.isHunter()) hunters.remove(uuid);
        data.setRole(role);
        if (role == Role.SPEEDRUNNER) speedrunners.put(uuid, data);
        else if (role == Role.HUNTER) hunters.put(uuid, data);
        markDirty();
    }

    public Role getPlayerRole(UUID uuid) {
        return players.containsKey(uuid) ? players.get(uuid).getRole() : Role.NONE;
    }

    public Map<UUID, PlayerRoleData> getSpeedrunners() { return speedrunners; }
    public Map<UUID, PlayerRoleData> getHunters() { return hunters; }
    public boolean isSpeedrunner(UUID uuid) { return speedrunners.containsKey(uuid); }
    public boolean isHunter(UUID uuid) { return hunters.containsKey(uuid); }

    public void clearAllRoles() {
        players.clear();
        speedrunners.clear();
        hunters.clear();
        markDirty();
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        speedrunners.remove(uuid);
        hunters.remove(uuid);
        markDirty();
    }
}
