/**
 * Structure for large plugins: {@link dev.lunamc.moonlib.module.MoonModule}s with their own
 * lifecycle and automatic cleanup, a {@link dev.lunamc.moonlib.module.ModuleManager} that orders
 * them by dependency, isolates failures and lets server owners toggle them, and a
 * {@link dev.lunamc.moonlib.module.Services} registry so modules share interfaces rather than
 * internals. See {@code docs/modules.md}.
 *
 * @since 1.4.0
 */
package dev.lunamc.moonlib.module;
