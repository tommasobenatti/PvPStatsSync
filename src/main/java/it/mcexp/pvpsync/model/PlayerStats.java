package it.mcexp.pvpsync.model;

import java.util.UUID;

public record PlayerStats(
        String nickname,
        UUID uuid,
        int kills,
        int deaths,
        int killstreak
) {
    public double kdr() {
        return deaths <= 0 ? (double) kills / (deaths + 1.0) : (double) kills / deaths;
    }
}