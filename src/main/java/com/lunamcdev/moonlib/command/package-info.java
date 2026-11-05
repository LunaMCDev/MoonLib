/**
 * The command framework.
 *
 * <p>Extend {@link com.lunamcdev.moonlib.command.MoonCommand} (or use
 * {@link com.lunamcdev.moonlib.command.CommandBuilder}), declare arguments with
 * {@link com.lunamcdev.moonlib.command.ArgumentTypes}, and register with
 * {@link com.lunamcdev.moonlib.command.CommandRegistry}. Permissions, player-only checks, cooldowns,
 * usage messages, help pages and tab completion are handled for you. Fail from anywhere by throwing
 * {@link com.lunamcdev.moonlib.command.CommandError}.</p>
 */
package com.lunamcdev.moonlib.command;
