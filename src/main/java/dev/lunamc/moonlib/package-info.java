/**
 * MoonLib core: {@link dev.lunamc.moonlib.MoonPlugin}, the recommended base class for plugins, and
 * {@link dev.lunamc.moonlib.MoonLib}, which owns the library lifecycle.
 *
 * <h2>Package overview</h2>
 * <ul>
 *     <li>{@link dev.lunamc.moonlib.command} - commands, subcommands, typed arguments, tab completion</li>
 *     <li>{@link dev.lunamc.moonlib.command.annotation} - annotation-driven commands</li>
 *     <li>{@link dev.lunamc.moonlib.menu} - inventory GUIs, pagination, confirmation dialogs</li>
 *     <li>{@link dev.lunamc.moonlib.scoreboard} - flicker-free sidebars</li>
 *     <li>{@link dev.lunamc.moonlib.display} - boss bars, tab list, holograms</li>
 *     <li>{@link dev.lunamc.moonlib.database} - SQL storage, pooling, transactions, migrations</li>
 *     <li>{@link dev.lunamc.moonlib.player} - per-player data loaded before join and saved safely</li>
 *     <li>{@link dev.lunamc.moonlib.module} - toggleable feature modules and services</li>
 *     <li>{@link dev.lunamc.moonlib.version} - version detection and cross-version materials, sounds, enchantments</li>
 *     <li>{@link dev.lunamc.moonlib.scheduler} - a Bukkit/Folia agnostic scheduler</li>
 *     <li>{@link dev.lunamc.moonlib.item} - item builder, skulls, persistent tags, serialisation</li>
 *     <li>{@link dev.lunamc.moonlib.config} - YAML and JSON files, object mapping, translatable messages</li>
 *     <li>{@link dev.lunamc.moonlib.event} - functional event listeners</li>
 *     <li>{@link dev.lunamc.moonlib.input} - chat prompts</li>
 *     <li>{@link dev.lunamc.moonlib.hook} - PlaceholderAPI and Vault soft hooks</li>
 *     <li>{@link dev.lunamc.moonlib.integration} - detect other plugins and link up with them</li>
 *     <li>{@link dev.lunamc.moonlib.util} - text and rich text, time, numbers, cooldowns, locations, regions, players,
 *     file watching, update checks</li>
 * </ul>
 *
 * <p>Guides for every package are in the {@code docs/} folder of the repository; start with
 * {@code docs/getting-started.md}, or copy {@code template-plugin/} for a ready development setup.</p>
 */
package dev.lunamc.moonlib;
