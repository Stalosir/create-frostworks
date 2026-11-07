# Integrations: the other mods Create: Frostworks works with

Every integration is optional except Create. Without its mod, an integration does nothing and loads none of that mod's
classes. This page says, mod by mod, what the integration changes and which settings go with it. Every setting is
described in [Configuration](configuration.md).

| Mod | Versions | What Frostworks does with it | Settings |
|---|---|---|---|
| [Create](#create) | required | the whole mod; hooks into its speed check, machines and basin | `rooms.overspeed`, `processing.coldProcessing` |
| [JEI, EMI](#jei-and-emi) | both | recipe categories | none |
| [TerraFirmaGreg](#terrafirmagreg) | Forge 1.20.1 | the pack's recipes, no overspeed, body temperature | `tfgCompat`, `coldSpeedBonus`, `tfcAmbientalRoomPercent` |
| [Greate](#greate) | Forge 1.20.1 | cold processing at its machines, its stress scale, a shaft passage | `greateStress` |
| [TerraFirmaCraft](#terrafirmacraft) | both | food preservation in the infuser | `integrations.tfcFood` |
| [TFC Ambiental (TerraFirmaGreg's)](#tfc-ambiental) | Forge 1.20.1 | a cold room sets the player's body temperature | `integrations.tfcAmbiental` |
| [Tough As Nails](#tough-as-nails) | both | a cold room is colder by its rules | `integrations.toughAsNails` |
| [Cold Sweat](#cold-sweat) | both | a cold room is colder by its rules | `integrations.coldSweat` |

## Create

Required: 6.0.10 and later on NeoForge 1.21.1, 6.0.4 or a later 6.0.x on Forge 1.20.1.

- **Overspeed.** In a COLD room, the blocks of the tags `frostworks:overspeed_machines` and
  `frostworks:overspeed_transmission` may turn past Create's `maxRotationSpeed` (see `rooms.overspeed`).
- **Lower stress in the cold.** In a closed room (cold or not powerful enough), every kinetic block of Create or of an
  add-on, except Frostworks' own, applies 10 % less stress from −10 °C and 20 % less from −20 °C. Capacity is never
  raised. There is no setting: it stays on even when the overspeed is off.
- **Cold processing.** The crushing wheels, the millstone and the basin (mixer and press over it) read the cold
  processing recipes (see [Cold processing](cold-processing.md); `processing.coldProcessing` turns them all off).
- **Display Link.** On a cold vent or a cold wall, Create's Display Link can show one reading of its room on a sign
  or a display board: its state, temperature, fastest speed allowed, active vents, coverage, or its name.
- **Ponder.** Every block has a scene (hold **W**).
- **If Create changes.** Each hook is optional. At start-up the log says, for each one,
  `Create hook ready: …` or `Create hook missing …`. A missing hook turns its feature off (machines keep Create's limit,
  machines keep their normal recipes); nothing else changes.

## JEI and EMI

JEI categories for the infusing, freezing, distilling, cold fluid, cold processing and food preservation recipes. The
infuser, the tower, the vent, the crushing wheels, the millstone, the basin, the mixer and the press are shown as
their catalysts. EMI shows them when it shows JEI's plugins (TerraFirmaGreg does).

## TerraFirmaGreg

Forge 1.20.1 (TerraFirmaGreg-Modern; TerraFirmaCraft and TFC Ambiental come inside TerraFirmaGreg-Core).

- **The pack's recipes** (`tfgCompat`, `AUTO` = on with the mod `tfg`): our crafts use the pack's materials, arrive at
  the steel age, and the crafts for Minecraft ores the pack does not have are left out. The recipe condition
  `frostworks:tfg_compat` lets a pack write its own crafts the same way.
- **No overspeed** (`coldSpeedBonus`, `AUTO` = off with `tfg`): GregTech's machines are not built for it. The lower
  stress in the cold stays.
- **Body temperature**: see [TFC Ambiental](#tfc-ambiental).
- **Liquid air** (`gtceu:liquid_air`): no recipe shipped. [Cold fluids](cold-fluids.md) has a ready-to-copy file to let
  vents use it.

## Greate

Forge 1.20.1 only.

- **Cold processing at its machines**: crushing wheels and millstones (`greate:crushing`, `greate:milling`), press
  (`greate:pressing`), fans on an item on the ground (`greate:splashing`, `greate:haunting`), saw (`greate:cutting`)
  and basin (`greate:mixing`, `greate:compacting`, `greate:brewing`, `greate:basin`). Frostworks ships files only for
  its crushers and millstones (the chilled ores' bonus); the others read a pack's files. A fan over a belt has no cold
  processing. Greate keeps its tier rule for its own results; ours are added after it, at any tier.
- **Stress scale** (`greateStress`, `AUTO` = on with `greate`): our machines' stress, and the expander's output,
  divided by 10 to fit Greate's small first tiers.
- **Shaft passage** for Greate's shafts, so that a tiered shaft can go through a cold wall.
- **Hooks**: the log says `Create hook ready: cold processing at Greate's …` for each machine.

## TerraFirmaCraft

Both versions (TFC 3.2 on Forge 1.20.1, 4.2 on NeoForge 1.21.1).

- **Food preservation**: the cryogenic infuser gives a TFC food a preservation trait for good: cold gas makes it last
  2.5 times longer, Frosium 4 times (the decay modifiers `integrations.tfcFood.*`). The recipes are
  `frostworks:food_preserving`; see [Food preservation](food-preservation.md).

## TFC Ambiental

TerraFirmaGreg's TFC Ambiental (inside TerraFirmaGreg-Core), Forge 1.20.1 only. The public TFC Ambiental is a
different mod and is not supported.

- **A closed cold room sets the player's body temperature**, as TerraFirmaGreg's heat pump does. A heat pump covering
  the player wins. Never warmer than the player would be without the room.
- `tfcAmbientalRoomPercent` softens it (100 = the room's temperature, 0 = nothing).
- **Frostworks' own frost on the screen is off** while this hook is ready: TFC Ambiental has its own.
- Details and what to expect with the pack's thresholds: [TFC Ambiental](tfc-ambiental.md).

## Tough As Nails

Both versions.

- **A player in a closed cold room is colder by Tough As Nails' rules**: one level colder at or below
  `toughAsNailsCoolerAt` (0 °C), COLD at or below `toughAsNailsColdAt` (−15 °C). Never colder than COLD, never
  warmer than the place already is. Its armour, campfires and drinks work as usual.
- `toughAsNailsEnabled` turns it off.

## Cold Sweat

Both versions.

- **In a closed cold room, Cold Sweat's world temperature is the room's**, softened below its Minimum Habitable
  Temperature by `coldSweatFactor` (0.5: half the cold below that threshold). Never warmer than the place was. An
  unprotected player gets cold, then hurt, as in a cold biome; Cold Sweat's gear protects as usual.
- The goggles show the room's temperature in Cold Sweat's units.
- `coldSweatEnabled` turns it off.

## Minecraft's frost

A room at `coldExposureTemperature` (−15 °C) or colder gives the player Minecraft's frost on the screen and its
slowness, never deadly (see `rooms.temperature`). It is there with or without Tough As Nails and Cold Sweat, and off
only while TFC Ambiental's hook is ready.
