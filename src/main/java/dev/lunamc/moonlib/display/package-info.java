/**
 * On-screen and in-world displays that work across versions:
 * {@link dev.lunamc.moonlib.display.Bar} boss bars (1.9+), the
 * {@link dev.lunamc.moonlib.display.TabList} header, footer and names, and
 * {@link dev.lunamc.moonlib.display.Hologram}s (text displays on 1.19.4+, armor stands before).
 * Everything created through these classes is removed when the owning plugin shuts down MoonLib.
 * See {@code docs/displays.md}. Sidebars live in {@link dev.lunamc.moonlib.scoreboard}.
 *
 * @since 1.1.1-alpha
 */
package dev.lunamc.moonlib.display;
