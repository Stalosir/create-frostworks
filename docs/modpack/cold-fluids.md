# Cold fluids (`frostworks:cold_fluid`)

Since 1.1, what a vent does with a fluid is set by recipes: which fluid it takes, how much it uses, how many cells it
covers and the temperature it brings a room to. A pack can add, replace or remove them with a data pack, like any
recipe.

The files go in `data/<namespace>/recipe/cold_fluid/` in 1.21.1 (`recipes/` in 1.20.1). The mod's Forge jar already
holds the 1.20.1 version of ours.

## Ours

| File | Fluid | Temperature | Use | Coverage |
|---|---|---|---|---|
| `frostworks:cold_fluid/cold_gas` | `frostworks:cold_gas` | −10 °C | 15 mB/s | 24 cells |
| `frostworks:cold_fluid/frosium` | `#frostworks:cold_liquid` | −20 °C | 4 mB/s | 64 cells |
| `frostworks:cold_fluid/create_nuclear` | `#createnuclear:nitrogen` | −20 °C | 4 mB/s | 64 cells |

The `create_nuclear` recipe lets Create Nuclear's nitrogen (its tag `#createnuclear:nitrogen`) cool our vents. Our
Frosium is no longer in that tag since 1.1. Without Create Nuclear the tag is empty: the recipe does nothing and has no
JEI page.

## The format

```json
{
  "type": "frostworks:cold_fluid",
  "ingredients": [ { "type": "neoforge:single", "fluid": "frostworks:cold_gas", "amount": 15 } ],
  "temperature": -10,
  "coverage": 24
}
```

| Key | What it means |
|---|---|
| `ingredients` | **One** fluid ingredient: `{"type": "neoforge:single", "fluid": …, "amount": …}` or `{"type": "neoforge:tag", "tag": …, "amount": …}`. In 1.20.1: `{"fluid": …, "amount": …}` or `{"fluidTag": …, "amount": …}` (Create's form). |
| `amount` | What the vent uses, in **mB per second**, while it blows. |
| `temperature` | The target of a room cooled by this fluid, in °C: a **whole number below 0**. |
| `coverage` | The cells **one** vent covers with this fluid. A room is cold when the coverage of its active vents reaches its volume. |

## The rules

- **A vent only takes a fluid that has a recipe.** A pipe cannot push anything else into it.
- **Two recipes for the same fluid**: the coldest applies.
- **A room fed with several fluids takes the temperature of the warmest** of its active vents. To reach a step, switch
  every vent to the colder fluid, not just one.
- **The overspeed** goes by steps on that temperature: the bonus `overspeedBonusPercent` in a cold room, the bonus
  `overspeedBonusPercentFrosium` at −20 °C and colder.
- **A `/reload`** reads the recipes again. A vent that keeps a fluid whose recipe is gone stops using it and stops
  cooling; it starts again if the recipe comes back.
- **Body temperature** (Cold Sweat, Tough As Nails, Minecraft's frost) reads the room's real temperature, not the
  recipe: it follows a fluid at −15 °C as one at −20 °C. Their thresholds are in the server config
  (`coldExposureTemperature`, `toughAsNailsCoolerAt`, `toughAsNailsColdAt`), "at this threshold or below": with their
  defaults, a room at exactly −15 °C freezes the player, one at −14 °C does not. With TFC Ambiental (TerraFirmaGreg),
  Minecraft's frost is not given: TFC Ambiental has its own ([TFC Ambiental](tfc-ambiental.md)).

## Example: a fluid at −30 °C

A pack that wants another mod's liquid helium to cool harder:

```json
{
  "type": "frostworks:cold_fluid",
  "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "mymod" } ],
  "ingredients": [ { "type": "neoforge:single", "fluid": "mymod:liquid_helium", "amount": 2 } ],
  "temperature": -30,
  "coverage": 96
}
```

In 1.20.1 (Forge), the same file is:

```json
{
  "type": "frostworks:cold_fluid",
  "conditions": [ { "type": "forge:mod_loaded", "modid": "mymod" } ],
  "ingredients": [ { "fluid": "mymod:liquid_helium", "amount": 2 } ],
  "temperature": -30,
  "coverage": 96
}
```

A room whose active vents all blow this helium goes down to −30 °C, and keeps the −20 °C overspeed bonus there (there
is no −30 step yet). With one Frosium vent next to them, it stays at −20 °C. To replace one of our recipes, a data
pack puts a file of the same name (`data/frostworks/recipe/cold_fluid/…`).

## Example: GregTech's liquid air (TerraFirmaGreg)

The mod ships **no** recipe for GregTech's liquid air (`gtceu:liquid_air`): it comes at the HV to EV age (vacuum
freezer), long after ours, and is worth more distilled (nitrogen, oxygen, helium, argon). A pack that wants it in the
vents anyway can copy this file, set like our Frosium (−20 °C, 4 mB/s, 64 cells): it opens no new step.

```json
{
  "type": "frostworks:cold_fluid",
  "neoforge:conditions": [ { "type": "frostworks:tfg_compat" } ],
  "ingredients": [ { "type": "neoforge:single", "fluid": "gtceu:liquid_air", "amount": 4 } ],
  "temperature": -20,
  "coverage": 64
}
```

In 1.20.1: `"conditions": [ { "type": "frostworks:tfg_compat" } ]` and `{ "fluid": "gtceu:liquid_air", "amount": 4 }`.

**Never add it to the tag `#frostworks:cold_liquid`.** That tag is not only for the vents: the frost tank and the
infuser take it, and the expander turns it into energy at Frosium's rate. A GregTech fluid would become a source of
energy nobody planned. The `cold_fluid` recipe alone only touches the vents.

## Errors

A refused file is left out alone; the server log says why, in one line:

```
Cold fluid recipe mypack:helium left out: temperature 5: a cold fluid must be below 0 deg C
```

Refused: a temperature that is not a whole negative number, an amount or a coverage not above 0, anything but one fluid
ingredient (several ingredients, an item, another form), an unknown fluid. The line `Cold fluids: N recipe(s) read`
gives the count after each load.

## In JEI and EMI, and with the goggles

- **JEI/EMI**, category "Cold Fluids": one page per recipe, the fluid (every fluid of the tag), the temperature
  reached, the coverage per vent, the use. The vent is its catalyst: its uses and the fluid's show the page.
- **Goggles** on a vent: the fluid it holds, by name, the temperature it cools to, its coverage; "Not a cold fluid" if
  it no longer has a recipe.
