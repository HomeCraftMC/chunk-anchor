package pl.psalkowski.chunkanchor.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.psalkowski.chunkanchor.manager.ChunkLoadManager;

public class PlayerConnectionListener implements Listener {

    private final ChunkLoadManager chunkLoadManager;

    public PlayerConnectionListener(ChunkLoadManager chunkLoadManager) {
        this.chunkLoadManager = chunkLoadManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (Bukkit.getOnlinePlayers().size() == 1 && !chunkLoadManager.isActive()) {
            chunkLoadManager.loadAllChunks();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (Bukkit.getOnlinePlayers().size() == 1 && chunkLoadManager.isActive()) {
            chunkLoadManager.unloadAllChunks();
        }
    }
}
