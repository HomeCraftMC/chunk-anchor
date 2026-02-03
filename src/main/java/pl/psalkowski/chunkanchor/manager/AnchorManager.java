package pl.psalkowski.chunkanchor.manager;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import pl.psalkowski.chunkanchor.model.LoadMode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnchorManager {

    public record Anchor(String world, int x, int z, LoadMode loadMode, boolean enabled) {
        public Anchor(String world, int x, int z) {
            this(world, x, z, LoadMode.DEFAULT, true);
        }

        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }

        public Anchor withLoadMode(LoadMode newMode) {
            return new Anchor(world, x, z, newMode, enabled);
        }

        public Anchor withEnabled(boolean newEnabled) {
            return new Anchor(world, x, z, loadMode, newEnabled);
        }
    }

    private final Plugin plugin;
    private final File dataFile;
    private final int limit;
    private final Map<UUID, Map<String, Anchor>> playerAnchors = new ConcurrentHashMap<>();

    public AnchorManager(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "anchors.yml");
        this.limit = config.getInt("default-limit", 3);
        load();
    }

    public boolean addAnchor(UUID playerId, String name, World world, int x, int z) {
        Map<String, Anchor> anchors = playerAnchors.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        if (getEnabledAnchorCount(playerId) >= limit) {
            return false;
        }

        if (anchors.containsKey(name)) {
            return false;
        }

        anchors.put(name, new Anchor(world.getName(), x, z));
        save();
        return true;
    }

    public boolean removeAnchor(UUID playerId, String name) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null || !anchors.containsKey(name)) {
            return false;
        }

        anchors.remove(name);
        if (anchors.isEmpty()) {
            playerAnchors.remove(playerId);
        }
        save();
        return true;
    }

    public Anchor getAnchor(UUID playerId, String name) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null) {
            return null;
        }
        return anchors.get(name);
    }

    public Map<String, Anchor> getPlayerAnchors(UUID playerId) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(anchors));
    }

    public Map<UUID, Map<String, Anchor>> getAllAnchors() {
        Map<UUID, Map<String, Anchor>> result = new HashMap<>();
        playerAnchors.forEach((uuid, anchors) -> result.put(uuid, new HashMap<>(anchors)));
        return result;
    }

    public Set<String> getAnchorNames(UUID playerId) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(anchors.keySet());
    }

    public int getAnchorCount(UUID playerId) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        return anchors == null ? 0 : anchors.size();
    }

    public int getEnabledAnchorCount(UUID playerId) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null) {
            return 0;
        }
        return (int) anchors.values().stream().filter(Anchor::enabled).count();
    }

    public int getLimit() {
        return limit;
    }

    public boolean setAnchorLoadMode(UUID playerId, String name, LoadMode loadMode) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null || !anchors.containsKey(name)) {
            return false;
        }
        Anchor oldAnchor = anchors.get(name);
        anchors.put(name, oldAnchor.withLoadMode(loadMode));
        save();
        return true;
    }

    public boolean setAnchorEnabled(UUID playerId, String name, boolean enabled) {
        Map<String, Anchor> anchors = playerAnchors.get(playerId);
        if (anchors == null || !anchors.containsKey(name)) {
            return false;
        }
        Anchor oldAnchor = anchors.get(name);
        if (oldAnchor.enabled() == enabled) {
            return true;
        }
        if (enabled && getEnabledAnchorCount(playerId) >= limit) {
            return false;
        }
        anchors.put(name, oldAnchor.withEnabled(enabled));
        save();
        return true;
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }

        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = data.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                Map<String, Anchor> anchors = new ConcurrentHashMap<>();
                for (String anchorName : playerSection.getKeys(false)) {
                    ConfigurationSection anchorSection = playerSection.getConfigurationSection(anchorName);
                    if (anchorSection == null) continue;

                    String world = anchorSection.getString("world");
                    int x = anchorSection.getInt("x");
                    int z = anchorSection.getInt("z");
                    String loadModeStr = anchorSection.getString("load-mode", "DEFAULT");
                    LoadMode loadMode;
                    try {
                        loadMode = LoadMode.valueOf(loadModeStr);
                    } catch (IllegalArgumentException e) {
                        loadMode = LoadMode.DEFAULT;
                    }
                    boolean enabled = anchorSection.getBoolean("enabled", true);

                    if (world != null) {
                        anchors.put(anchorName, new Anchor(world, x, z, loadMode, enabled));
                    }
                }

                if (!anchors.isEmpty()) {
                    playerAnchors.put(uuid, anchors);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid player UUID in anchors.yml: " + uuidStr);
            }
        }

        int totalAnchors = playerAnchors.values().stream().mapToInt(Map::size).sum();
        plugin.getLogger().info("Loaded " + totalAnchors + " anchors for " + playerAnchors.size() + " players");
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();

        playerAnchors.forEach((uuid, anchors) -> {
            String basePath = "players." + uuid.toString();
            anchors.forEach((name, anchor) -> {
                String anchorPath = basePath + "." + name;
                data.set(anchorPath + ".world", anchor.world());
                data.set(anchorPath + ".x", anchor.x());
                data.set(anchorPath + ".z", anchor.z());
                data.set(anchorPath + ".load-mode", anchor.loadMode().name());
                data.set(anchorPath + ".enabled", anchor.enabled());
            });
        });

        try {
            plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save anchors: " + e.getMessage());
        }
    }
}
