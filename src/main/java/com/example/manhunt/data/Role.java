package com.example.manhunt.data;

public enum Role {
    NONE,
    SPEEDRUNNER,
    HUNTER;

    public boolean isSpeedrunner() { return this == SPEEDRUNNER; }
    public boolean isHunter() { return this == HUNTER; }
    public boolean isAssigned() { return this != NONE; }
}
