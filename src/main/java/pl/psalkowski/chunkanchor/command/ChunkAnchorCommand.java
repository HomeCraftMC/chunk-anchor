package pl.psalkowski.chunkanchor.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.psalkowski.chunkanchor.manager.AnchorManager;
import pl.psalkowski.chunkanchor.manager.ChunkLoadManager;
import pl.psalkowski.chunkanchor.model.LoadMode;
import pl.psalkowski.chunkanchor.visualization.AnchorVisualizer;

import java.util.*;

public class ChunkAnchorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "remove", "list", "show", "mode", "enable", "disable");
    private static final List<String> LOAD_MODES = Arrays.asList("DEFAULT", "ALWAYS", "PLAYER_ONLINE");

    private final AnchorManager anchorManager;
    private final ChunkLoadManager chunkLoadManager;
    private final AnchorVisualizer visualizer;
    private final LoadMode defaultLoadMode;

    public ChunkAnchorCommand(AnchorManager anchorManager, ChunkLoadManager chunkLoadManager, AnchorVisualizer visualizer, LoadMode defaultLoadMode) {
        this.anchorManager = anchorManager;
        this.chunkLoadManager = chunkLoadManager;
        this.visualizer = visualizer;
        this.defaultLoadMode = defaultLoadMode;
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
            case "mode" -> handleMode(player, args);
            case "enable" -> handleEnable(player, args);
            case "disable" -> handleDisable(player, args);
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

        if (anchorManager.getEnabledAnchorCount(playerId) >= anchorManager.getLimit()) {
            player.sendMessage(Component.text("You have reached the maximum of " + anchorManager.getLimit() + " active anchors", NamedTextColor.RED));
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

        chunkLoadManager.loadChunksForAnchor(playerId, name);

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

        int enabledCount = anchorManager.getEnabledAnchorCount(playerId);
        player.sendMessage(Component.text()
                .append(Component.text("Your Chunk Anchors (", NamedTextColor.GOLD))
                .append(Component.text(enabledCount, NamedTextColor.YELLOW))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(anchorManager.getLimit(), NamedTextColor.YELLOW))
                .append(Component.text(" active):", NamedTextColor.GOLD))
                .build());

        anchors.forEach((name, anchor) -> {
            boolean isLoaded = chunkLoadManager.isAnchorLoaded(playerId, name);
            LoadMode effectiveMode = anchor.loadMode() == LoadMode.DEFAULT ? defaultLoadMode : anchor.loadMode();

            TextComponent.Builder builder = Component.text()
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(name, NamedTextColor.YELLOW));

            if (anchor.enabled()) {
                builder.append(Component.text(" [ENABLED]", NamedTextColor.GREEN));
            } else {
                builder.append(Component.text(" [DISABLED]", NamedTextColor.RED));
            }

            if (anchor.loadMode() != LoadMode.DEFAULT) {
                builder.append(Component.text(" [" + anchor.loadMode().name() + "]", NamedTextColor.LIGHT_PURPLE));
            } else {
                builder.append(Component.text(" [DEFAULT->" + effectiveMode.name() + "]", NamedTextColor.GRAY));
            }

            if (isLoaded) {
                builder.append(Component.text(" (loaded)", NamedTextColor.AQUA));
            }

            builder.append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(anchor.world(), NamedTextColor.WHITE))
                    .append(Component.text(" @ chunk ", NamedTextColor.GRAY))
                    .append(Component.text(anchor.chunkX(), NamedTextColor.WHITE))
                    .append(Component.text(", ", NamedTextColor.GRAY))
                    .append(Component.text(anchor.chunkZ(), NamedTextColor.WHITE));

            player.sendMessage(builder.build());
        });

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

    private boolean handleMode(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /chunkanchor mode <name> <DEFAULT|ALWAYS|PLAYER_ONLINE>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        String modeStr = args[2].toUpperCase();
        UUID playerId = player.getUniqueId();

        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, name);
        if (anchor == null) {
            player.sendMessage(Component.text("No anchor named '" + name + "' found", NamedTextColor.RED));
            return true;
        }

        LoadMode newMode;
        try {
            newMode = LoadMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid mode. Use DEFAULT, ALWAYS, or PLAYER_ONLINE", NamedTextColor.RED));
            return true;
        }

        LoadMode oldMode = anchor.loadMode();
        if (!anchorManager.setAnchorLoadMode(playerId, name, newMode)) {
            player.sendMessage(Component.text("Failed to set load mode", NamedTextColor.RED));
            return true;
        }

        chunkLoadManager.onAnchorModeChanged(playerId, name, oldMode, newMode);

        player.sendMessage(Component.text()
                .append(Component.text("Anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' load mode set to ", NamedTextColor.GREEN))
                .append(Component.text(newMode.name(), NamedTextColor.LIGHT_PURPLE))
                .build());

        return true;
    }

    private boolean handleEnable(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chunkanchor enable <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        UUID playerId = player.getUniqueId();

        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, name);
        if (anchor == null) {
            player.sendMessage(Component.text("No anchor named '" + name + "' found", NamedTextColor.RED));
            return true;
        }

        if (anchor.enabled()) {
            player.sendMessage(Component.text("Anchor '" + name + "' is already enabled", NamedTextColor.YELLOW));
            return true;
        }

        if (anchorManager.getEnabledAnchorCount(playerId) >= anchorManager.getLimit()) {
            player.sendMessage(Component.text("Cannot enable anchor: you have reached the maximum of " + anchorManager.getLimit() + " active anchors", NamedTextColor.RED));
            return true;
        }

        if (!anchorManager.setAnchorEnabled(playerId, name, true)) {
            player.sendMessage(Component.text("Failed to enable anchor", NamedTextColor.RED));
            return true;
        }

        chunkLoadManager.onAnchorEnabledChanged(playerId, name, true);

        player.sendMessage(Component.text()
                .append(Component.text("Anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' enabled", NamedTextColor.GREEN))
                .build());

        return true;
    }

    private boolean handleDisable(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /chunkanchor disable <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[1];
        UUID playerId = player.getUniqueId();

        AnchorManager.Anchor anchor = anchorManager.getAnchor(playerId, name);
        if (anchor == null) {
            player.sendMessage(Component.text("No anchor named '" + name + "' found", NamedTextColor.RED));
            return true;
        }

        if (!anchor.enabled()) {
            player.sendMessage(Component.text("Anchor '" + name + "' is already disabled", NamedTextColor.YELLOW));
            return true;
        }

        if (!anchorManager.setAnchorEnabled(playerId, name, false)) {
            player.sendMessage(Component.text("Failed to disable anchor", NamedTextColor.RED));
            return true;
        }

        chunkLoadManager.onAnchorEnabledChanged(playerId, name, false);

        player.sendMessage(Component.text()
                .append(Component.text("Anchor '", NamedTextColor.GREEN))
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("' disabled", NamedTextColor.GREEN))
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
        player.sendMessage(Component.text("  /chunkanchor mode <name> <mode>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set load mode (DEFAULT/ALWAYS/PLAYER_ONLINE)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /chunkanchor enable <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - Enable an anchor", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /chunkanchor disable <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - Disable an anchor", NamedTextColor.GRAY)));
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
            if (subcommand.equals("remove") || subcommand.equals("show") || subcommand.equals("mode") || subcommand.equals("enable") || subcommand.equals("disable")) {
                Set<String> anchorNames = anchorManager.getAnchorNames(player.getUniqueId());
                return anchorNames.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            if (subcommand.equals("mode")) {
                return LOAD_MODES.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}
