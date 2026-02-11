package sh.pcx.gemsync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Listens for TNE money commands that affect Gems and syncs the
 * equivalent change to LPC Pro's Chat Shop balance.
 *
 * Supports all known TNE command formats and aliases.
 */
public class CommandSyncListener implements Listener {

    private final GemSync plugin;

    // Sub-command aliases for give/take/set
    private static final Set<String> GIVE_ALIASES = new HashSet<>(Arrays.asList(
            "give", "+", "add"
    ));
    private static final Set<String> TAKE_ALIASES = new HashSet<>(Arrays.asList(
            "take", "-", "remove"
    ));
    private static final Set<String> SET_ALIASES = new HashSet<>(Arrays.asList(
            "set", "="
    ));

    // All base commands that TNE uses (with /money or /eco style)
    // Format: /basecommand <subcommand> <player> <amount> [world] [currency]
    private static final Set<String> MONEY_BASE_COMMANDS = new HashSet<>(Arrays.asList(
            "/money", "/eco", "/econ", "/economy"
    ));

    // Shorthand commands: /givemoney, /givebal, /givemoney, /ecogive, etc.
    // Format: /shorthand <player> <amount> [world] [currency]
    private static final Set<String> GIVE_SHORTHAND = new HashSet<>(Arrays.asList(
            "/givemoney", "/givebal", "/ecogive", "/ecogivemoney",
            "/moneygive", "/addmoney", "/addbal"
    ));
    private static final Set<String> TAKE_SHORTHAND = new HashSet<>(Arrays.asList(
            "/takemoney", "/takebal", "/ecotake", "/ecotakemoney",
            "/moneytake", "/removemoney", "/removebal"
    ));
    private static final Set<String> SET_SHORTHAND = new HashSet<>(Arrays.asList(
            "/setmoney", "/setbal", "/ecoset", "/ecosetmoney",
            "/moneyset"
    ));

    public CommandSyncListener(GemSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        plugin.debug("Player command detected: " + message);
        processCommand(message, "player:" + event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String message = "/" + event.getCommand();
        plugin.debug("Console command detected: " + message);
        processCommand(message, "console");
    }

    private void processCommand(String command, String source) {
        String original = command.trim();
        String normalized = original.toLowerCase();

        String action = null;
        String[] args = null;

        // 1. Check for base command + sub-command format: /money give, /eco add, etc.
        for (String base : MONEY_BASE_COMMANDS) {
            if (normalized.startsWith(base + " ")) {
                String afterBase = original.substring(base.length()).trim();
                String[] parts = afterBase.split("\\s+");
                if (parts.length >= 1) {
                    action = resolveAction(parts[0].toLowerCase());
                    if (action != null) {
                        args = new String[parts.length - 1];
                        System.arraycopy(parts, 1, args, 0, args.length);
                    }
                }
                break;
            }
        }

        // 2. Check shorthand commands if no match yet
        if (action == null) {
            String firstWord = normalized.contains(" ") ? normalized.substring(0, normalized.indexOf(' ')) : normalized;

            if (GIVE_SHORTHAND.contains(firstWord)) {
                action = "give";
                args = original.substring(original.indexOf(' ') + 1).trim().split("\\s+");
            } else if (TAKE_SHORTHAND.contains(firstWord)) {
                action = "take";
                args = original.substring(original.indexOf(' ') + 1).trim().split("\\s+");
            } else if (SET_SHORTHAND.contains(firstWord)) {
                action = "set";
                args = original.substring(original.indexOf(' ') + 1).trim().split("\\s+");
            }
        }

        if (action == null || args == null) {
            return;
        }

        plugin.debug("Detected action: " + action + ", args count: " + args.length);
        for (int i = 0; i < args.length; i++) {
            plugin.debug("  arg[" + i + "]: " + args[i]);
        }

        // Expected: <player> <amount> [world] [currency]
        if (args.length < 2) {
            plugin.debug("Not enough arguments, ignoring.");
            return;
        }

        String targetPlayer = args[0];
        String amountStr = args[1];

        // Check if our tracked currency appears in remaining args
        String currency = plugin.getTneCurrency();
        boolean currencyMatch = false;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(currency)) {
                currencyMatch = true;
                break;
            }
        }

        if (!currencyMatch) {
            plugin.debug("Currency '" + currency + "' not found in command args, ignoring.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            plugin.debug("Invalid amount: " + amountStr);
            return;
        }

        if (amount <= 0) {
            plugin.debug("Amount is zero or negative, ignoring.");
            return;
        }

        GemSync.SyncAction syncAction;
        switch (action) {
            case "give":
                syncAction = GemSync.SyncAction.GIVE;
                break;
            case "take":
                syncAction = GemSync.SyncAction.TAKE;
                break;
            case "set":
                syncAction = GemSync.SyncAction.SET;
                break;
            default:
                return;
        }

        plugin.getLogger().info("Syncing " + action.toUpperCase() + " " + (int) amount + " " + currency
                + " for " + targetPlayer + " to Chat Shop (source: " + source + ")");
        plugin.syncToShop(targetPlayer, amount, syncAction);
    }

    private String resolveAction(String subCommand) {
        if (GIVE_ALIASES.contains(subCommand)) return "give";
        if (TAKE_ALIASES.contains(subCommand)) return "take";
        if (SET_ALIASES.contains(subCommand)) return "set";
        return null;
    }
}
