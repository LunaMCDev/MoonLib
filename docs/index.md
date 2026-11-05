# MoonLib

**A shadeable foundation for Bukkit, Spigot, Paper, Purpur and Folia plugins.** Shade it into every
plugin and skip straight to the parts that are actually yours.

| | |
|---|---|
| Minecraft | 1.8.8 → newest, including 26.x |
| Servers | CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia |
| Dependencies | none |
| Plugins per server | any number, relocated or shared |

## Start here

1. **[Getting started](getting-started.md)**: install MoonLib and write a first command, menu and
   sidebar.
2. **[Development workflow](dev-workflow.md)**: copy the template plugin and get a test server where
   code changes apply without restarting.
3. Pick the guides for what you are building, from the menu on the left.

## What is in the box

| Area | Highlights |
|---|---|
| [Commands](commands.md) | classes, builders or annotations; typed arguments, tab completion, cooldowns, help pages |
| [Menus](menus.md) | layouts, pagination, confirm dialogs, dupe-proof clicks |
| [Scoreboards](scoreboards.md) & [displays](displays.md) | flicker-free sidebars, boss bars, tab list, holograms |
| [Rich text](rich-text.md) | gradients, rainbow, hex and decorations, injection-safe placeholders, every version |
| [Files](data-files.md) & [config](config-and-messages.md) | YAML and JSON, your own classes, broken-file recovery, reload on save |
| [Database](database.md) | SQLite/MySQL with pooling, async, transactions and migrations, no dependency |
| [Player data](player-data.md) | loaded before join, saved safely, rejoin-proof |
| [Modules](modules.md) | toggleable features, dependency order, isolated failures |
| [Scheduler](scheduler.md) | one API for Bukkit and Folia |
| [Integrations](integrations.md) | plugins that find and use each other with no API jar |

## Reference

- [Cookbook](cookbook.md): complete recipes
- [Architecture](architecture.md): how it works inside
- [Compatibility & stability](compatibility.md): version support and what counts as API
- [FAQ](faq.md)
- [Javadoc](api/index.html)
