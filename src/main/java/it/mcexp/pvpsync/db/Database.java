package it.mcexp.pvpsync.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.Statement;

public final class Database {

    private final JavaPlugin plugin;
    private HikariDataSource ds;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("database");
        if (c == null) throw new IllegalStateException("Missing 'database' section in config.yml");

        String host = c.getString("host");
        int port = c.getInt("port");
        String db = c.getString("name");
        String user = c.getString("user");
        String pass = c.getString("password");
        String params = c.getString("parameters", "");

        ConfigurationSection pool = c.getConfigurationSection("pool");
        int maxPool = pool != null ? pool.getInt("maximumPoolSize", 10) : 10;
        int minIdle = pool != null ? pool.getInt("minimumIdle", 2) : 2;
        long connTimeout = pool != null ? pool.getLong("connectionTimeoutMs", 10000) : 10000;

        String jdbc = "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params;

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbc);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(connTimeout);
        hc.setPoolName("PvPStatsSync");

        this.ds = new HikariDataSource(hc);

        createTables();
    }

    private void createTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS pvpsync_stats (
              nickname   VARCHAR(16)  NOT NULL,
              uuid       CHAR(36)     NOT NULL,
              kills      INT          NOT NULL DEFAULT 0,
              deaths     INT          NOT NULL DEFAULT 0,
              killstreak INT          NOT NULL DEFAULT 0,
              updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (nickname),
              UNIQUE KEY uq_uuid (uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("Failed creating tables", e);
        }
    }

    public Connection getConnection() throws Exception {
        return ds.getConnection();
    }

    public void shutdown() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}