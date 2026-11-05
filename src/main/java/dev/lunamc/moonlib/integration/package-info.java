/**
 * Plugin-to-plugin integration.
 *
 * <ul>
 *     <li>{@link dev.lunamc.moonlib.integration.Integrations} and {@link dev.lunamc.moonlib.integration.Integration}
 *     detect another plugin and switch a link on and off as it enables and disables.</li>
 *     <li>{@link dev.lunamc.moonlib.integration.MoonLink} lets plugins built on MoonLib offer and call each other's
 *     features and publish events, with no API jar or compile-time dependency, even when every plugin has its
 *     own relocated copy of MoonLib.</li>
 * </ul>
 */
package dev.lunamc.moonlib.integration;
