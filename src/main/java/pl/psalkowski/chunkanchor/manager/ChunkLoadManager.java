package pl.psalkowski.chunkanchor.manager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import pl.psalkowski.chunkanchor.model.LoadMode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChunkLoadManager {

    private final Plugin plugin;
    private final AnchorManager anchorManager;
    private final LoadMode defaultLoadMode;
    private final int chunkRadius;
    private boolean playerOnlineChunksLoaded;
    private final Set<String> loadedAnchorKeys = new HashSet<>();

    public ChunkLoadManager(Plugin plugin, FileConfiguration config, AnchorManager anchorManager, LoadMode defaultLoadMode) {
        this.plugin = plugin;
        this.anchorManager = anchorManager;
        this.defaultLoadMode = defaultLoadMode;
        this.chunkRadius = config.getInt("chunk-radius", 3);
        this.playerOnlineChunksLoaded = false;
    }

    public void loadAlwaysAnchors() {
        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int loadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            UUID playerId = playerEntry.getKey();
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                String anchorName = anchorEntry.getKey();
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (shouldLoadAlways(anchor) && loadChunksForAnchorInternal(playerId, anchorName, anchor)) {
                    loadedCount++;
                }
            }
        }

        if (loadedCount > 0) {
            plugin.getLogger().info("Loaded chunks for " + loadedCount + " ALWAYS-mode anchors");
        }
    }

    public void loadPlayerOnlineAnchors() {
        if (playerOnlineChunksLoaded) {
            return;
        }

        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int loadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            UUID playerId = playerEntry.getKey();
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                String anchorName = anchorEntry.getKey();
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (shouldLoadOnPlayerOnline(anchor) && loadChunksForAnchorInternal(playerId, anchorName, anchor)) {
                    loadedCount++;
                }
            }
        }

        playerOnlineChunksLoaded = true;
        if (loadedCount > 0) {
            plugin.getLogger().info("Loaded chunks for " + loadedCount + " PLAYER_ONLINE-mode anchors");
        }
    }

    public void unloadPlayerOnlineAnchors() {
        if (!playerOnlineChunksLoaded) {
            return;
        }

        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int unloadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            UUID playerId = playerEntry.getKey();
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                String anchorName = anchorEntry.getKey();
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (shouldLoadOnPlayerOnline(anchor) && unloadChunksForAnchorInternal(playerId, anchorName, anchor)) {
                    unloadedCount++;
                }
            }
        }

        playerOnlineChunksLoaded = false;
        if (unloadedCount > 0) {
            plugin.getLogger().info("Unloaded chunks for " + unloadedCount + " PLAYER_ONLINE-mode anchors");
        }
    }

    public void unloadAllChunks() {
        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int unloadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            UUID playerId = playerEntry.getKey();
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                String anchorName = anchorEntry.getKey();
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (unloadChunksForAnchorInternal(playerId, anchorName, anchor)) {
                    unloadedCount++;
                }
            }
        }

        playerOnlineChunksLoaded = false;
        loadedAnchorKeys.clear();
        plugin.getLogger().info("Unloaded chunks for " + unloadedCount + " anchors");
    }

    public void loadChunksForAnchor(UUID playerId, String anchorName) {
        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null || !anchor.enabled()) {
            return;
        }

        if (shouldLoadAlways(anchor)) {
            loadChunksForAnchorInternal(playerId, anchorName, anchor);
        } else if (shouldLoadOnPlayerOnline(anchor) && playerOnlineChunksLoaded) {
            loadChunksForAnchorInternal(playerId, anchorName, anchor);
        }
    }

    public void unloadChunksForAnchor(UUID playerId, String anchorName) {
        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null) {
            return;
        }

        unloadChunksForAnchorInternal(playerId, anchorName, anchor);
    }

    public void onAnchorEnabledChanged(UUID playerId, String anchorName, boolean enabled) {
        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null) {
            return;
        }

        if (enabled) {
            loadChunksForAnchor(playerId, anchorName);
        } else {
            unloadChunksForAnchor(playerId, anchorName);
        }
    }

    public void onAnchorModeChanged(UUID playerId, String anchorName, LoadMode oldMode, LoadMode newMode) {
        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null || !anchor.enabled()) {
            return;
        }

        boolean wasLoaded = isAnchorLoaded(playerId, anchorName);
        boolean shouldBeLoaded = shouldBeLoadedNow(anchor);

        if (wasLoaded && !shouldBeLoaded) {
            unloadChunksForAnchorInternal(playerId, anchorName, anchor);
        } else if (!wasLoaded && shouldBeLoaded) {
            loadChunksForAnchorInternal(playerId, anchorName, anchor);
        }
    }

    public boolean isAnchorLoaded(UUID playerId, String anchorName) {
        return loadedAnchorKeys.contains(getAnchorKey(playerId, anchorName));
    }

    public boolean isPlayerOnlineChunksLoaded() {
        return playerOnlineChunksLoaded;
    }

    private boolean shouldLoadAlways(AnchorManager.Anchor anchor) {
        if (!anchor.enabled()) {
            return false;
        }
        LoadMode effectiveMode = anchor.loadMode() == LoadMode.DEFAULT ? defaultLoadMode : anchor.loadMode();
        return effectiveMode == LoadMode.ALWAYS;
    }

    private boolean shouldLoadOnPlayerOnline(AnchorManager.Anchor anchor) {
        if (!anchor.enabled()) {
            return false;
        }
        LoadMode effectiveMode = anchor.loadMode() == LoadMode.DEFAULT ? defaultLoadMode : anchor.loadMode();
        return effectiveMode == LoadMode.PLAYER_ONLINE;
    }

    private boolean shouldBeLoadedNow(AnchorManager.Anchor anchor) {
        if (!anchor.enabled()) {
            return false;
        }
        if (shouldLoadAlways(anchor)) {
            return true;
        }
        return shouldLoadOnPlayerOnline(anchor) && playerOnlineChunksLoaded;
    }

    private String getAnchorKey(UUID playerId, String anchorName) {
        return playerId.toString() + ":" + anchorName;
    }

    private boolean loadChunksForAnchorInternal(UUID playerId, String anchorName, AnchorManager.Anchor anchor) {
        if (!anchor.enabled()) {
            return false;
        }

        String key = getAnchorKey(playerId, anchorName);
        if (loadedAnchorKeys.contains(key)) {
            return false;
        }

        World world = Bukkit.getWorld(anchor.world());
        if (world == null) {
            plugin.getLogger().warning("Cannot load chunks for anchor: world '" + anchor.world() + "' is not loaded");
            return false;
        }

        int cx = anchor.chunkX();
        int cz = anchor.chunkZ();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.addPluginChunkTicket(plugin);
            }
        }

        loadedAnchorKeys.add(key);
        return true;
    }

    private boolean unloadChunksForAnchorInternal(UUID playerId, String anchorName, AnchorManager.Anchor anchor) {
        String key = getAnchorKey(playerId, anchorName);
        if (!loadedAnchorKeys.contains(key)) {
            return false;
        }

        World world = Bukkit.getWorld(anchor.world());
        if (world == null) {
            loadedAnchorKeys.remove(key);
            return false;
        }

        int cx = anchor.chunkX();
        int cz = anchor.chunkZ();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                chunk.removePluginChunkTicket(plugin);
            }
        }

        loadedAnchorKeys.remove(key);
        return true;
    }
}
