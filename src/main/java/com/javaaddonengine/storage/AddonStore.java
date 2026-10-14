package com.javaaddonengine.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite runtime metadata store (MySQL optional via JDBC URL override).
 * Stores add-on states, compatibility scores and NPC definitions.
 */
public final class AddonStore implements AutoCloseable {

    private final Connection connection;

    public AddonStore(File dataFolder, String jdbcOverride) throws Exception {
        String url = (jdbcOverride != null && !jdbcOverride.isEmpty())
                ? jdbcOverride : "jdbc:sqlite:" + new File(dataFolder, "javaaddonengine.db").getAbsolutePath();
        this.connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS addons (id TEXT PRIMARY KEY, name TEXT, version TEXT, enabled INTEGER, score REAL, state TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS npcs (id TEXT PRIMARY KEY, addon TEXT, name TEXT, data TEXT)");
        }
    }

    public synchronized void upsertAddon(String id, String name, String version, boolean enabled, double score, String state) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO addons (id, name, version, enabled, score, state) VALUES (?,?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, version=excluded.version, enabled=excluded.enabled, score=excluded.score, state=excluded.state")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, version);
            ps.setInt(4, enabled ? 1 : 0);
            ps.setDouble(5, score);
            ps.setString(6, state);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public synchronized List<String[]> listAddons() {
        List<String[]> out = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, version, enabled, score FROM addons")) {
            while (rs.next()) {
                out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3),
                        String.valueOf(rs.getInt(4)), String.valueOf(rs.getDouble(5))});
            }
        } catch (Exception ignored) {}
        return out;
    }

    public synchronized void deleteAddon(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM addons WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public synchronized void saveNpc(String id, String addon, String name, String data) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO npcs (id, addon, name, data) VALUES (?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET addon=excluded.addon, name=excluded.name, data=excluded.data")) {
            ps.setString(1, id);
            ps.setString(2, addon);
            ps.setString(3, name);
            ps.setString(4, data);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public synchronized List<String[]> listNpcs(String addon) {
        List<String[]> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, name, data FROM npcs WHERE addon=?")) {
            ps.setString(1, addon);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
            }
        } catch (Exception ignored) {}
        return out;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
