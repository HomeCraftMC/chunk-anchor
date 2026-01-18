package pl.psalkowski.chunkanchor.manager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;

public class ChunkLoadManager {

    private final Plugin plugin;
    private final AnchorManager anchorManager;
    private final int chunkRadius;
    private boolean chunksLoaded;

    public ChunkLoadManager(Plugin plugin, FileConfiguration config, AnchorManager anchorManager) {
        this.plugin = plugin;
        this.anchorManager = anchorManager;
        this.chunkRadius = config.getInt("chunk-radius", 3);
        this.chunksLoaded = false;
    }

    public void loadAllChunks() {
        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int loadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (loadChunksForAnchorInternal(anchor)) {
                    loadedCount++;
                }
            }
        }

        chunksLoaded = true;
        plugin.getLogger().info("Loaded chunks for " + loadedCount + " anchors (radius: " + chunkRadius + ")");
    }

    public void unloadAllChunks() {
        Map<UUID, Map<String, AnchorManager.Anchor>> allAnchors = anchorManager.getAllAnchors();
        int unloadedCount = 0;

        for (Map.Entry<UUID, Map<String, AnchorManager.Anchor>> playerEntry : allAnchors.entrySet()) {
            for (Map.Entry<String, AnchorManager.Anchor> anchorEntry : playerEntry.getValue().entrySet()) {
                AnchorManager.Anchor anchor = anchorEntry.getValue();
                if (unloadChunksForAnchorInternal(anchor)) {
                    unloadedCount++;
                }
            }
        }

        chunksLoaded = false;
        plugin.getLogger().info("Unloaded chunks for " + unloadedCount + " anchors");
    }

    public void loadChunksForAnchor(UUID playerId, String anchorName) {
        if (!chunksLoaded) {
            return;
        }

        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null) {
            return;
        }

        loadChunksForAnchorInternal(anchor);
    }

    public void unloadChunksForAnchor(UUID playerId, String anchorName) {
        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null) {
            return;
        }

        unloadChunksForAnchorInternal(anchor);
    }

    public boolean isActive() {
        return chunksLoaded;
    }

    private boolean loadChunksForAnchorInternal(AnchorManager.Anchor anchor) {
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

        return true;
    }

    private boolean unloadChunksForAnchorInternal(AnchorManager.Anchor anchor) {
        World world = Bukkit.getWorld(anchor.world());
        if (world == null) {
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

        return true;
    }
}
