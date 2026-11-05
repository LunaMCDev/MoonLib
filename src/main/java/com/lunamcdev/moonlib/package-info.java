/**
 * MoonLib core: {@link com.lunamcdev.moonlib.MoonPlugin}, the recommended base class for plugins, and
 * {@link com.lunamcdev.moonlib.MoonLib}, which owns the library lifecycle.
 *
 * <h2>Package overview</h2>
 * <ul>
 *     <li>{@link com.lunamcdev.moonlib.command} - commands, subcommands, typed arguments, tab completion</li>
 *     <li>{@link com.lunamcdev.moonlib.command.annotation} - annotation-driven commands</li>
 *     <li>{@link com.lunamcdev.moonlib.menu} - inventory GUIs, pagination, confirmation dialogs</li>
 *     <li>{@link com.lunamcdev.moonlib.scoreboard} - flicker-free sidebars</li>
 *     <li>{@link com.lunamcdev.moonlib.display} - boss bars, tab list, holograms</li>
 *     <li>{@link com.lunamcdev.moonlib.database} - SQL storage, pooling, transactions, migrations</li>
 *     <li>{@link com.lunamcdev.moonlib.player} - per-player data loaded before join and saved safely</li>
 *     <li>{@link com.lunamcdev.moonlib.module} - toggleable feature modules and services</li>
 *     <li>{@link com.lunamcdev.moonlib.version} - version detection and cross-version materials, sounds, enchantments</li>
 *     <li>{@link com.lunamcdev.moonlib.scheduler} - a Bukkit/Folia agnostic scheduler</li>
 *     <li>{@link com.lunamcdev.moonlib.item} - item builder, skulls, persistent tags, serialisation</li>
 *     <li>{@link com.lunamcdev.moonlib.config} - YAML and JSON files, object mapping, translatable messages</li>
 *     <li>{@link com.lunamcdev.moonlib.event} - functional event listeners</li>
 *     <li>{@link com.lunamcdev.moonlib.input} - chat prompts</li>
 *     <li>{@link com.lunamcdev.moonlib.hook} - PlaceholderAPI and Vault soft hooks</li>
 *     <li>{@link com.lunamcdev.moonlib.integration} - detect other plugins and link up with them</li>
 *     <li>{@link com.lunamcdev.moonlib.util} - text and rich text, time, numbers, cooldowns, locations, regions, players,
 *     file watching, update checks</li>
 * </ul>
 *
 * <p>Guides for every package are in the {@code docs/} folder of the repository; start with
 * {@code docs/getting-started.md}, or start from the MoonLib-Template repository for a ready development setup.</p>
 */
package com.lunamcdev.moonlib;
