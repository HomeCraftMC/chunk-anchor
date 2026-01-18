package pl.psalkowski.chunkanchor.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.psalkowski.chunkanchor.manager.AnchorManager;
import pl.psalkowski.chunkanchor.manager.ChunkLoadManager;
import pl.psalkowski.chunkanchor.visualization.AnchorVisualizer;

import java.util.*;

public class ChunkAnchorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "remove", "list", "show");

    private final AnchorManager anchorManager;
    private final ChunkLoadManager chunkLoadManager;
    private final AnchorVisualizer visualizer;

    public ChunkAnchorCommand(AnchorManager anchorManager, ChunkLoadManager chunkLoadManager, AnchorVisualizer visualizer) {
        this.anchorManager = anchorManager;
        this.chunkLoadManager = chunkLoadManager;
        this.visualizer = visualizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        return switch (subcommand) {
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "show" -> handleShow(player, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chunkanchor add <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        UUID playerId = player.getUniqueId();

        if (anchorManager.getAnchorCount(playerId) >= anchorManager.getLimit()) {
            player.sendMessage(Component.text("You have reached the maximum of " + anchorManager.getLimit() + " anchors", NamedTextColor.RED));
            return true;
        }

        if (anchorManager.getAnchor(playerId, name) != null) {
            player.sendMessage(Component.text("An anchor named '" + name + "' already exists", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        boolean added = anchorManager.addAnchor(playerId, name, loc.getWorld(), x, z);
        if (!added) {
            player.sendMessage(Component.text("Failed to create anchor", NamedTextColor.RED));
            return true;
        }

        if (chunkLoadManager.isActive()) {
            chunkLoadManager.loadChunksForAnchor(playerId, name);
        }

        player.sendMessage(Component.text()
                .append(Component.text("Anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' created at (", NamedTextColor.GREEN))
                .append(Component.text(x, NamedTextColor.YELLOW))
                .append(Component.text(", ", NamedTextColor.GREEN))
                .append(Component.text(z, NamedTextColor.YELLOW))
                .append(Component.text(") in ", NamedTextColor.GREEN))
                .append(Component.text(loc.getWorld().getName(), NamedTextColor.YELLOW))
                .build());

        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chunkanchor remove <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        UUID playerId = player.getUniqueId();

        if (anchorManager.getAnchor(playerId, name) == null) {
            player.sendMessage(Component.text("No anchor named '" + name + "' found", NamedTextColor.RED));
            return true;
        }

        chunkLoadManager.unloadChunksForAnchor(playerId, name);
        anchorManager.removeAnchor(playerId, name);

        player.sendMessage(Component.text()
                .append(Component.text("Anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' removed", NamedTextColor.GREEN))
                .build());

        return true;
    }

    private boolean handleList(Player player) {
        UUID playerId = player.getUniqueId();
        Map<String, AnchorManager.Anchor> anchors = anchorManager.getPlayerAnchors(playerId);

        if (anchors.isEmpty()) {
            player.sendMessage(Component.text("You have no anchors", NamedTextColor.YELLOW));
            return true;
        }

        player.sendMessage(Component.text("Your anchors:", NamedTextColor.GOLD));
        anchors.forEach((name, anchor) -> {
            player.sendMessage(Component.text()
                    .append(Component.text("- ", NamedTextColor.GRAY))
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(anchor.world(), NamedTextColor.GREEN))
                    .append(Component.text(" @ (", NamedTextColor.GRAY))
                    .append(Component.text(anchor.x(), NamedTextColor.AQUA))
                    .append(Component.text(", ", NamedTextColor.GRAY))
                    .append(Component.text(anchor.z(), NamedTextColor.AQUA))
                    .append(Component.text(")", NamedTextColor.GRAY))
                    .build());
        });

        player.sendMessage(Component.text()
                .append(Component.text("Total: ", NamedTextColor.GOLD))
                .append(Component.text(anchors.size(), NamedTextColor.YELLOW))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(anchorManager.getLimit(), NamedTextColor.YELLOW))
                .append(Component.text(" anchors", NamedTextColor.GOLD))
                .build());

        return true;
    }

    private boolean handleShow(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chunkanchor show <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        UUID playerId = player.getUniqueId();

        if (anchorManager.getAnchor(playerId, name) == null) {
            player.sendMessage(Component.text("No anchor named '" + name + "' found", NamedTextColor.RED));
            return true;
        }

        visualizer.showAnchor(player, playerId, name);

        player.sendMessage(Component.text()
                .append(Component.text("Showing anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' boundaries for 30 seconds", NamedTextColor.GREEN))
                .build());

        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("ChunkAnchor Commands:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  /chunkanchor add <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - Create anchor at current location", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /chunkanchor remove <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove an anchor", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /chunkanchor list", NamedTextColor.YELLOW)
                .append(Component.text(" - List your anchors", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /chunkanchor show <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - Visualize anchor boundaries", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            if (subcommand.equals("remove") || subcommand.equals("show")) {
                Set<String> anchorNames = anchorManager.getAnchorNames(player.getUniqueId());
                return anchorNames.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}
