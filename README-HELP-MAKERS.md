# Create: Frostworks: help for modpack makers

Everything a modpack can change in Create: Frostworks, without code, is explained in [`docs/modpack/`](docs/modpack/).
This page says where to look.

| I want to… | Read |
|---|---|
| change a setting (server, common or client config) | [Configuration](docs/modpack/configuration.md) |
| know what the mod does with another mod, and which setting goes with it | [Integrations](docs/modpack/integrations.md) |
| see every recipe type, tag and data map | [Recipes](docs/modpack/recipes.md) |
| change what machines give in a cold room | [Cold processing](docs/modpack/cold-processing.md) |
| add a fluid that cools a room | [Cold fluids](docs/modpack/cold-fluids.md) |
| preserve TerraFirmaCraft food | [Food preservation](docs/modpack/food-preservation.md) |
| set the body temperature in TerraFirmaGreg | [TFC Ambiental](docs/modpack/tfc-ambiental.md) |
| check what changed before updating the mod in a pack | [Changelog](docs/release/changelog.md) |

## The guides

- [Configuration](docs/modpack/configuration.md): the three config files, every key, its default, its range and what it
  turns on, off or changes; and the tags and data map beyond the config.
- [Integrations](docs/modpack/integrations.md): mod by mod (Create, JEI and EMI, TerraFirmaGreg, Greate, TerraFirmaCraft,
  TFC Ambiental, Tough As Nails, Cold Sweat), what Frostworks does with it and the settings that go
  with it.
- [Recipes](docs/modpack/recipes.md): every recipe type at a glance (infusing, freezing, distilling, and the three
  below), the `frostworks:tfg_compat` condition, the tags and the ice values.
- [Cold processing](docs/modpack/cold-processing.md): `frostworks:cold_process`, other results in a cold room (steps,
  modes, one recipe or all of them, the basin, Greate, the chilled state), with two complete examples.
- [Cold fluids](docs/modpack/cold-fluids.md): `frostworks:cold_fluid`, what a vent takes and how cold it gets.
- [Food preservation](docs/modpack/food-preservation.md): `frostworks:food_preserving`, with TerraFirmaCraft.
- [TFC Ambiental](docs/modpack/tfc-ambiental.md): the cold rooms in TerraFirmaGreg's body temperature.
- [Changelog](docs/release/changelog.md): what each version changed. Worth a look before updating a pack: a data
  format can change between versions (in 1.1, `"chilled"` moved from the top of a cold processing file to each
  ingredient).

The mod's presentation for players is the [README](README.md).
