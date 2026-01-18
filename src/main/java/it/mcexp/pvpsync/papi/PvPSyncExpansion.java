package it.mcexp.pvpsync.papi;

import it.mcexp.pvpsync.PvPStatsSync;
import it.mcexp.pvpsync.model.PlayerStats;
import it.mcexp.pvpsync.service.StatsService;
import it.mcexp.pvpsync.db.StatsRepository.LeaderEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class PvPSyncExpansion extends PlaceholderExpansion {

    private final PvPStatsSync plugin;
    private final StatsService stats;
    private final DecimalFormat kdrFormat = new DecimalFormat("0.00");

    public PvPSyncExpansion(PvPStatsSync plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public String getIdentifier() {
        return "pvpsync";
    }

    @Override
    public String getAuthor() {
        return "McExp";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null || player.getName() == null) return "0";

        String nick = player.getName();
        String id = identifier.toLowerCase();

        /* =========================
         *  BASE STATS PLACEHOLDERS
         * ========================= */
        try {
            Optional<PlayerStats> opt = stats
                    .getStatsByNick(nick)
                    .get(150, TimeUnit.MILLISECONDS);

            if (opt.isPresent()) {
                PlayerStats ps = opt.get();

                switch (id) {
                    case "kills":
                        return String.valueOf(ps.kills());
                    case "deaths":
                        return String.valueOf(ps.deaths());
                    case "killstreak":
                        return String.valueOf(ps.killstreak());
                    case "kdr":
                        return kdrFormat.format(ps.kdr());
                }
            }
        } catch (Exception ignored) {
            return "0";
        }

        /* =========================
         *  PERSONAL LEADERBOARD
         * ========================= */
        if (id.equals("topkills_personal_rank")) {
            try {
                return String.valueOf(
                        stats.getPersonalRankByKills(nick)
                                .get(200, TimeUnit.MILLISECONDS)
                );
            } catch (Exception ignored) {
                return "0";
            }
        }

        if (id.equals("topkills_personal_kills")) {
            try {
                Optional<PlayerStats> opt = stats
                        .getStatsByNick(nick)
                        .get(150, TimeUnit.MILLISECONDS);

                return opt.map(ps -> String.valueOf(ps.kills())).orElse("0");
            } catch (Exception ignored) {
                return "0";
            }
        }

        /* =========================
         *  TOP KILLS LEADERBOARD
         *  topkills_<pos>_name
         *  topkills_<pos>_kills
         * ========================= */
        if (id.startsWith("topkills_")) {
            String[] parts = id.split("_");
            if (parts.length != 3) return null;

            int pos;
            try {
                pos = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                return "0";
            }

            if (pos <= 0) return "0";

            String field = parts[2]; // name | kills

            try {
                List<LeaderEntry> list = stats
                        .getTopKills(Math.max(pos, 10))
                        .get(250, TimeUnit.MILLISECONDS);

                if (list.size() < pos) {
                    return field.equals("name") ? "" : "0";
                }

                LeaderEntry entry = list.get(pos - 1);

                if (field.equals("name")) {
                    return entry.nickname();
                }

                if (field.equals("kills")) {
                    return String.valueOf(entry.kills());
                }

            } catch (Exception ignored) {
                return "0";
            }
        }

        return null;
    }
}