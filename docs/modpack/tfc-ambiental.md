# Cold rooms and TFC Ambiental (TerraFirmaGreg)

Since 1.1, on **Forge 1.20.1**, a closed cold room counts in the body temperature of **TFC Ambiental as TerraFirmaGreg
ships it** (`tfcambiental`, inside the TerraFirmaGreg-Core jar). It behaves there like TerraFirmaGreg's heat pump.
Without that mod, nothing happens and nothing breaks.

The public TFC Ambiental (different code) and NeoForge 1.21.1 are not supported.

## The rules

- **A closed room** (cold or not powerful enough, not open) sets its temperature on the player standing in it. The
  player must be inside the room's volume.
- **As TerraFirmaGreg's heat pump does**, the environment is replaced by one modifier: the room's temperature, at the
  pump's potency (10).
  - No longer counted: TFC's climate, the time of day, rain, wind, being underground, the warmth of an indoor space
    ("cosy"), thirst, food, being wet.
  - Still counted: clothes, effects, what the player carries (hot ingots) and nearby blocks (ice, fires, firepits).
- **A heat pump already covering the player wins**: its rule, none of ours.
- **Never warmer**: if the normal environment is already colder than the room (a cold biome's winter), it stays.
- **The server does everything.** TFC Ambiental sends the temperature to the client itself (its gauge).
- **No frost of ours.** Minecraft's frost on the screen, its slowness and the temperature line above the hotbar, that
  the mod gives below −15 °C (`coldExposureTemperature`), are off while the hook is ready: TFC Ambiental has its own
  frost and effects, and the room acts through them. Hook `missing`: our frost stays.

## What it gives with the pack's thresholds

TerraFirmaGreg sets TFC Ambiental so that the body is cool below 5 °C and freezes below 0 °C. Each wool piece counts +4.

| Room | No clothes | Wool (4 pieces) |
|---|---|---|
| −10 °C (cold gas) | below freezing: damage | cool, no damage |
| −20 °C (Frosium) | fast damage | slow damage |

A full Nano, Quantum or space suit protects from everything, as anywhere with TFC Ambiental. The body changes
temperature slowly: walking through a room does nothing, staying in it without clothes hurts.

## The setting

In the server config, `integrations.tfcAmbiental.tfcAmbientalRoomPercent`, 0 to 100, **100 by default**:
- **100**: the room's temperature, as a heat pump;
- **50**: halfway between the normal environment and the room (a softer pack);
- **0**: the room does nothing.

## At start-up

The log says whether the hook is in place:

```
Create hook ready: cold rooms in TFC Ambiental's body temperature
```

With `missing` instead (another version of TerraFirmaGreg-Core), the room does not count and nothing else changes.
Without TerraFirmaGreg-Core, nothing is written.
