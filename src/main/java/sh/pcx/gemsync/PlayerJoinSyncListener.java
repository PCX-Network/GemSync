package sh.pcx.gemsync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On player join, syncs their TNE Gem balance to LPC Pro Chat Shop.
 * Uses PlaceholderAPI with the correct TNE placeholder format:
 * %tne_balance_currency_<CurrencyName>%
 */
public class PlayerJoinSyncListener implements Listener {

    private final GemSync plugin;
    private boolean placeholderApiAvailable;

    public PlayerJoinSyncListener(GemSync plugin) {
        this.plugin = plugin;
        this.placeholderApiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        if (!placeholderApiAvailable) {
            plugin.getLogger().warning("PlaceholderAPI not found - join sync will be disabled.");
            plugin.getLogger().warning("Install PlaceholderAPI and the TNE expansion for automatic balance sync on join.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!placeholderApiAvailable) return;
        if (!plugin.getConfig().getBoolean("sync-on-join", true)) return;

        Player player = event.getPlayer();

        // Delay to allow TNE to fully load player data
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                String currency = plugin.getTneCurrency();

                // Correct TNE placeholder format: %tne_balance_currency_<Name>%
                String placeholder = "%tne_balance_currency_" + currency + "%";
                String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);

                plugin.debug("Join sync for " + player.getName() + ": " + placeholder + " -> " + result);

                // Check if placeholder resolved
                if (result == null || result.contains("%") || result.isEmpty()) {
                    plugin.debug("Placeholder did not resolve for " + player.getName() + ". Check that TNE PAPI expansion is installed.");
                    return;
                }

                // Clean up the balance string
                String cleaned = result;
                cleaned = cleaned.replaceAll("§[0-9a-fk-or]", "");   // Legacy color codes
                cleaned = cleaned.replaceAll("<[^>]+>", "");            // MiniMessage tags
                cleaned = cleaned.replaceAll(",", "");                  // Thousands separators
                cleaned = cleaned.replaceAll("[^\\d.]", "");            // Keep only digits and decimal

                if (cleaned.isEmpty()) {
                    plugin.debug("Could not extract numeric value from: " + result);
                    return;
                }

                double balance = Double.parseDouble(cleaned);
                plugin.debug("Parsed " + currency + " balance for " + player.getName() + ": " + balance);

                if (balance >= 0) {
                    plugin.syncToShop(player.getName(), balance, GemSync.SyncAction.SET);
                    plugin.debug("Join sync complete for " + player.getName() + ": set " + currency + " to " + (int) balance);
                }
            } catch (Exception e) {
                plugin.debug("Error during join sync for " + player.getName() + ": " + e.getMessage());
            }
        }, 60L); // 3 second delay for TNE to load player data
    }
}
