package com.ocloud.taixiu.game;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TaiXiuStore implements AutoCloseable {
    private final JavaPlugin plugin;
    private Connection connection;

    TaiXiuStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    synchronized void open() throws SQLException {
        if (connection != null && !connection.isClosed()) return;
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new SQLException("Cannot create data directory " + dataDir);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("SQLite JDBC driver is missing", ex);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataDir, "taixiu.db").getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE IF NOT EXISTS tx_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS tx_rounds ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, opened_at INTEGER NOT NULL, closed_at INTEGER NOT NULL DEFAULT 0, "
                    + "dice_1 INTEGER NOT NULL DEFAULT 0, dice_2 INTEGER NOT NULL DEFAULT 0, dice_3 INTEGER NOT NULL DEFAULT 0, "
                    + "total INTEGER NOT NULL DEFAULT 0, result TEXT NOT NULL DEFAULT '', tai_total INTEGER NOT NULL DEFAULT 0, "
                    + "xiu_total INTEGER NOT NULL DEFAULT 0, player_count INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS tx_bets ("
                    + "round_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, side TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, payout INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0, "
                    + "status TEXT NOT NULL, created_at INTEGER NOT NULL, settled_at INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(round_id, player_uuid), FOREIGN KEY(round_id) REFERENCES tx_rounds(id))");
            statement.execute("CREATE TABLE IF NOT EXISTS tx_notifications ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, message TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS tx_bets_player_idx ON tx_bets(player_uuid, round_id DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS tx_rounds_status_idx ON tx_rounds(status, id DESC)");
        }
    }

    synchronized long createRound() throws SQLException {
        requireOpen();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO tx_rounds(opened_at,status) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, System.currentTimeMillis());
            insert.setString(2, "OPEN");
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Round id was not generated");
                return keys.getLong(1);
            }
        }
    }

    synchronized String metadata(String key) throws SQLException {
        requireOpen();
        try (PreparedStatement query = connection.prepareStatement("SELECT value FROM tx_meta WHERE key=?")) {
            query.setString(1, key);
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getString(1) : ""; }
        }
    }

    synchronized void setMetadata(String key, String value) throws SQLException {
        requireOpen();
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO tx_meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            upsert.setString(1, key);
            upsert.setString(2, value);
            upsert.executeUpdate();
        }
    }

    synchronized void addBet(long roundId, UUID playerId, String playerName, TaiXiuMath.Side side, long amount)
            throws SQLException {
        requireOpen();
        if (amount <= 0L) throw new SQLException("Bet amount must be positive");
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO tx_bets(round_id,player_uuid,player_name,side,amount,status,created_at) VALUES(?,?,?,?,?,'ACTIVE',?) "
                        + "ON CONFLICT(round_id,player_uuid) DO UPDATE SET amount=amount+excluded.amount, player_name=excluded.player_name")) {
            upsert.setLong(1, roundId);
            upsert.setString(2, playerId.toString());
            upsert.setString(3, playerName == null ? playerId.toString() : playerName);
            upsert.setString(4, side.name());
            upsert.setLong(5, amount);
            upsert.setLong(6, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }

    synchronized List<Bet> activeBets(long roundId) throws SQLException {
        requireOpen();
        List<Bet> bets = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT round_id,player_uuid,player_name,side,amount,payout,tax,status FROM tx_bets "
                        + "WHERE round_id=? AND status='ACTIVE' ORDER BY created_at")) {
            query.setLong(1, roundId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) bets.add(readBet(result));
            }
        }
        return bets;
    }

    synchronized List<Bet> allActiveBets() throws SQLException {
        requireOpen();
        List<Bet> bets = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT round_id,player_uuid,player_name,side,amount,payout,tax,status FROM tx_bets "
                        + "WHERE status='ACTIVE' ORDER BY round_id,created_at");
             ResultSet result = query.executeQuery()) {
            while (result.next()) bets.add(readBet(result));
        }
        return bets;
    }

    synchronized List<Bet> betsByStatus(String status) throws SQLException {
        requireOpen();
        List<Bet> bets = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT round_id,player_uuid,player_name,side,amount,payout,tax,status FROM tx_bets "
                        + "WHERE status=? ORDER BY round_id,created_at")) {
            query.setString(1, status);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) bets.add(readBet(result));
            }
        }
        return bets;
    }

    synchronized List<Bet> betsByStatus(UUID playerId, String status) throws SQLException {
        requireOpen();
        List<Bet> bets = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT round_id,player_uuid,player_name,side,amount,payout,tax,status FROM tx_bets "
                        + "WHERE player_uuid=? AND status=? ORDER BY round_id")) {
            query.setString(1, playerId.toString());
            query.setString(2, status);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) bets.add(readBet(result));
            }
        }
        return bets;
    }

    synchronized void markBet(long roundId, UUID playerId, String status, long payout, long tax) throws SQLException {
        requireOpen();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE tx_bets SET status=?,payout=?,tax=?,settled_at=? WHERE round_id=? AND player_uuid=?")) {
            update.setString(1, status);
            update.setLong(2, Math.max(0L, payout));
            update.setLong(3, Math.max(0L, tax));
            update.setLong(4, System.currentTimeMillis());
            update.setLong(5, roundId);
            update.setString(6, playerId.toString());
            update.executeUpdate();
        }
    }

    synchronized void finishRound(long roundId, int first, int second, int third, String result, long taiTotal,
                                  long xiuTotal, int playerCount, String status) throws SQLException {
        requireOpen();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE tx_rounds SET closed_at=?,dice_1=?,dice_2=?,dice_3=?,total=?,result=?,tai_total=?,xiu_total=?,"
                        + "player_count=?,status=? WHERE id=?")) {
            update.setLong(1, System.currentTimeMillis());
            update.setInt(2, first);
            update.setInt(3, second);
            update.setInt(4, third);
            update.setInt(5, first + second + third);
            update.setString(6, result == null ? "TRIPLE" : result);
            update.setLong(7, Math.max(0L, taiTotal));
            update.setLong(8, Math.max(0L, xiuTotal));
            update.setInt(9, Math.max(0, playerCount));
            update.setString(10, status);
            update.setLong(11, roundId);
            update.executeUpdate();
        }
    }

    synchronized void settleRound(long roundId, TaiXiuMath.DiceRoll roll, int taxPercent,
                                  long taiTotal, long xiuTotal) throws SQLException {
        requireOpen();
        connection.setAutoCommit(false);
        try {
            List<Bet> active = activeBets(roundId);
            for (Bet bet : active) {
                boolean won = roll.side() != null && bet.side() == roll.side();
                markBet(roundId, bet.playerId(), won ? "PAYOUT_PENDING" : "LOST",
                        won ? TaiXiuMath.payout(bet.amount(), taxPercent) : 0,
                        won ? TaiXiuMath.tax(bet.amount(), taxPercent) : 0);
            }
            finishRound(roundId, roll.first(), roll.second(), roll.third(),
                    roll.side() == null ? "TRIPLE" : roll.side().name(), taiTotal, xiuTotal, active.size(), "SETTLED");
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally { connection.setAutoCommit(true); }
    }

    synchronized void queueInterruptedRefunds() throws SQLException {
        requireOpen();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tx_bets SET status='REFUND_PENDING', payout=amount, tax=0 WHERE status='ACTIVE'");
            cancelInterruptedRounds();
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally { connection.setAutoCommit(true); }
    }

    synchronized void cancelInterruptedRounds() throws SQLException {
        requireOpen();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE tx_rounds SET closed_at=?,status='CANCELLED' WHERE status IN ('OPEN','ROLLING')")) {
            update.setLong(1, System.currentTimeMillis());
            update.executeUpdate();
        }
    }

    synchronized List<RoundHistory> history(int limit) throws SQLException {
        requireOpen();
        List<RoundHistory> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id,closed_at,dice_1,dice_2,dice_3,total,result,tai_total,xiu_total,player_count "
                        + "FROM tx_rounds WHERE status='SETTLED' ORDER BY id DESC LIMIT ?")) {
            query.setInt(1, Math.max(1, limit));
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    rows.add(new RoundHistory(result.getLong(1), result.getLong(2), result.getInt(3), result.getInt(4),
                            result.getInt(5), result.getInt(6), result.getString(7), result.getLong(8),
                            result.getLong(9), result.getInt(10)));
                }
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    synchronized PlayerStats playerStats(UUID playerId) throws SQLException {
        requireOpen();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*),COALESCE(SUM(amount),0),"
                        + "COALESCE(SUM(CASE WHEN status='WON' THEN 1 ELSE 0 END),0),"
                        + "COALESCE(SUM(CASE WHEN status='WON' THEN payout ELSE 0 END),0) "
                        + "FROM tx_bets WHERE player_uuid=? AND status IN ('WON','LOST')")) {
            query.setString(1, playerId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) return new PlayerStats(result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4));
            }
        }
        return new PlayerStats(0L, 0L, 0L, 0L);
    }

    synchronized void addNotification(UUID playerId, String message) throws SQLException {
        requireOpen();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO tx_notifications(player_uuid,message,created_at) VALUES(?,?,?)")) {
            insert.setString(1, playerId.toString());
            insert.setString(2, message);
            insert.setLong(3, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    synchronized List<String> drainNotifications(UUID playerId) throws SQLException {
        requireOpen();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<String> messages = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id,message FROM tx_notifications WHERE player_uuid=? ORDER BY id")) {
                query.setString(1, playerId.toString());
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) messages.add(result.getString(2));
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM tx_notifications WHERE player_uuid=?")) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            connection.commit();
            return messages;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private Bet readBet(ResultSet result) throws SQLException {
        return new Bet(result.getLong(1), UUID.fromString(result.getString(2)), result.getString(3),
                TaiXiuMath.Side.valueOf(result.getString(4)), result.getLong(5), result.getLong(6),
                result.getLong(7), result.getString(8));
    }

    private void requireOpen() throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("Tài Xỉu database is closed");
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Không thể đóng taixiu.db: " + ex.getMessage());
        } finally {
            connection = null;
        }
    }

    record Bet(long roundId, UUID playerId, String playerName, TaiXiuMath.Side side, long amount,
               long payout, long tax, String status) {
    }

    record RoundHistory(long id, long closedAt, int first, int second, int third, int total, String result,
                        long taiTotal, long xiuTotal, int playerCount) {
        boolean triple() {
            return "TRIPLE".equalsIgnoreCase(result);
        }

        TaiXiuMath.Side side() {
            if (triple()) return null;
            try {
                return TaiXiuMath.Side.valueOf(result);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    record PlayerStats(long rounds, long totalBet, long wins, long totalPayout) {
    }
}
