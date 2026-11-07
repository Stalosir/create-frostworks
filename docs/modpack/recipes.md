# Recipes: every recipe type of Create: Frostworks

All of the mod's crafts are data files: a pack can add, replace or remove them with a data pack, like any recipe. They
go in `data/<namespace>/recipe/<type>/` in 1.21.1 and `data/<namespace>/recipes/<type>/` in 1.20.1. The examples below
are written for 1.21.1; in 1.20.1, a result's `id` is `item` (a fluid's is `fluid`), a fluid ingredient has no
`"type": "neoforge:single"`, `c:` tags are `forge:` tags, and `neoforge:conditions` is `conditions`.

| Type | Machine | What it does | Guide |
|---|---|---|---|
| `frostworks:cold_process` | crushing wheels, millstone, basin; Greate's machines | other results in a closed cold room | [Cold processing](cold-processing.md) (two complete examples at the end) |
| `frostworks:cold_fluid` | cold vent | which fluid a vent takes, how much, how far, how cold | [Cold fluids](cold-fluids.md) |
| `frostworks:food_preserving` | cryogenic infuser | a TerraFirmaCraft food lasts longer | [Food preservation](food-preservation.md) |
| `frostworks:infusing` | cryogenic infuser | an item and a cold fluid into another item, or the item marked chilled | below |
| `frostworks:freezing` | cryogenic infuser over a basin | water into ice | below |
| `frostworks:distilling` | distillation tower | fluids, ice and rotation into a product | below |
| `create:item_application` | (Create's) | the cold wall: a cold iron sheet on stone | Create's format |
| crafting | crafting table | the mod's blocks | Minecraft's format |

The recipe condition **`frostworks:tfg_compat`** is true when the TerraFirmaGreg recipes are on (`tfgCompat` in the
common config). Our files use it to switch between the normal crafts and the pack's (in `recipe/tfg/`).

## `frostworks:infusing`

The infuser presses an item while it holds cold fluid: cold gas in its first tank, a cold liquid
(`#frostworks:cold_liquid`, Frosium) in its second.

```json
{
  "type": "frostworks:infusing",
  "ingredients": [
    { "item": "create:iron_sheet" },
    { "type": "neoforge:single", "amount": 500, "fluid": "frostworks:cold_gas" }
  ],
  "results": [ { "id": "frostworks:cold_iron_sheet" } ]
}
```

- One item, one fluid. The amount is used per item.
- **`"results": []`** gives back the same item **marked chilled** (a mark on the stack, drawn as frost): one recipe on a
  tag covers every ore of a pack. Ours mark `#c:ores` and `#c:raw_materials` with 100 mB of Frosium
  (`chilledInfusionFrosium` sets it). A stack already marked is not infused again. A cold processing file can then
  ask for chilled ingredients.

## `frostworks:freezing`

The infuser over a basin of water freezes it into ice, with cold gas from its tank (`infuserIceGas`, 250 mB).

```json
{
  "type": "frostworks:freezing",
  "ingredients": [ { "type": "neoforge:single", "amount": 1000, "fluid": "minecraft:water" } ],
  "results": [ { "id": "minecraft:ice" } ]
}
```

## `frostworks:distilling`

One craft of the distillation tower. Ours:

```json
{
  "type": "frostworks:distilling",
  "inputs": [ { "fluid": "frostworks:cold_gas" }, { "fluid": "minecraft:water" } ],
  "ice": true,
  "result": "frostworks:frosium",
  "yield": "energy"
}
```

- **`inputs`**: up to 4, each a fluid (`"fluid"`) or a fluid tag (`"tag"`). The first one drives the tower: it is taken
  at the tower's rate (`towerGasPerLayer` a layer at `towerReferenceRpm`).
- **`ice`**: whether it uses ice (any item of the data map `frostworks:ice_value`, counted in packed ice).
- **`result`**: the fluid made.
- **`yield`**, two kinds:
  - `"energy"`: the product holds the energy of the gas and of the stress, and the expander gives it back. Only for
    cold gas into one of the mod's cold liquids (otherwise a data pack could make energy out of nothing). The other
    inputs, the ice and the shares are then the tower's settings (`towerWaterPerLiquid`, `towerGasPerLiquid`,
    `towerFrosiumPerPackedIce`, `towerGasShare`).
  - `{"per_input": x}`: x mB made per mB of the first input, no energy kept. Each input then gives its `share` of the
    room (equal shares if none) and its `per_output` (mB used per mB made, the first input aside), and
    `ice_per_output` the packed ice used per mB made.
- **What the tower makes**: empty, it takes the first input of any recipe; then only what, with what it holds, still
  makes a recipe. It makes the recipe it holds every input of, the one it was making first.

## Tags and data maps

| Name | Kind | What it does |
|---|---|---|
| `frostworks:cold_liquid` | fluid tag | Fluids the frost tank, the infuser's second tank and the expander take. **Do not add fluids lightly**: the expander turns them into energy at Frosium's rate. A fluid only for the vents needs a cold fluid recipe, not this tag. |
| `frostworks:cold_walls` | block tag | Blocks that close a cold room as cold walls do. |
| `frostworks:overspeed_machines` | block tag | Machines that may overspeed in a cold room. |
| `frostworks:overspeed_transmission` | block tag | Shafts, cogwheels and such that may carry the overspeed. |
| `frostworks:never_preserved` | item tag | Foods the infuser never preserves: dried kelp (Minecraft's and TFC's), kept for the cold dried kelp craft. |
| `frostworks:fans`, `frostworks:pumps` | item tags | The fan and the pump used in the TerraFirmaGreg crafts of the vent and the compressor. |
| `frostworks:ice_value` | item data map | What an item is worth to the tower, in packed ice: ice 1/9, packed ice 1, blue ice 9. Another mod adds its own: `{"values": {"mymod:dry_ice": {"value": 2.5}}}` in `data/<namespace>/data_maps/item/ice_value.json` (the same file is read on Forge 1.20.1, which has no data maps). |
