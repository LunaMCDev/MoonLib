/**
 * The command framework.
 *
 * <p>Extend {@link dev.lunamc.moonlib.command.MoonCommand} (or use
 * {@link dev.lunamc.moonlib.command.CommandBuilder}), declare arguments with
 * {@link dev.lunamc.moonlib.command.ArgumentTypes}, and register with
 * {@link dev.lunamc.moonlib.command.CommandRegistry}. Permissions, player-only checks, cooldowns,
 * usage messages, help pages and tab completion are handled for you. Fail from anywhere by throwing
 * {@link dev.lunamc.moonlib.command.CommandError}.</p>
 */
package dev.lunamc.moonlib.command;
