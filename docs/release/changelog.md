# Changelog

## 1.1.0

**Before updating a world or a pack from 1.0.x:**
- **Server config**: the keys are sorted into new sections and four are renamed for Frosium. A server config from
  1.0.x is read as the defaults: set your values again.
- **Saved blocks (NBT)**: the Cryogenic Infuser's second tank is saved under a new key (`Frosium`), and the
  Distillation Tower no longer reads the older key of its output (from before its craft became a recipe file). What
  they held in an older world is lost; everything else is kept.
- **Ids**: the old `liquid_nitrogen` ids are aliases of Frosium (below), so worlds and pack recipes that name them
  still load.
- **Licence**: Create: Frostworks is now All Rights Reserved (it was MIT). The source code stays visible for reading;
  no copy, redistribution or derivative work without the written permission of the author.

- Dried kelp (Minecraft's and TerraFirmaCraft's) is never cryo-preserved: the Cryogenic Infuser keeps it for Chilled
  Dried Kelp. It is in the new item tag `frostworks:never_preserved`, to which a pack can add other foods.
- With TerraFirmaCraft, the Cryogenic Infuser also makes Chilled Dried Kelp from TFC's dried kelp
  (`tfc:food/dried_kelp`, 250 mB of cold gas, as Minecraft's), with or without TerraFirmaGreg
  (`infusing/cold_dried_kelp_tfc`; the TerraFirmaGreg-only `tfg/infusing/cold_dried_kelp` is gone).
- Create Nuclear is no longer listed as an optional dependency. Its nitrogen still cools the Cold Vents.

- The Distillation Tower now uses a round 3500 mB of cold gas for the 2000 mB of Frosium of one Packed Ice (setting
  `towerGasPerLiquid`, 1.75 mB of gas per mB; it was 3488). Its stress (192 su a layer at 64 RPM), its water and its
  ice do not change; the few mB of gas beyond its energy are lost, as the water.
- **Liquid nitrogen is now Frosium**, a fluid of our own: `frostworks:frosium`, `frostworks:flowing_frosium`,
  `frostworks:frosium_bucket` (the old ids `frostworks:liquid_nitrogen`, `frostworks:flowing_liquid_nitrogen` and
  `frostworks:liquid_nitrogen_bucket` are aliases: saved worlds, tanks, buckets and pack recipes that name them get
  Frosium). Every text says Frosium. Its config keys are renamed (`overspeedBonusPercentFrosium`,
  `infuserFrosiumTankCapacity`, `towerFrosiumPerPackedIce`, `chilledInfusionFrosium`): custom values go back to their
  defaults. The Cryogenic Infuser's second tank is saved under a new key: what it held in an older world is lost.
  Frosium is no longer in `c:nitrogen` / `forge:nitrogen` nor `createnuclear:nitrogen` (no more Create Nuclear reactor
  coolant); Create Nuclear's nitrogen still feeds the cold vents (`cold_fluid/create_nuclear`), but the Frost Tank
  and the Expander no longer take it. `#frostworks:cold_liquid` is kept.
- Cold processing recipes (`frostworks:cold_process`): data files that change what a machine gives in a closed cold
  room, by temperature steps (whole numbers below 0 °C). `replace` takes the place of the normal recipe's results,
  `bonus` adds to every recipe of the process; a result can be a copy of the recipe's main result, and a bonus can
  take the place of the recipe's last roll. For now: Create's Crushing Wheels and Millstone (`create:crushing`,
  `create:milling`) and Greate's crushing wheels, millstone and press (`greate:milling`, `greate:crushing`, `greate:pressing`, Forge 1.20.1). A "Cold Processing" category in JEI
  (and EMI). Pack makers: see `docs/modpack/cold-processing.md`. The Cryogenic Infuser can mark any item chilled (no new
  item: a mark on the stack, drawn as frost), and a cold processing file can ask for chilled stacks only; with Greate,
  every raw ore and ore block of the pack can be chilled for a bonus at Greate's machines in the cold.
- Chilled ores now use it: 15 % for one more ore out of the cold (unchanged), 25 % from −10 °C, 35 % from −20 °C, in
  closed rooms only (an open room gives no bonus), in steps instead of a straight line. The Millstone and Greate's
  machines can take cold processing files too.
- Cold processing at the basin: Create's mixer and press over a basin (`create:mixing`, `create:compacting`,
  `create:basin`) and Greate's basin (`greate:mixing`, `greate:compacting`, `greate:brewing`, `greate:basin`, Forge
  1.20.1) read cold processing files; Frostworks ships none for them. Every file now has a `recipe` field under
  `process`: one recipe by its id, or empty for every recipe of the process. At the basin, a step can give fluids
  (`amount` in mB instead of `count`) and an ingredient can be a fluid. `"chilled": true` now goes on each item
  ingredient instead of the whole file. A fan over a belt has no cold processing.
- Config: `chilledRoomBonus` and `chilledRoomBonusEnabled` are gone (the numbers are in the recipe file now); one
  switch, `coldProcessingEnabled` (section `processing.coldProcessing`, on by default), turns cold processing off.

- Cold Sweat (optional): a player in a closed cold room is in a cold place by its own rules. The room counts as it
  is down to Cold Sweat's Minimum Habitable Temperature, softened below it (`coldSweatFactor`, half its cold below
  that threshold by default), so the cold comes as the room cools, and a room at −20 °C stays colder than one at
  −10 °C. The goggles show the temperature Cold Sweat feels in the room, in the units chosen in Cold Sweat. The room never makes a place warmer, and Frostworks never
  touches the body temperature: an unprotected player gets cold, then hurt, as in a cold biome, and Cold Sweat's own
  gear protects as usual. Its thermometer and display show it. Server settings `coldSweatEnabled` (on by default) and
  `coldSweatFactor` (0.5).
- TFC Ambiental (TerraFirmaGreg's, Forge 1.20.1): a closed cold room sets the player's body temperature, like
  TerraFirmaGreg's heat pump (`tfcAmbientalRoomPercent`, 100 by default). Our own frost on the screen and its
  slowness are then left to TFC Ambiental: with its hook ready, Frostworks adds none.
- The server config is sorted into five groups: `rooms` (detection, temperature, overspeed), `blocks` (one section
  per block), `processing` (energy rates, chilled ores), `integrations` (Tough As Nails, Cold Sweat) and `debug`. Every setting
  keeps its name and default value. A server config from an earlier version is read as the defaults: set your values
  again in their new sections.
- Tough As Nails (optional): a player in a closed cold room is colder by its own rules. A room at 0 °C or below
  makes the place one level colder, at −15 °C or below COLD whatever the biome; never ICY, never warmer than the
  place already is, nothing where it is already COLD or ICY. Its armour, campfires and drinks work as usual. Its
  thermometer shows it too. Server settings `toughAsNailsEnabled` (on by default), `toughAsNailsCoolerAt` and
  `toughAsNailsColdAt`.
- Create's Display Link can read a cold room: aimed at a Cold Vent or a Cold Wall, it shows the room's status,
  temperature, fastest allowed speed, active vents (e.g. 3/6) or capacity (the cells its active vents cover out of its
  volume, as the goggles show it) or name on a sign, display board or nixie tube, refreshed every second, in each player's own
  language. A block in no room shows "No room".
- Cold rooms can be named: right-click one of the room's Cold Walls with a renamed name tag (the tag is kept).
  The name stays while the room stays as it is (closed, same size): opened, cut, joined to another or reshaped, it
  loses it; the goggles show it in place of "Cold room #1", and the Display Link shows it
  ("Unnamed" before).
- Cold Walls can be dyed: right-click one with any dye and every Cold Wall joined to it face to face takes the colour,
  as a wave from the clicked wall (one step every 2 ticks, with a puff of dye-coloured dust on each wall). One dye for
  the whole structure, none in creative; gray dye takes the colour away. The colour stays on the placed
  wall only: a dyed wall drops, and is picked as, the plain Cold Wall. At most `wallTintMaxBlocks` walls at once
  (4096 by default); a bigger structure is left as it is, and the player is told.
- Fixed: dyed Cold Walls lost their colour from afar or at a grazing angle.
- Fixed (Forge 1.20.1): the Cryogenic Infuser and the Frost Tank could not be crafted (their recipes used `c:` tags).
- Fixed (Forge 1.20.1): liquid nitrogen is now in the `forge:nitrogen` fluid tag (it was in `c:nitrogen`, which Forge
  1.20.1 does not use).

## Next version

- Forge 1.20.1 version (Create 6.0.4 and newer), next to the NeoForge 1.21.1 one.
- Overspeed: the cold room's limit is now a bonus over Create's own limit (`maxRotationSpeed`): +25 % on cold gas,
  +50 % on liquid nitrogen (320 and 384 RPM with Create's default 256). Settings `overspeedBonusPercent` and
  `overspeedBonusPercentNitrogen` replace `overspeedMaxRpm` and `overspeedMaxRpmNitrogen`.
- Fixed: a belt encased with Cold Walls inside a cold room (several segments in a row, or all along) left the cold,
  and broke 30 seconds later. A Belt Passage now belongs to the rooms all around it.
- Fixed: Shaft Passages in a row (a thick wall, or as decoration) left the cold and broke 30 seconds later.
- Fixed: a Create casing put on a Belt Passage left a hidden hole in the room, which opened later.
- Fixed: a Cold Vent turned with the wrench kept counting for its old room.
- Fixed: a saved room was dropped when one of its vents was in an unloaded chunk next to it.
- Overspeed: when a cold room's limit falls (liquid nitrogen short, cold gas still covering it), machines over the new
  limit get the same 30-second grace period as when the cold goes away, instead of breaking at once.
- Ice and snow layers no longer melt in a cold room down to its temperature (−10 °C), even next to a torch, whatever
  the biome.
- JEI: a "Distillation" page for the Distillation Tower (cold gas, water, ice and rotation into liquid nitrogen), with
  the server's settings.
- The Column Base's tooltip gives its stress impact, as Create's machines do.
- The Distillation Tower draws 3 su per rpm and per layer (setting `towerStressPerRpm`, replacing `towerWorkRatio`): a
  little more rotation and a little less cold gas for the same liquid nitrogen.
- A Cold Vent uses 15 mB of cold gas per second (was 25), and a newly placed Compressor takes 256 su (was 2048):
  one Compressor at its lowest setting now keeps a vent going. Worlds with an existing server config keep their values.
- The NeoForge version requires Create 6.0.x (6.0.10 or newer, below 6.1).
- The Distillation Tower's craft is now a recipe file (`frostworks:distilling`,
  `data/frostworks/recipe/distilling/liquid_nitrogen.json`), with the same numbers as before; JEI shows one page per
  recipe. The tower takes, at first, the first input of any recipe, then only what still makes a recipe with what it
  holds (holding cold gas: water and ice; liquid nitrogen is refused). Its goggles show one line per fluid held, with
  its amount and its room. Existing towers keep their content.

## 1.0.0 (first release)

The first public version. Everything below is new.

### Content
- Cold Wall, Strip Curtain Door, Cold Vent, Shaft, Pipe and Belt Passages: closed cold rooms, found and kept by the
  server, cold on cold gas (-10 °C) or on liquid nitrogen (-20 °C). On liquid nitrogen, players in them slow down a little.
- Overspeed: 320 RPM in a cold room, 384 on liquid nitrogen, with a grace period when the cold goes away. A belt holds
  as long as its ends and its shafts are in the cold, and may go out between two cold rooms.
- Compressor, Cryogenic Infuser, Distillation Tower (Column Base, column made of Frost Tanks), Frost Tank, Expander.
- Cold Iron Sheet, Chilled Dried Kelp, cold gas, liquid nitrogen.
- Ice: the Infuser freezes a basin of water with cold gas; the tower uses ice (plain, packed or blue) through the sides
  of its base, and stops without it.
- Chilled ores: 20 chilled ores (every ore Create's Crushing Wheels take, stone, deepslate and Nether) and chilled raw
  iron, copper, gold and zinc, made in the Cryogenic Infuser with 100 mB of liquid nitrogen (its second tank). Crushed
  by Create's wheels: what the base ore gives, plus one more crushed ore at 15 % (in the recipe, shown in JEI), 25 % in
  a room at −10 °C, 35 % at −20 °C.

### Help for players
- Ponder scenes for every block; JEI categories for infusion and freezing; Engineer's Goggles on rooms, vents and
  machines. English and French.

### For other add-ons
- Fluid tag `frostworks:cold_liquid`: a cold liquid of your own, taken by our tanks, vents and the Expander.
- Item data map `frostworks:ice_value`: what an item is worth to the tower, in packed ice.

### Known
- The four chilled raw ores use their base raw ore's texture.
- Create 6.0.10 and later (`[6.0.10,)`), tested on 6.0.10 only. Both hooks into Create (overspeed, chilled ores) are
  optional: with a Create that changed, the feature turns itself off and the log says so. A larger Create update (6.1,
  7.0) may change more than these two places and is not promised to work.
- No world compatibility promised before 1.0.
