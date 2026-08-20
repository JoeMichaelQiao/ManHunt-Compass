package com.example.manhunt.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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

    // 使用新的 Type API
    public static final PersistentState.Type<StateSaverAndLoader> TYPE =
            new PersistentState.Type<>(
                    StateSaverAndLoader::new,                  // 无参构造
                    StateSaverAndLoader::fromNbt,              // 反序列化
                    null                                       // DataFixer（无需）
            );

    // 无参构造（用于新创建）
    public StateSaverAndLoader() {}

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList playersList = new NbtList();
        for (PlayerRoleData data : players.values()) {
            playersList.add(data.toNbt());
        }
        nbt.put("players", playersList);

        NbtList speedrunnerList = new NbtList();
        for (UUID uuid : speedrunners.keySet()) {
            speedrunnerList.add(NbtString.of(uuid.toString()));
        }
        nbt.put("speedrunners", speedrunnerList);

        NbtList hunterList = new NbtList();
        for (UUID uuid : hunters.keySet()) {
            hunterList.add(NbtString.of(uuid.toString()));
        }
        nbt.put("hunters", hunterList);

        return nbt;
    }

    // 反序列化（必须与 writeNbt 匹配）
    public static StateSaverAndLoader fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        StateSaverAndLoader state = new StateSaverAndLoader();

        NbtList playersList = nbt.getList("players", NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < playersList.size(); i++) {
            NbtCompound playerNbt = playersList.getCompound(i);
            PlayerRoleData data = PlayerRoleData.fromNbt(playerNbt);
            state.players.put(data.getPlayerUuid(), data);
            if (data.isSpeedrunner()) state.speedrunners.put(data.getPlayerUuid(), data);
            else if (data.isHunter()) state.hunters.put(data.getPlayerUuid(), data);
        }

        // 兼容旧格式（可选），但我们已经从 players 重建，无需再读 speedrunners/hunters 列表
        return state;
    }

    public static StateSaverAndLoader getServerState(MinecraftServer server) {
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(TYPE, DATA_NAME);
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