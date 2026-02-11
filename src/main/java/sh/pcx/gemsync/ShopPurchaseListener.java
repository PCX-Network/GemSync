package sh.pcx.gemsync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Detects when a player purchases something from the LPC Pro Chat Shop
 * and deducts the equivalent amount from their TNE Gems balance.
 *
 * Strategy:
 * 1. When a player opens /chatshop, record their current LPC Pro balance.
 * 2. When they close any inventory, check if their LPC Pro balance decreased.
 * 3. If it decreased, deduct the difference from TNE Gems.
 */
public class ShopPurchaseListener implements Listener {

    private final GemSync plugin;

    public ShopPurchaseListener(GemSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Detect when a player opens the chat shop via command.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase().trim();

        // Detect chatshop open commands
        if (cmd.equals("/chatshop") || cmd.equals("/cshop") ||
                cmd.equals("/perkshop") || cmd.equals("/chatstore") ||
                cmd.startsWith("/chatshop colors") || cmd.startsWith("/chatshop names") ||
                cmd.startsWith("/chatshop tags") || cmd.startsWith("/chatshop perks")) {

            Player player = event.getPlayer();
            // Small delay to let the GUI open and balance to be current
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int balance = plugin.getLpcBalance(player);
                if (balance >= 0) {
                    plugin.getPreShopBalances().put(player.getUniqueId(), balance);
                    plugin.debug("Shop opened by " + player.getName() + ", recorded LPC balance: " + balance);
                }
            }, 5L);
        }
    }

    /**
     * When a player closes an inventory, check if they were in the shop
     * and if their LPC Pro balance decreased (meaning they bought something).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        if (!plugin.getPreShopBalances().containsKey(player.getUniqueId())) {
            return; // Player wasn't in the shop
        }

        // Delay to let LPC Pro process the purchase
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int previousBalance = plugin.getPreShopBalances().getOrDefault(player.getUniqueId(), -1);
            if (previousBalance < 0) return;

            int currentBalance = plugin.getLpcBalance(player);
            if (currentBalance < 0) return;

            int spent = previousBalance - currentBalance;

            plugin.debug("Shop closed by " + player.getName()
                    + " - Previous: " + previousBalance
                    + ", Current: " + currentBalance
                    + ", Spent: " + spent);

            if (spent > 0) {
                plugin.getLogger().info(player.getName() + " spent " + spent + " Gems in Chat Shop. Deducting from TNE.");
                plugin.deductFromTNE(player.getName(), spent);

                // Update tracked balance
                plugin.getPreShopBalances().put(player.getUniqueId(), currentBalance);
            } else {
                // No purchase, clean up
                plugin.getPreShopBalances().remove(player.getUniqueId());
            }
        }, 10L);
    }
}
