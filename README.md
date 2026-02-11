# GemSync - TNE to LPC Pro Chat Shop Bridge

A lightweight Paper/Spigot plugin that automatically syncs your TNE Gems currency with LPC Pro's Chat Shop points balance — in both directions.

## What It Does

- **Command Sync (TNE → LPC Pro)**: When Gems are given, taken, or set via any TNE command (`/money give`, `/money +`, `/money add`, `/eco give`, shorthand aliases, etc.), GemSync mirrors the change to LPC Pro's chat shop balance.
- **Purchase Sync (LPC Pro → TNE)**: When a player buys something from the chat shop, GemSync detects the points spent and deducts the same amount from their TNE Gems balance.
- **Join Sync**: When a player logs in, their TNE Gems balance is read via PlaceholderAPI and synced to LPC Pro's chat shop balance. Handles offline gem grants (e.g. from a webstore).
- **Console & Player Support**: Works with commands issued by players in-game and by console (important for webstore integration).

## Requirements

- Paper/Spigot 1.20+
- Java 17+
- TheNewEconomy (TNE)
- LPC Pro (with chat shop set to `points` mode)
- PlaceholderAPI (required for join sync and purchase sync)

## Building

```bash
cd GemSync
mvn clean package
```

The compiled jar will be in `target/GemSync-1.0.0.jar`.

## Installation

1. Drop `GemSync-1.0.0.jar` into your `plugins/` folder.
2. Set LPC Pro's `features/shop.yml` to:
   ```yaml
   currency-mode: "points"
   currency-name: "Gems"
   ```
3. Restart the server.
4. Configure `plugins/GemSync/config.yml` if needed (defaults work for a currency named "Gems").

## Configuration

```yaml
# The TNE currency identifier to track (must match your TNE currency config)
tne-currency: "Gems"

# Sync balance on player join (requires PlaceholderAPI)
sync-on-join: true

# Enable debug logging
debug: false
```

## Supported TNE Command Aliases

GemSync detects all of the following command formats:

| Format | Actions |
|--------|---------|
| `/money give/+/add` | Give gems |
| `/money take/-/remove` | Take gems |
| `/money set/=` | Set gems |
| `/eco give/+/add` | Give gems |
| `/eco take/-/remove` | Take gems |
| `/eco set/=` | Set gems |
| `/econ`, `/economy` | Same sub-commands |
| `/givemoney`, `/givebal`, `/ecogive`, `/addmoney`, `/addbal` | Give gems |
| `/takemoney`, `/takebal`, `/ecotake`, `/removemoney`, `/removebal` | Take gems |
| `/setmoney`, `/setbal`, `/ecoset` | Set gems |

The currency name (e.g. `Gems`) must appear as an argument for GemSync to trigger.

## Webstore Integration (Tebex / CraftingStore)

When selling Gems through your webstore, you only need one command per package:

```
money give {username} <amount> Gems
```

GemSync will automatically detect this command and mirror it to the chat shop. If the player is offline, it will sync on their next login.

## How It Works

1. **Command Sync**: Listens for TNE money commands at `MONITOR` priority. If the command targets the configured currency, it dispatches the equivalent `/chatshop give|take|set` command as the target player (with a temporary `lpcpro.shop.admin` permission grant).
2. **Purchase Sync**: When a player opens `/chatshop`, their LPC Pro points balance is recorded. When they close the shop inventory, GemSync checks if the balance decreased. If so, it runs `/money take` to deduct the same amount from TNE.
3. **Join Sync**: Reads the TNE balance via `%tne_balance_currency_Gems%` placeholder and sets the LPC Pro chat shop balance to match.

## Package

`sh.pcx.gemsync`
