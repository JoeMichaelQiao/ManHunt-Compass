package com.example.manhunt.data;

import net.minecraft.nbt.NbtCompound;
import java.util.UUID;

public class PlayerRoleData {
    private final UUID playerUuid;
    private Role role = Role.NONE;

    public PlayerRoleData(UUID uuid) {
        this.playerUuid = uuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isSpeedrunner() { return role == Role.SPEEDRUNNER; }
    public boolean isHunter() { return role == Role.HUNTER; }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("uuid", playerUuid);
        nbt.putString("role", role.name());
        return nbt;
    }

    public static PlayerRoleData fromNbt(NbtCompound nbt) {
        UUID uuid = nbt.getUuid("uuid");
        PlayerRoleData data = new PlayerRoleData(uuid);
        data.role = Role.valueOf(nbt.getString("role"));
        return data;
    }
}
