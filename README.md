# Create: Frostworks

Industrial cold for [Create](https://modrinth.com/mod/create). Build insulated cold rooms, cool them with cold gas or
Frosium, and run your machines **past Create's 256 RPM limit** inside them. Store rotation as Frosium
and give it back with the Expander.

Two versions, the same mod:

| Minecraft | Loader | Create | Jar |
|---|---|---|---|
| 1.21.1 | NeoForge 21.1.219 or newer | 6.0.10 and later | `frostworks-<version>.jar` |
| 1.20.1 | Forge 47.2.0 or newer | 6.0.x (from 6.0.4) | `frostworks-<version>-forge-1.20.1.jar` |

## What it does

- **Cold rooms.** Close a space with Cold Walls (a Strip Curtain Door lets you walk through; Shaft, Pipe and Belt
  Passages let rotation, fluids and items in). Cold Vents blow cold gas into it; once their coverage reaches the
  room's volume, the room is **cold**. Engineer's Goggles on a wall show its state, temperature and what is missing.
- **Overspeed.** In a cold room, machines may turn **25 % faster** than Create allows (320 RPM by default), or
  **50 %** faster on Frosium (384 RPM). The bonus follows Create's own limit. If the cold goes away, the room
  turns normal after a grace period, and Create's rule applies again.
- **Storing rotation.** The Distillation Tower turns cold gas, water, ice and rotation into Frosium, stored in
  Frost Tanks. The Expander turns it back into stress capacity (and cold gas).
- **Chilled ores.** The Cryogenic Infuser chills ores and raw iron, copper, gold and zinc with Frosium. Crushed
  by Create's Crushing Wheels, they give what their base ore gives, and one more crushed ore 15 % of the time (shown
  in JEI), 25 % in a room at −10 °C, 35 % at −20 °C. Create's own chances do not change.

## The three tiers

1. **No cold yet.** The Compressor (rotation → cold gas) and the Cryogenic Infuser (cold gas → Cold Iron Sheets, Chilled
   Dried Kelp, and ice from a basin of water) need nothing of the mod.
2. **Cold gas.** Cold Iron Sheets make Cold Walls, hence the rooms: Cold Vents, the door, the passages.
3. **Frosium.** Column Bases, Frost Tanks (set 2 × 2 on the bases, they become the column) and the Expander.
   The tower uses ice, which the Infuser freezes from water: plain Ice, Packed Ice or Blue Ice.

Every block has a Ponder scene (hold **W** on it). With JEI, the infusion and freezing recipes have their own
categories.

## Dependencies

| Mod | | |
|---|---|---|
| Create | required | 6.0.10 and later (`[6.0.10,)`), tested on 6.0.10; see below |
| JEI | optional | recipe categories |

On Forge 1.20.1: Create 6.0.4 or a later 6.0.x (it ships Ponder, Flywheel and Registrate), JEI 15 (optional).

**Create versions.** The mod hooks into Create in two places: its speed check (overspeed) and its crushing wheels
(chilled ores). Both hooks are optional. If a later Create changes the code they hook into, the game still loads:
the feature turns itself off (machines keep Create's 256 RPM limit; chilled ores keep their 15 %), the log says so at
start-up, and the goggles on a cold room say the overspeed is off. Only Create 6.0.10 has been tested.

Other add-ons can add their own cold liquid (fluid tag `frostworks:cold_liquid`) and their own ice for the tower
(item data map `frostworks:ice_value`, in packed ice).

## Reporting a problem

Please say what you saw, with the mod's version (`1.0.0` and so on) and the log (`logs/latest.log`).

## Documentation

Modpack makers: see [README-HELP-MAKERS](README-HELP-MAKERS.md). It lists every guide: the configuration, the
integrations, the recipes and data, and the changelog.

## License

All Rights Reserved. The source code is visible for reading only: no copy, redistribution or derivative work
without the written permission of the author. See `LICENSE`.

## Credits

Art (textures, models): Stalosir. Code: written with Claude (Anthropic). An add-on for Create, by the Create Team.
