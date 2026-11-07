# Configuration: every setting of Create: Frostworks

The mod has three config files. This page lists every setting: what it does, what it turns on or off, and what it
changes in play. The same files and keys exist on both versions (NeoForge 1.21.1 and Forge 1.20.1).

| File | Where | Who reads it | When a change applies |
|---|---|---|---|
| `frostworks-server.toml` | `<world>/serverconfig/` (a new world copies `defaultconfigs/frostworks-server.toml` if the pack ships one) | the server, synced to the clients | read when used: most changes apply at once |
| `frostworks-common.toml` | `config/` | client and server, before any world loads | read when used, except `tfgCompat` (when recipes load, `/reload`) |
| `frostworks-client.toml` | `config/` | the client only (looks) | at once, the file is reloaded when it changes |

To ship your own server defaults in a pack, put the file in `defaultconfigs/`: every new world starts from it.

The comments the game writes into each file say the same in short. Where they and this page differ, this page is the
one that follows the 1.1 code.

## Server config (`frostworks-server.toml`)

Five groups: `rooms`, `blocks`, `processing`, `integrations`, `debug`.

### `rooms.detection`: how a cold room is found

A room is the closed space a vent blows into, bounded by cold walls, doors and passages. It is searched again a moment
after one of its blocks changes.

| Key | Default | Range | What it does |
|---|---|---|---|
| `roomSearchCap` | 20000 | 1 – 2000000 | A search that finds more cells than this stops and declares the room **OPEN** (not closed). Raise it for very big rooms; each cell costs a little time. |
| `rescanDelayTicks` | 10 | 0 – 1200 | Ticks between a change (wall, vent, door) and the new search. Higher: fewer searches while building. |
| `maxRescansPerTick` | 4 | 1 – 1000 | Searches started at most per tick and per dimension. The others wait for the next tick. |
| `maxCellsPerTick` | 50000 | 100 – 10000000 | Cells visited at most per tick and per dimension. A longer search goes on next tick (a big room takes a few ticks to be found). |

### `rooms.temperature`: the room's temperature (°C)

A closed room cools down while its vents cover its volume, and warms back up otherwise. How cold it can get is set by
the fluid of its vents (the cold fluid recipes, see [Cold fluids](cold-fluids.md)), not here.

| Key | Default | Range | What it does |
|---|---|---|---|
| `ambient` | 20.0 | −100 – 100 | The temperature of a room that is not cooled, and the one it warms back up to. |
| `coldExposureTemperature` | −15.0 | −273 – 100 | At or below this, a player in the room slowly gets Minecraft's frost (the frost on the screen and its slowness, capped well below "fully frozen": **it never hurts**) and a line above the hotbar gives the temperature. A player who cannot freeze (leather armour, creative) is left alone. With TerraFirmaGreg's TFC Ambiental, this frost is off: TFC Ambiental has its own (see [Integrations](integrations.md)). |
| `coolingRate` | 0.5 | 0 – 1000 | °C per second a room loses while it is COLD, down to its target. |
| `warmingRate` | 0.5 | 0 – 1000 | °C per second a room gains back when it is not COLD, up to `ambient`. |

### `rooms.overspeed`: machines past Create's speed limit

In a **COLD** room, the machines of the block tag `frostworks:overspeed_machines` (Create's mixer and saw by default)
and the blocks of `frostworks:overspeed_transmission` (shafts, cogwheels, gearbox, speedometer, our shaft passage) may
turn faster than Create's `maxRotationSpeed`. Add blocks to those tags to let more machines overspeed. The whole
feature is also switched by `coldSpeedBonus` in the common config (off by default in TerraFirmaGreg).

Apart from the overspeed, and with no setting: in a closed room, the machines of Create and its add-ons (not
Frostworks' own) apply 10 % less stress from −10 °C and 20 % less from −20 °C.

| Key | Default | Range | What it does |
|---|---|---|---|
| `overspeedEnabled` | true | | **Off**: no machine ever goes past Create's limit, cold or not. |
| `overspeedBonusPercent` | 25 | 0 – 1000 | The bonus in a COLD room, in percent of Create's limit: 25 = 125 %, 320 rpm with Create's default 256. |
| `overspeedBonusPercentFrosium` | 50 | 0 – 1000 | The bonus in a COLD room at −20 °C or colder (Frosium vents): 50 = 150 %, 384 rpm. |
| `overspeedGraceSeconds` | 30 | 0 – 3600 | Once the room stops being COLD, overspeed stays allowed this long; then an overspeeding block breaks, as in plain Create. A belt with one end out of the cold breaks at that end. |

### `blocks.compressor`: rotation into cold gas

| Key | Default | Range | What it does |
|---|---|---|---|
| `stressMin` | 256 | 1 – 1000000 | Lowest stress a player can set on a compressor (su). |
| `stressMax` | 32768 | 1 – 1000000 | Highest stress a player can set (su). |
| `stressStep` | 256 | 1 – 1000000 | Step of the value panel (su). |
| `stressDefault` | 256 | 1 – 1000000 | Setting of a newly placed compressor (su). |
| `stressMode` | `TOTAL` | `TOTAL`, `PER_RPM` | `TOTAL`: the network sees exactly the setting, whatever the speed. `PER_RPM`: the setting is the impact per rpm, like a regular Create machine (the network sees setting × rpm). |
| `gasPerStressUnit` | 0.075 | 0 – 1000 | Cold gas made per second for each su really taken (mB/s/su). 2048 su make 153.6 mB/s. One of the two rates of the stress loop (with `processing.energy.liquidPerEnergy`). |
| `bufferCapacity` | 1000 | 1 – 1000000 | The compressor's gas tank (mB). |
| `compressorMinRpm` | 8 | 1 – 256 | Least speed it works at. Above it, only the stress taken counts, not the speed. |

With Greate's stress scale on (`greateStress`, common config), the stress of the compressor is divided by 10 and the gas
it makes for the same setting stays the same.

### `blocks.infuser`: the cryogenic infuser

| Key | Default | Range | What it does |
|---|---|---|---|
| `infuserStressImpact` | 8.0 | 0 – 1000 | Its stress impact (su per rpm), Create's press by default. |
| `infuserTankCapacity` | 1000 | 1 – 1000000 | Its first tank, for cold gas (mB). |
| `infuserFrosiumTankCapacity` | 1000 | 1 – 1000000 | Its second tank, for cold liquids (`#frostworks:cold_liquid`, Frosium by default) (mB). |
| `infuserDwellTicks` | 40 | 0 – 1200 | Ticks its arm stays down on an item before the item is done. |
| `infuserIceGas` | 250 | 1 – 100000 | Cold gas used to freeze one ice in a basin of water under it (mB). |

### `blocks.vent`: the cold vent

What a vent takes, uses, covers and cools a room to is in the cold fluid recipes ([Cold fluids](cold-fluids.md)).

| Key | Default | Range | What it does |
|---|---|---|---|
| `activeWindowTicks` | 20 | 1 – 12000 | A vent stays active (counts for its room) this long after it last had fluid. Higher: a short gap in the supply does not open the room. |
| `bufferCapacity` | 250 | 1 – 100000 | The vent's tank (mB). |

### `blocks.reservoir`: the frost tank

| Key | Default | Range | What it does |
|---|---|---|---|
| `reservoirCapacityPerBlock` | 8000 | 1 – 1000000 | Capacity of each block of a frost tank (mB). |

### `blocks.expander`: Frosium back into rotation

| Key | Default | Range | What it does |
|---|---|---|---|
| `expanderLiquidRate` | 20 | 1 – 100000 | Cold liquid expanded per second while it has some (mB/s). With `liquidPerEnergy`, this sets the stress capacity it gives. |
| `expanderBuffer` | 1000 | 1 – 1000000 | Its two tanks: liquid in, cold gas out (mB). |

### `blocks.tower`: the distillation tower

A 2 × 2 multiblock: a layer of column bases and up to `towerMaxLayers − 1` layers of frost tanks above. It runs the
distilling recipes (cold gas + water + ice + rotation → Frosium by default).

| Key | Default | Range | What it does |
|---|---|---|---|
| `towerMaxLayers` | 8 | 1 – 64 | Layers of a tower at most, the base included (a layer is 4 blocks). |
| `towerCapacityPerBlock` | 4000 | 1 – 1000000 | Storage of each block of the column, the bases aside (mB). The product takes its room first; what it leaves is shared by the inputs. |
| `towerGasShare` | 0.5 | 0.01 – 0.99 | For the Frosium recipe (the `energy` yield): the share of the inputs' room given to cold gas; the other inputs share the rest. Other recipes give their own shares in their file. |
| `towerGasPerLayer` | 20 | 1 – 100000 | Cold gas (the recipe's first input) taken per second by each layer at `towerReferenceRpm` (mB/s). |
| `towerReferenceRpm` | 64 | 1 – 256 | The speed at which a layer takes `towerGasPerLayer`. The tower goes as fast as it turns. |
| `towerStressPerRpm` | 3.0 | 0 – 1000 | Stress drawn by each layer per rpm (su): 192 su a layer at 64 rpm. Divided by 10 with Greate's stress scale. |
| `towerWaterPerLiquid` | 0.5 | 0 – 100 | Water used per mB of Frosium made (lost, holds no energy). |
| `towerGasPerLiquid` | 1.75 | 0.01 – 1000 | Cold gas used per mB of Frosium made. The tower makes what this ratio allows, never more than the energy it gets (gas + stress); the gas beyond is lost. Default: one packed ice, 1000 mB of water and 3500 mB of cold gas make 2000 mB of Frosium. |
| `towerFrosiumPerPackedIce` | 2000 | 1 – 1000000 | Frosium made per packed ice used (mB). Packed ice is the unit of the ice values (item data map `frostworks:ice_value`: ice 1/9, packed ice 1, blue ice 9). |

### `blocks.door`: the strip curtain door

| Key | Default | Range | What it does |
|---|---|---|---|
| `flapMinSpeed` | 0.02 | 0 – 10 | Least movement of an entity in the door that swings the strips (blocks per tick; walking is about 0.2, sneaking about 0.07). It stops counting as moving below half of it. |

### `blocks.wall`: cold wall tint

| Key | Default | Range | What it does |
|---|---|---|---|
| `wallTintMaxBlocks` | 4096 | 1 – 65536 | A dye on a cold wall colours every cold wall joined to it face to face, up to this many. A bigger structure is left as it is, and the player is told. |

### `processing.energy`: the stress loop

Energy is counted in su·s (stress × seconds). The same two rates are used both ways, without loss: the compressor turns
stress into gas (`gasPerStressUnit`), and the tower and the expander turn energy into cold liquid and back.

| Key | Default | Range | What it does |
|---|---|---|---|
| `liquidPerEnergy` | 0.025 | 0.000001 – 1000 | mB of cold liquid per su·s of energy. Lower: Frosium holds more energy (the expander gives more stress per mB). |

### `processing.chilledOres`

| Key | Default | Range | What it does |
|---|---|---|---|
| `chilledInfusionFrosium` | 100 | 1 – 100000 | Frosium the infuser uses to chill one ore or raw material (mB). |

### `processing.coldProcessing`

| Key | Default | What it does |
|---|---|---|
| `coldProcessingEnabled` | true | **Off**: no cold processing recipe applies anywhere (chilled ores' bonus included); every machine gives its normal recipe. See [Cold processing](cold-processing.md). |

### `integrations`: other mods

Each section does nothing when its mod is absent. What each integration does is in [Integrations](integrations.md).

| Section | Key | Default | Range | What it does |
|---|---|---|---|---|
| `toughAsNails` | `toughAsNailsEnabled` | true | | **Off**: cold rooms do not change Tough As Nails' temperature. |
| | `toughAsNailsCoolerAt` | 0.0 | −273 – 100 | At or below this room temperature (°C), the room makes the place one level colder. |
| | `toughAsNailsColdAt` | −15.0 | −273 – 100 | At or below this, the room makes the place COLD, whatever the biome. |
| `coldSweat` | `coldSweatEnabled` | true | | **Off**: cold rooms do not change Cold Sweat's temperature. |
| | `coldSweatFactor` | 0.5 | 0 – 1 | How much of the room's cold below Cold Sweat's Minimum Habitable Temperature it feels: 1 = all of it, 0 = none. |
| `tfcFood` | `coldPreservedModifier` | 0.4 | 0.01 – 1 | Decay modifier of the cold gas preservation (level 1): 0.4 = a food lasts 2.5 times longer. Changing it changes the dates of foods already preserved. |
| | `deepColdPreservedModifier` | 0.25 | 0.01 – 1 | Decay modifier of the Frosium preservation (level 2): 0.25 = 4 times longer. |
| `tfcAmbiental` | `tfcAmbientalRoomPercent` | 100 | 0 – 100 | How much a closed cold room sets the player's body temperature in TFC Ambiental (TerraFirmaGreg, Forge 1.20.1): 100 = the room's temperature, 50 = halfway from the normal environment, 0 = nothing. |

### `debug`

| Key | Default | What it does |
|---|---|---|
| `roomLeaks` | false | Logs every room found OPEN (or waiting for an unloaded chunk): the box searched, why (cell cap, left the world, unloaded chunk) and the cell farthest from the vent. Use it to find the hole in a room that will not close. |

## Common config (`frostworks-common.toml`)

Read before a world loads, the same on every side. Each key is `AUTO`, `ON` or `OFF`.

| Key | Default | What it does |
|---|---|---|
| `integrations.tfgCompat` | `AUTO` | TerraFirmaGreg recipes (recipe condition `frostworks:tfg_compat`): the pack's materials in place of Create's and Minecraft's, steel age crafts, and no recipes for the Minecraft ores the pack does not have. `AUTO`: on when the mod `tfg` is loaded. Read when recipes load (`/reload`). |
| `integrations.coldSpeedBonus` | `AUTO` | The cold room's overspeed (see `rooms.overspeed`). `AUTO`: off when `tfg` is loaded, on otherwise. The lower stress in the cold stays on either way. |
| `integrations.greateStress` | `AUTO` | Greate's stress scale: the stress of the compressor, the tower and the infuser divided by 10, and the expander's output too, for Greate's small first tiers (32 su). The fluids made for a setting stay the same, so the loop stays balanced. `AUTO`: on when `greate` is loaded. |

## Client config (`frostworks-client.toml`)

Looks only, nothing the server needs. A change shows at once.

| Key | Default | Range | What it does |
|---|---|---|---|
| `door.flapSwingFactor` | 0.1 | 0 – 1 | Swing of the door's strips when something walks through, as a share of the full swing (45 to 60°, from the speed): 0.1 gives 4.5 to 6°, 0 no swing. |
| `vent.ventPropellerRpm` | 64 | 0 – 256 | Speed of the propeller of a vent that cools a room (rpm); 0 keeps it still. |

## Beyond the config

Some changes are made with data packs, not the config:

- **Tags**: `frostworks:overspeed_machines` and `frostworks:overspeed_transmission` (blocks that may overspeed),
  `frostworks:cold_walls` (blocks that count as cold walls), `frostworks:cold_liquid` (fluids the frost tank, the
  infuser's second tank and the expander take), `frostworks:fans` and `frostworks:pumps` (items used in the
  TerraFirmaGreg crafts), `frostworks:never_preserved` (foods never preserved: dried kelp).
- **Data map** `frostworks:ice_value` (items the tower takes as ice, in packed ice).
- **Recipes**: see [Recipes](recipes.md).
