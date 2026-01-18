package it.mcexp.pvpsync.db;

import it.mcexp.pvpsync.model.PlayerStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class StatsRepository {

    private final Database db;

    public StatsRepository(Database db) {
        this.db = db;
    }

    /* =========================
     *  DATA MODELS
     * ========================= */
    public record LeaderEntry(String nickname, int kills) {}

    /* =========================
     *  BASIC FETCH
     * ========================= */
    public Optional<PlayerStats> findByNickname(String nickname) throws Exception {
        String sql = """
                SELECT nickname, uuid, kills, deaths, killstreak
                FROM pvpsync_stats
                WHERE nickname=?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nickname);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PlayerStats(
                        rs.getString("nickname"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("killstreak")
                ));
            }
        }
    }

    public Optional<PlayerStats> findByUuid(UUID uuid) throws Exception {
        String sql = """
                SELECT nickname, uuid, kills, deaths, killstreak
                FROM pvpsync_stats
                WHERE uuid=?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PlayerStats(
                        rs.getString("nickname"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("killstreak")
                ));
            }
        }
    }

    /* =========================
     *  IDENTITY SYNC
     * ========================= */
    public void upsertIdentityNicknameFirst(
            String nickname,
            UUID uuid,
            boolean updateUuidIfNicknameMatches
    ) throws Exception {

        String sql = """
            INSERT INTO pvpsync_stats (nickname, uuid, kills, deaths, killstreak)
            VALUES (?, ?, 0, 0, 0)
            ON DUPLICATE KEY UPDATE
              uuid = IF(?, VALUES(uuid), uuid)
            """;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nickname);
            ps.setString(2, uuid.toString());
            ps.setBoolean(3, updateUuidIfNicknameMatches);
            ps.executeUpdate();
        }
    }

    public void updateNicknameByUuid(UUID uuid, String newNickname) throws Exception {
        String sql = "UPDATE pvpsync_stats SET nickname=? WHERE uuid=?";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newNickname);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    /* =========================
     *  PVP UPDATE
     * ========================= */
    public void incrementKill(String nickname) throws Exception {
        String sql = """
                UPDATE pvpsync_stats
                SET kills = kills + 1,
                    killstreak = killstreak + 1
                WHERE nickname=?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nickname);
            ps.executeUpdate();
        }
    }

    public void incrementDeathAndResetStreak(String nickname) throws Exception {
        String sql = """
                UPDATE pvpsync_stats
                SET deaths = deaths + 1,
                    killstreak = 0
                WHERE nickname=?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nickname);
            ps.executeUpdate();
        }
    }

    /* =========================
     *  LEADERBOARD
     * ========================= */
    public List<LeaderEntry> topKills(int limit) throws Exception {
        String sql = """
                SELECT nickname, kills
                FROM pvpsync_stats
                ORDER BY kills DESC, nickname ASC
                LIMIT ?
                """;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<LeaderEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new LeaderEntry(
                            rs.getString("nickname"),
                            rs.getInt("kills")
                    ));
                }
                return list;
            }
        }
    }

    /**
     * Rank deterministico:
     * - kills DESC
     * - nickname ASC (tie-break)
     */
    public int rankByKills(String nickname) throws Exception {

        String getKills = "SELECT kills FROM pvpsync_stats WHERE nickname=?";
        int myKills;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(getKills)) {

            ps.setString(1, nickname);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                myKills = rs.getInt(1);
            }
        }

        String rankSql = """
            SELECT COUNT(*) AS ahead
            FROM pvpsync_stats
            WHERE kills > ?
               OR (kills = ? AND nickname < ?)
            """;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(rankSql)) {

            ps.setInt(1, myKills);
            ps.setInt(2, myKills);
            ps.setString(3, nickname);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("ahead") + 1;
            }
        }
    }
}