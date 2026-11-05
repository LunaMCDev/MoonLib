/**
 * On-screen and in-world displays that work across versions:
 * {@link com.lunamcdev.moonlib.display.Bar} boss bars (1.9+), the
 * {@link com.lunamcdev.moonlib.display.TabList} header, footer and names, and
 * {@link com.lunamcdev.moonlib.display.Hologram}s (text displays on 1.19.4+, armor stands before).
 * Everything created through these classes is removed when the owning plugin shuts down MoonLib.
 * See {@code docs/displays.md}. Sidebars live in {@link com.lunamcdev.moonlib.scoreboard}.
 *
 * @since 1.2.0
 */
package com.lunamcdev.moonlib.display;
