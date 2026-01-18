package it.mcexp.pvpsync.listener;

import it.mcexp.pvpsync.service.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PvPListener implements Listener {

    private final StatsService stats;

    public PvPListener(StatsService stats) {
        this.stats = stats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        stats.ensureIdentity(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // ensure identities exist
        stats.ensureIdentity(victim);
        if (killer != null) stats.ensureIdentity(killer);

        String victimNick = victim.getName();
        String killerNick = killer != null ? killer.getName() : null;

        stats.recordKillAndDeath(killerNick, victimNick);
    }
}