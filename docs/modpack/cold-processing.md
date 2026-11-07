# Cold processing (`frostworks:cold_process`)

Since 1.1, a machine working in a **closed cold room** can give other results than its normal recipe. What it gives,
and from which temperature, is set by data files: one file = one process, one recipe (or all of them), a mode,
ingredients and temperature steps. A pack can add, replace or remove them with a data pack, like any recipe.

The files go in `data/<namespace>/recipe/cold_process/` in 1.21.1 (`recipes/` in 1.20.1). The mod's Forge jar already
holds the 1.20.1 version of ours.

What a vent does with a fluid (temperature, consumption, coverage) is another recipe type: see
[Cold fluids](cold-fluids.md). Two complete files are at the end of the rules: [Two complete examples](#two-complete-examples).

## The format

Every machine reads the same structure: `process`, then `recipe`, `mode`, `ingredients`, `steps`.

```json
{
  "type": "frostworks:cold_process",
  "process": "create:crushing",
  "recipe": "",
  "mode": "bonus",
  "ingredients": [ { "tag": "c:ores", "chilled": true }, { "tag": "c:raw_materials", "chilled": true } ],
  "steps": [
    { "results": [ { "copy": "main", "count": 1, "chance": 0.15 } ] },
    { "temperature": -10, "results": [ { "copy": "main", "count": 1, "chance": 0.25 } ] },
    { "temperature": -20, "results": [ { "copy": "main", "count": 1, "chance": 0.35 } ] }
  ]
}
```

This is our file `frostworks:cold_process/chilled_crushing`: any chilled ore gives one more crushed ore 15 % of the
time anywhere, 25 % at −10 °C, 35 % at −20 °C (`c:` is `forge:` in the Forge jar). `"recipe": ""`: every crushing
recipe these ores go through.

| Key | What it means |
|---|---|
| `type` | Always `frostworks:cold_process`. |
| `process` | The machine's recipe type. For now: `create:crushing` (crushing wheels), `create:milling` (millstone, and crushing wheels when they take a milling recipe), the basin's `create:mixing` (mixer), `create:compacting` (press over a basin) and `create:basin`; with Greate (Forge 1.20.1 only): `greate:milling` and `greate:crushing` (crushing wheels and millstones), `greate:pressing` (press, on the ground and on a belt, out of a basin), `greate:splashing` and `greate:haunting` (fans, an item on the ground), `greate:cutting` (saw), and its basin's `greate:mixing`, `greate:compacting`, `greate:brewing`, `greate:basin`. Any other process is refused ("not supported yet"). |
| `recipe` | **One recipe** of that process, by its id: `create:mixing/brass_ingot`. **Left empty (`""`): every recipe of the process.** See "One recipe, or all of them" below. |
| `mode` | `replace` or `bonus` (see below). |
| `ingredients` | A list of ingredients, items or tags, in the recipe format of the version (`{"item": …}`, `{"tag": …}`); at the basin, fluids too. One entry must take one of the items or fluids the craft uses. Empty (`[]`): any input. |
| `chilled` | On an item ingredient, optional, `false` by default. `true`: that entry only takes a **chilled** stack (marked by the cryogenic infuser, see below). A fluid cannot be chilled. Written at the top of the file (as early 1.1 did), it is refused with a message. |
| `steps` | The steps: a `temperature` (optional for one of them, see "The step that applies anywhere") and its `results`. At least one. |
| `base` | Reserved (a recipe that would only exist in the cold). Read, but the file is refused: "not supported yet". |

### One recipe, or all of them

- **`"recipe": "create:mixing/brass_ingot"`**: the file only applies to that recipe. It must exist in the process,
  or the file is left out with a message ("there is no create:mixing recipe …"). At the basin this is the clearest: a
  mixing is a craft of its own. The ingredients can then stay empty.
- **`"recipe": ""`** (or the key left out): every recipe of the process. With ingredients, those they go through (our
  chilled ores). **Without ingredients, really all of them**: a pack can give a waste, a pollutant, to every mixing in
  a cold room this way. The mod ships no such file.
- Write the key in every file, even empty: the structure is the same on every machine.

### The results of a step

Several kinds, mixed freely in one step:

- **an item, as Create writes it** in its recipes: `{"id": "minecraft:iron_nugget", "count": 3, "chance": 0.5}`
  (`item` instead of `id` in 1.20.1; the Forge jar converts our files). Without `chance` it always drops; with one, it
  is rolled on its own.
- **a fluid, at the basin only**: `{"id": "minecraft:water", "amount": 250, "chance": 0.5}`. **`amount`, in mB,
  replaces `count`** (both together are refused); `fluid` instead of `id` in 1.20.1. Without `chance` it always comes
  out. Anywhere but the basin, a fluid is refused with a message.
- **a copy of the main result** of the normal recipe (its first result): `{"copy": "main", "count": 1, "chance": 0.25}`.
  Handy with a tag: one file covers dozens of ores, each gets a copy of *its own* result. At the basin,
  `{"copy": "main", "amount": 250}` copies the recipe's first **fluid**, 250 mB.

`"replaces": "last"` (bonus mode only) leaves out the normal recipe's **last item result**: the bonus takes the place of
that roll instead of adding one. The last result is left out once, even if several files ask for it.

## The steps

- A temperature is a **whole number below 0** (°C). 0 and above are refused, and so are two steps at the same
  temperature.
- Of the steps at least as warm as the room, the room uses **the coldest**. With steps at −10 and −20: a room at −8 °C
  takes none (the normal recipe), at −10 or −15 °C the −10 step, at −20 °C or colder the −20 step.
- Only **closed rooms** count (states "cold" and "not enough power", at their real temperature). An open room does
  nothing: its cells reach outside.
- The temperature is read when the machine rolls its results.
- Our 1.1 files go no colder than −20 °C; a pack can go lower.

### The step that applies anywhere

A step **without `temperature`** (one per file) applies anywhere: out of any room, in an open room, in a closed room
warmer than every cold step. The cold steps take its place once reached (one step applies at a time).

```json
"steps": [
  { "results": [ { "copy": "main", "chance": 0.15 } ] },
  { "temperature": -10, "results": [ { "copy": "main", "chance": 0.25 } ] },
  { "temperature": -20, "results": [ { "copy": "main", "chance": 0.35 } ] }
]
```

One more ore 15 % of the time anywhere, 25 % from −10 to −19 °C, 35 % at −20 °C and colder.

## The two modes

**`replace`**: the step's results take the place of the normal recipe's; at the basin, its items **and** its fluids (a
step without a fluid removes the recipe's). A normal recipe must exist (the named recipe, or one for one of the
ingredients); otherwise the file is left out with a message. If two `replace` files aim at the same recipe or the same
item in the same process, the log names both and the first by id wins. A `replace` step that applies comes before
everything, and the bonuses' `"replaces": "last"` is then ignored (the recipe's list is no longer there).

```json
{
  "type": "frostworks:cold_process",
  "process": "create:milling",
  "recipe": "",
  "mode": "replace",
  "ingredients": [ { "item": "minecraft:gravel" } ],
  "steps": [
    { "temperature": -5,  "results": [ { "id": "minecraft:flint" }, { "id": "minecraft:sand", "chance": 0.5 } ] },
    { "temperature": -15, "results": [ { "id": "minecraft:flint", "count": 2 }, { "id": "minecraft:snowball", "chance": 0.25 } ] }
  ]
}
```

Above −5 °C, gravel mills as usual; from −5 to −14 °C it gives a flint and a chance of sand; at −15 °C and colder, two
flints and a chance of a snowball.

**`bonus`**: the step's results are added to the recipe (the named recipe, or any recipe of the process the ingredient
goes through). Several bonus files that take the same craft **add up**. The example at the top (the chilled ores) is a
bonus.

## The basin (mixing, compacting)

The mixer and the press over a basin read the cold processing files of `create:mixing`, `create:compacting` and
`create:basin` (both versions), and Greate's basin reads its own (Forge 1.20.1). The mod ships none: they are for packs
to write.

- **Aim at a craft**: `"recipe": "create:mixing/brass_ingot"`. The simplest, with no ingredients.
- **Aim by ingredient**: the file applies if one of the items or fluids **the craft uses** is in its ingredients (a
  chilled item if the entry asks for it).
- **Fluid ingredients** are written as in recipes: `{"type": "neoforge:single", "fluid": "minecraft:water"}` or
  `{"type": "neoforge:tag", "tag": "c:milk"}` (1.20.1: `{"fluid": …}`, `{"fluidTag": …}`). Their amount is not read:
  the recipe decides how much it uses.
- **Fluid results**: with `amount` instead of `count` (see "The results of a step"). They go into the basin's output
  tank with the recipe's.
- **Room**: the basin has 9 output slots and 2 tanks. As in Create, a craft whose results (ours included) do not fit
  waits for room.
- **The temperature** is that of the **basin's** room, read when the basin rolls its results.
- **Not covered**: the crafting table recipes the mixer makes in a basin (shaped or shapeless, they are not basin
  recipes). Create's brewing (its potions) has no recipe id: only a `create:mixing` file without `recipe` touches it.
- **A fan over a belt** has no cold processing, at Create's fans or Greate's.

At start-up the log should say `Create hook ready: cold processing at the basin (mixing, compacting)` (and with Greate,
`… at Greate's basin (mixing, compacting, brewing)`). "missing": the basin keeps its normal recipes.

## Two complete examples

### Example 1: crushing, every recipe (1.21.1, NeoForge)

```json
{
  "type": "frostworks:cold_process",
  "process": "create:crushing",
  "recipe": "",
  "mode": "bonus",
  "ingredients": [
    { "item": "minecraft:iron_ore", "chilled": true },
    { "tag": "c:raw_materials", "chilled": true }
  ],
  "steps": [
    { "results": [ { "copy": "main", "count": 1, "chance": 0.15 } ] },
    {
      "temperature": -10,
      "results": [
        { "copy": "main", "count": 1, "chance": 0.25 },
        { "id": "minecraft:iron_nugget", "count": 3, "chance": 0.5 }
      ]
    },
    { "temperature": -20, "results": [ { "copy": "main", "count": 1, "chance": 0.35, "replaces": "last" } ] }
  ]
}
```

**What it does.** A chilled iron ore, or a chilled raw material, crushed by Create's crushing wheels: one more crushed
ore 15 % of the time anywhere; at −10 °C, 25 %, plus a 50 % chance of 3 iron nuggets; at −20 °C, 35 %, in place of the
recipe's last roll. `"recipe": ""`: every crushing recipe these items go through.

### Example 2: the basin, one recipe, with a fluid (1.21.1, NeoForge)

```json
{
  "type": "frostworks:cold_process",
  "process": "create:mixing",
  "recipe": "create:mixing/brass_ingot",
  "mode": "bonus",
  "ingredients": [],
  "steps": [
    { "temperature": -10, "results": [ { "id": "create:copper_nugget", "count": 2, "chance": 0.5 } ] },
    {
      "temperature": -20,
      "results": [
        { "copy": "main", "count": 1, "chance": 0.25 },
        { "id": "minecraft:water", "amount": 100, "chance": 0.5 }
      ]
    }
  ]
}
```

**What it does.** Brass mixed in a basin in a closed cold room: at −10 °C, a 50 % chance of 2 copper nuggets; at
−20 °C, a 25 % chance of one more brass ingot and a 50 % chance of 100 mB of water in the basin. Nothing out of a cold
room (no step without a temperature). No ingredients: the recipe alone chooses.

### The same files, 1.20.1 (Forge)

Three differences: `forge:` tags instead of `c:`, `item` instead of `id` for an item result, `fluid` instead of `id`
for a fluid result. Example 1:

```json
{
  "type": "frostworks:cold_process",
  "process": "create:crushing",
  "recipe": "",
  "mode": "bonus",
  "ingredients": [
    { "item": "minecraft:iron_ore", "chilled": true },
    { "tag": "forge:raw_materials", "chilled": true }
  ],
  "steps": [
    { "results": [ { "copy": "main", "count": 1, "chance": 0.15 } ] },
    {
      "temperature": -10,
      "results": [
        { "copy": "main", "count": 1, "chance": 0.25 },
        { "item": "minecraft:iron_nugget", "count": 3, "chance": 0.5 }
      ]
    },
    { "temperature": -20, "results": [ { "copy": "main", "count": 1, "chance": 0.35, "replaces": "last" } ] }
  ]
}
```

Example 2's fluid result, in 1.20.1: `{ "fluid": "minecraft:water", "amount": 100, "chance": 0.5 }`.

## Errors

A broken file is left out alone; the rest goes on. The server log says why, in one line:

```
Cold processing recipe mypack:gravel_cold left out: step temperature 0: a cold step must be below 0 deg C
```

After each `/reload`, the files are read again. The line `Cold processing: N recipe(s) read` gives the count. The
server setting `coldProcessingEnabled` (section `processing.coldProcessing`) turns everything off at once.

## In JEI and EMI

The "Cold Processing" category shows one file per page: its ingredients (the items, and at the basin the fluids under
them), what it does, the recipe it aims at if it names one ("Recipe: …"), then one step per row (warmest first, the step
that applies anywhere first, marked "Anywhere") with its results, items and fluids, and their chances. A copy of the
main result is shown as that result, in step with the ingredient shown. The basin, the mixer and the press are among
its catalysts, with the crushing wheels and the millstone. EMI shows it too when it shows JEI's plugins (TerraFirmaGreg
does). A page shows three steps and six results per step at most; beyond that, it says so.

## With Greate

- `greate:milling`, `greate:crushing`, `greate:pressing`, `greate:splashing` and `greate:haunting` (its fans, an item on
  the ground only: on a belt, no cold processing), `greate:cutting` (its saw) and its basin (`greate:mixing`,
  `greate:compacting`, `greate:brewing`, `greate:basin`) aim at Greate's machines (Forge 1.20.1). The mod ships no file
  for the fans, the saw, the press or the basin: they read a pack's. Its crushing wheels and millstones only take
  `greate:` recipes: a `create:crushing` file does not touch them.
- Greate keeps its tier rule for **its** results (a result kept for a higher tier does not drop on a weaker machine).
  Ours are added **after**, without that rule: they drop whatever the machine's tier. Its basin rolls its results as
  Create does, without that rule.
- Greate's machines have 15 output slots. Identical results stack together (a copy of the main result joins its stack);
  only more than 15 **different** results would lose the surplus.
- At start-up the log should say `Create hook ready: cold processing at Greate's crushing wheels` (and the millstone,
  the press, the fans, the saw, the roll and the basin). A "missing" line means another version of Greate changed the
  call: its machines then keep their normal recipes, nothing else changes.

## The chilled state on any item

- The cryogenic infuser can **mark** a stack, without making a new item: a pack's Raw Silver stays the pack's Raw
  Silver, with a mark (NBT `frostworks:chilled` in 1.20.1, component `frostworks:chilled` in 1.21.1:
  `"components": { "frostworks:chilled": {} }` in a result). The pack's recipes still take it; placed, a block loses
  the mark.
- **The infusing recipe** that marks is a `frostworks:infusing` recipe **without a result** (`"results": []`): it gives
  back the same item, marked. One recipe on a tag covers every ore of a pack. A stack already marked is not infused
  again. It uses Frosium, the amount of the setting `chilledInfusionFrosium`.
- **An ingredient** of a cold processing file with `"chilled": true` (`{"tag": …, "chilled": true}`) only takes marked
  stacks. At the basin, one marked item among those the craft uses is enough for that entry.
- **The look**: frost drawn over the item (a cube for a block, a flat picture for an item), wherever it is drawn:
  inventory, ground, belt, depot, hand. A "Chilled" line in the tooltip, with the crushing bonus. Both versions.
- **Shipped with the mod** (both versions): the infusion that marks `#c:ores` and `#c:raw_materials` (`forge:` in
  1.20.1, 100 mB of Frosium), and the bonus at the top (ingredients `"chilled": true`) on Create's crushing and milling
  (`create:crushing`, `create:milling`); with Greate (Forge 1.20.1), the same on its crushers (`greate:milling`,
  `greate:crushing`). The other machines read cold processing files, but the mod gives them none.
- **The chilled ores of 1.0** (`frostworks:chilled_*`, 20 blocks and 4 raw materials) stay in the game for saved
  worlds, hidden (creative menu, JEI, EMI). They turn into the base ore, marked: in a player's inventory (on their own),
  through a shapeless crafting recipe, and a placed block drops the marked ore. They will go in a later version.
