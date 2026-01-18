package pl.psalkowski.chunkanchor.visualization;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import pl.psalkowski.chunkanchor.manager.AnchorManager;
import pl.psalkowski.chunkanchor.manager.AnchorManager.Anchor;

import java.util.UUID;

public class AnchorVisualizer {

    private final Plugin plugin;
    private final AnchorManager anchorManager;
    private final int chunkRadius;
    private final int showDuration;

    public AnchorVisualizer(Plugin plugin, FileConfiguration config, AnchorManager anchorManager) {
        this.plugin = plugin;
        this.anchorManager = anchorManager;
        this.chunkRadius = config.getInt("chunk-radius", 3);
        this.showDuration = config.getInt("show-duration", 30);
    }

    public void showAnchor(Player player, UUID playerId, String anchorName) {
        Anchor anchor = anchorManager.getAnchor(playerId, anchorName);
        if (anchor == null) {
            return;
        }

        World world = Bukkit.getWorld(anchor.world());
        if (world == null) {
            return;
        }

        int cX = anchor.chunkX();
        int cZ = anchor.chunkZ();

        int minX = (cX - chunkRadius) * 16;
        int maxX = (cX + chunkRadius + 1) * 16;
        int minZ = (cZ - chunkRadius) * 16;
        int maxZ = (cZ + chunkRadius + 1) * 16;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            spawnCornerPillars(player, world, minX, maxX, minZ, maxZ);
            spawnGroundOutline(player, world, minX, maxX, minZ, maxZ);
        }, 0L, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, task::cancel, showDuration * 20L);
    }

    private void spawnCornerPillars(Player player, World world, int minX, int maxX, int minZ, int maxZ) {
        int[][] corners = {
            {minX, minZ},
            {maxX, minZ},
            {minX, maxZ},
            {maxX, maxZ}
        };

        for (int[] corner : corners) {
            int x = corner[0];
            int z = corner[1];

            if (!isChunkLoaded(world, x, z)) {
                continue;
            }

            int groundY = world.getHighestBlockYAt(x, z);

            for (double y = groundY; y <= groundY + 10; y += 0.5) {
                player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnGroundOutline(Player player, World world, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x += 2) {
            spawnGroundParticle(player, world, x, minZ);
            spawnGroundParticle(player, world, x, maxZ);
        }

        for (int z = minZ; z <= maxZ; z += 2) {
            spawnGroundParticle(player, world, minX, z);
            spawnGroundParticle(player, world, maxX, z);
        }
    }

    private void spawnGroundParticle(Player player, World world, int x, int z) {
        if (!isChunkLoaded(world, x, z)) {
            return;
        }

        int y = world.getHighestBlockYAt(x, z) + 1;
        player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    private boolean isChunkLoaded(World world, int x, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }
}
