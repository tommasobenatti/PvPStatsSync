package it.mcexp.pvpsync.service;

import it.mcexp.pvpsync.db.StatsRepository;
import it.mcexp.pvpsync.db.StatsRepository.LeaderEntry;
import it.mcexp.pvpsync.model.PlayerStats;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

public final class StatsService {

    private final JavaPlugin plugin;
    private final StatsRepository repo;
    private final ExecutorService dbExec;

    /* =========================
     *  CACHE
     * ========================= */
    private final Map<String, CacheEntry> cacheByNick = new ConcurrentHashMap<>();
    private volatile LeaderCache topKillsCache = new LeaderCache(List.of(), 0L);
    private final long expireMillis;

    private final boolean updateUuidIfNicknameMatches;
    private final boolean updateNicknameIfUuidMatches;

    public StatsService(JavaPlugin plugin, StatsRepository repo) {
        this.plugin = plugin;
        this.repo = repo;

        this.dbExec = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "PvPStatsSync-DB");
            t.setDaemon(true);
            return t;
        });

        ConfigurationSection cache = plugin.getConfig().getConfigurationSection("cache");
        long expSec = cache != null ? cache.getLong("expireSeconds", 60) : 60;
        this.expireMillis = Math.max(5, expSec) * 1000L;

        ConfigurationSection sync = plugin.getConfig().getConfigurationSection("sync");
        this.updateUuidIfNicknameMatches = sync == null || sync.getBoolean("updateUuidIfNicknameMatches", true);
        this.updateNicknameIfUuidMatches = sync == null || sync.getBoolean("updateNicknameIfUuidMatches", true);
    }

    public void shutdown() {
        dbExec.shutdownNow();
    }

    /* =========================
     *  IDENTITY
     * ========================= */
    public void ensureIdentity(OfflinePlayer p) {
        if (p == null || p.getName() == null) return;

        String nick = p.getName();
        UUID uuid = p.getUniqueId();

        CompletableFuture.runAsync(() -> {
            try {
                repo.upsertIdentityNicknameFirst(nick, uuid, updateUuidIfNicknameMatches);

                if (updateNicknameIfUuidMatches) {
                    repo.findByUuid(uuid).ifPresent(ps -> {
                        if (!ps.nickname().equals(nick)) {
                            try {
                                repo.updateNicknameByUuid(uuid, nick);
                            } catch (Exception ignored) {}
                        }
                    });
                }

                cacheByNick.remove(nick);
            } catch (Exception e) {
                plugin.getLogger().severe("ensureIdentity failed: " + e.getMessage());
            }
        }, dbExec);
    }

    /* =========================
     *  PVP EVENTS
     * ========================= */
    public void recordKillAndDeath(String killerNick, String victimNick) {
        if (killerNick != null) cacheByNick.remove(killerNick);
        if (victimNick != null) cacheByNick.remove(victimNick);
        topKillsCache = new LeaderCache(List.of(), 0L);

        CompletableFuture.runAsync(() -> {
            try {
                if (victimNick != null) repo.incrementDeathAndResetStreak(victimNick);
                if (killerNick != null) repo.incrementKill(killerNick);
            } catch (Exception e) {
                plugin.getLogger().severe("recordKillAndDeath failed: " + e.getMessage());
            }
        }, dbExec);
    }

    /* =========================
     *  STATS FETCH
     * ========================= */
    public CompletableFuture<Optional<PlayerStats>> getStatsByNick(String nickname) {
        if (nickname == null || nickname.isBlank())
            return CompletableFuture.completedFuture(Optional.empty());

        CacheEntry cached = cacheByNick.get(nickname);
        long now = System.currentTimeMillis();

        if (cached != null && (now - cached.cachedAt) <= expireMillis) {
            return CompletableFuture.completedFuture(Optional.of(cached.stats));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<PlayerStats> ps = repo.findByNickname(nickname);
                ps.ifPresent(s -> cacheByNick.put(nickname, new CacheEntry(s, System.currentTimeMillis())));
                return ps;
            } catch (Exception e) {
                plugin.getLogger().severe("getStatsByNick failed: " + e.getMessage());
                return Optional.empty();
            }
        }, dbExec);
    }

    /* =========================
     *  LEADERBOARD
     * ========================= */
    public CompletableFuture<List<LeaderEntry>> getTopKills(int limit) {
        long now = System.currentTimeMillis();
        LeaderCache c = topKillsCache;

        if (!c.entries.isEmpty()
                && (now - c.cachedAt) <= expireMillis
                && c.entries.size() >= limit) {
            return CompletableFuture.completedFuture(c.entries);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<LeaderEntry> list = repo.topKills(Math.max(limit, 10));
                topKillsCache = new LeaderCache(list, System.currentTimeMillis());
                return list;
            } catch (Exception e) {
                plugin.getLogger().severe("getTopKills failed: " + e.getMessage());
                return List.of();
            }
        }, dbExec);
    }

    public CompletableFuture<Integer> getPersonalRankByKills(String nickname) {
        if (nickname == null || nickname.isBlank())
            return CompletableFuture.completedFuture(0);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return repo.rankByKills(nickname);
            } catch (Exception e) {
                plugin.getLogger().severe("rankByKills failed: " + e.getMessage());
                return 0;
            }
        }, dbExec);
    }

    /* =========================
     *  CACHE RECORDS
     * ========================= */
    private record CacheEntry(PlayerStats stats, long cachedAt) {}
    private record LeaderCache(List<LeaderEntry> entries, long cachedAt) {}
}