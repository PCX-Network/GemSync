package sh.pcx.gemsync;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GemSync extends JavaPlugin {

    private String tneCurrency;
    private boolean debugMode;

    // Track the LPC Pro balance before a player opens the shop
    private final Map<UUID, Integer> preShopBalances = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(new CommandSyncListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinSyncListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopPurchaseListener(this), this);

        getLogger().info("GemSync enabled! Syncing TNE currency '" + tneCurrency + "' with LPC Pro Chat Shop.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GemSync disabled.");
    }

    public void loadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        tneCurrency = config.getString("tne-currency", "Gems");
        debugMode = config.getBoolean("debug", false);
    }

    public String getTneCurrency() {
        return tneCurrency;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public Map<UUID, Integer> getPreShopBalances() {
        return preShopBalances;
    }

    /**
     * Gets the player's current LPC Pro balance via PlaceholderAPI.
     * Returns -1 if unable to parse.
     */
    public int getLpcBalance(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return -1;

        try {
            String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%lpcpro_balance%");
            if (result == null || result.contains("%") || result.isEmpty()) return -1;

            // Clean up
            String cleaned = result.replaceAll("[^\\d.]", "");
            if (cleaned.isEmpty()) return -1;

            return (int) Double.parseDouble(cleaned);
        } catch (Exception e) {
            debug("Error getting LPC balance: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Syncs a balance change to LPC Pro's Chat Shop.
     * Runs as the target player since LPC Pro commands are player-only.
     */
    public void syncToShop(String playerName, double amount, SyncAction action) {
        String command;
        switch (action) {
            case GIVE:
                command = "chatshop give " + playerName + " " + (int) amount;
                break;
            case TAKE:
                command = "chatshop take " + playerName + " " + (int) amount;
                break;
            case SET:
                command = "chatshop set " + playerName + " " + (int) amount;
                break;
            default:
                return;
        }

        debug("Dispatching sync command: /" + command);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player target = Bukkit.getPlayerExact(playerName);

            if (target != null && target.isOnline()) {
                boolean hadPerm = target.hasPermission("lpcpro.shop.admin");
                org.bukkit.permissions.PermissionAttachment attachment = null;

                if (!hadPerm) {
                    attachment = target.addAttachment(this, "lpcpro.shop.admin", true);
                    debug("Granted temporary lpcpro.shop.admin to " + playerName);
                }

                target.performCommand(command);
                debug("Executed as player: /" + command);

                if (attachment != null) {
                    final org.bukkit.permissions.PermissionAttachment toRemove = attachment;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        target.removeAttachment(toRemove);
                        debug("Removed temporary lpcpro.shop.admin from " + playerName);
                    }, 5L);
                }
            } else {
                getLogger().info("Player " + playerName + " is offline. Balance will sync on next join.");
            }
        }, 5L);
    }

    /**
     * Deducts gems from TNE when a player spends them in the chat shop.
     */
    public void deductFromTNE(String playerName, int amount) {
        if (amount <= 0) return;

        String command = "money take " + playerName + " " + amount + " " + tneCurrency;
        debug("Deducting from TNE: /" + command);

        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            debug("TNE deduction executed: /" + command);
        });
    }

    public enum SyncAction {
        GIVE, TAKE, SET
    }
}
