package it.mcexp.pvpsync;

import it.mcexp.pvpsync.db.Database;
import it.mcexp.pvpsync.db.StatsRepository;
import it.mcexp.pvpsync.listener.PvPListener;
import it.mcexp.pvpsync.papi.PvPSyncExpansion;
import it.mcexp.pvpsync.service.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPStatsSync extends JavaPlugin {

    private Database database;
    private StatsService statsService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.database = new Database(this);
        this.database.init(); // pool + create table

        StatsRepository repo = new StatsRepository(database);
        this.statsService = new StatsService(this, repo);

        Bukkit.getPluginManager().registerEvents(new PvPListener(statsService), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PvPSyncExpansion(this, statsService).register();
            getLogger().info("PlaceholderAPI found: expansion registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found: placeholders disabled.");
        }

        getLogger().info("PvPStatsSync enabled.");
    }

    @Override
    public void onDisable() {
        if (statsService != null) statsService.shutdown();
        if (database != null) database.shutdown();
        getLogger().info("PvPStatsSync disabled.");
    }

    public StatsService getStatsService() {
        return statsService;
    }
}