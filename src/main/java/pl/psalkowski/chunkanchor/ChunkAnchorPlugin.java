package pl.psalkowski.chunkanchor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import pl.psalkowski.chunkanchor.command.ChunkAnchorCommand;
import pl.psalkowski.chunkanchor.listener.PlayerConnectionListener;
import pl.psalkowski.chunkanchor.manager.AnchorManager;
import pl.psalkowski.chunkanchor.manager.ChunkLoadManager;
import pl.psalkowski.chunkanchor.model.LoadMode;
import pl.psalkowski.chunkanchor.visualization.AnchorVisualizer;

public class ChunkAnchorPlugin extends JavaPlugin {

    private AnchorManager anchorManager;
    private ChunkLoadManager chunkLoadManager;
    private AnchorVisualizer anchorVisualizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int defaultLimit = getConfig().getInt("default-limit", 3);
        int chunkRadius = getConfig().getInt("chunk-radius", 1);
        int showDuration = getConfig().getInt("show-duration", 10);
        String loadModeStr = getConfig().getString("default-load-mode", "PLAYER_ONLINE");
        LoadMode defaultLoadMode;
        try {
            defaultLoadMode = LoadMode.valueOf(loadModeStr);
            if (defaultLoadMode == LoadMode.DEFAULT) {
                defaultLoadMode = LoadMode.PLAYER_ONLINE;
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid default-load-mode in config: " + loadModeStr + ", using PLAYER_ONLINE");
            defaultLoadMode = LoadMode.PLAYER_ONLINE;
        }

        anchorManager = new AnchorManager(this, getConfig());
        chunkLoadManager = new ChunkLoadManager(this, getConfig(), anchorManager, defaultLoadMode);
        anchorVisualizer = new AnchorVisualizer(this, getConfig(), anchorManager);

        ChunkAnchorCommand command = new ChunkAnchorCommand(anchorManager, chunkLoadManager, anchorVisualizer, defaultLoadMode);
        getCommand("chunkanchor").setExecutor(command);
        getCommand("chunkanchor").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(chunkLoadManager), this);

        chunkLoadManager.loadAlwaysAnchors();

        getLogger().info("ChunkAnchor enabled! Default limit: " + defaultLimit + ", Chunk radius: " + chunkRadius + ", Show duration: " + showDuration + "s, Default mode: " + defaultLoadMode);
    }

    @Override
    public void onDisable() {
        if (chunkLoadManager != null) {
            chunkLoadManager.unloadAllChunks();
        }
        getLogger().info("ChunkAnchor disabled!");
    }

    public AnchorManager getAnchorManager() {
        return anchorManager;
    }

    public ChunkLoadManager getChunkLoadManager() {
        return chunkLoadManager;
    }

    public AnchorVisualizer getAnchorVisualizer() {
        return anchorVisualizer;
    }
}
