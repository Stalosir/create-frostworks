# Food preservation (`frostworks:food_preserving`)

Since 1.1, with TerraFirmaCraft, the cryogenic infuser can **cryo-preserve** a food. It gives it, for good, a
TerraFirmaCraft preservation trait that makes it last longer. Without TFC, nothing: food does not rot, and our recipes do
not load (condition `tfc`).

The files go in `data/<namespace>/recipe/food_preserving/` in 1.21.1 (`recipes/` in 1.20.1). The mod's Forge jar
already holds the 1.20.1 version of ours.

## Ours

| File | Fluid | Level | Trait | Lasts (default) |
|---|---|---|---|---|
| `frostworks:food_preserving/cold_gas` | `frostworks:cold_gas`, 50 mB per item | 1 | `frostworks:cold_preserved`, "Cryo-preserved (cold gas)" | ×2.5 (modifier 0.4) |
| `frostworks:food_preserving/frosium` | `frostworks:frosium`, 50 mB per item | 2 | `frostworks:deep_cold_preserved`, "Cryo-preserved (Frosium)" | ×4 (modifier 0.25) |

**The values** are in the server config, synced to the clients:
- `integrations.tfcFood.coldPreservedModifier` (0.4);
- `integrations.tfcFood.deepColdPreservedModifier` (0.25);
- from 0.01 to 1.

While the config is not read yet (menus, JEI before entering a world), the default applies.

## The format

```json
{
  "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "tfc" } ],
  "type": "frostworks:food_preserving",
  "ingredients": [
    { "type": "frostworks:perishable_food" },
    { "type": "neoforge:single", "amount": 50, "fluid": "frostworks:cold_gas" }
  ],
  "results": [],
  "level": 1
}
```

| Key | What it means |
|---|---|
| `ingredients[0]` | Always `{"type": "frostworks:perishable_food"}`: **any TerraFirmaCraft food that can rot**, known by its food capability (not by a tag). The dishes of TFC, Firmalife and TFG are. |
| `ingredients[1]` | **One** fluid, `{"type": "neoforge:single", "fluid": …, "amount": …}` (in 1.20.1: `{"fluid": …, "amount": …}`). The amount is used **per item**. A gas (such as cold gas) goes in the infuser's first tank; a cold liquid (`#frostworks:cold_liquid`, such as Frosium) in the second. A fluid neither tank takes makes the recipe unusable. |
| `level` | The level given, **1 or 2**. The recipe says it, not the fluid. |
| `neoforge:conditions` | Put the `tfc` condition: without TFC there is no food and no trait. In 1.20.1, `"conditions": [{"type": "forge:mod_loaded", "modid": "tfc"}]`. |

`"results": []` is required and stays empty (Create reads that key; without it, the recipe does not load): the output is
the same food, one at a time, with the trait.

## The rules

- **Refused**:
  - an item that is not a TFC food;
  - **dried kelp, always** (Minecraft's `minecraft:dried_kelp` and TFC's `tfc:food/dried_kelp`): it is never preserved,
    because the infuser uses it to craft cold dried kelp (for the strip curtain door). It is in the item tag
    `frostworks:never_preserved`; a pack can add other foods to that tag;
  - a rotten food, or one that does not rot (modifier 0, JEI's example stacks);
  - a food already at this level, or above.
- **One level replaces the other**: Frosium on a food done with gas takes off the level 1 trait and gives the level 2
  one. The other way round is refused.
- **With other mods' traits, TFC's rule**: they multiply (salted, smoked, Firmalife's cellar shelf, TFG's fridge,
  freeze-dried…). TFC has no exclusion between traits.
- **No renewal**: TFC keeps the share of life already used (`applyTrait`). A half-old food stays half old; it is the
  time left that lasts longer.
- **The date is rounded** after the trait is given, as TFG and Firmalife do, so that foods infused a few hours apart
  stack. A stack with our trait does not stack with the same food without it, as with any trait.
- **Changing a value** in the config at once changes the expiry date of foods already preserved: TFC reads the trait's
  value at each calculation.
- **An infusing recipe first**: if a pack writes a `frostworks:infusing` recipe that takes a food, the infuser makes that
  one.
- **JEI** shows the "Preserving" category: TFC foods as examples, the fluid, the output with its trait and the time
  gained ("Lasts ×2.5").

## Example: a future fluid

A pack that wants another mod's liquid helium (`examplemod:liquid_helium`, put in `#frostworks:cold_liquid` so that the
infuser takes it in its second tank) to preserve at level 2, with less fluid:

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "tfc" },
    { "type": "neoforge:mod_loaded", "modid": "examplemod" }
  ],
  "type": "frostworks:food_preserving",
  "ingredients": [
    { "type": "frostworks:perishable_food" },
    { "type": "neoforge:single", "amount": 25, "fluid": "examplemod:liquid_helium" }
  ],
  "results": [],
  "level": 2
}
```

There are only two levels, so two traits: a future fluid gives one or the other. A stronger trait needs one more level
in the mod.
