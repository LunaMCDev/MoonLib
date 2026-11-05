/**
 * Annotation-driven commands: write a command tree as plain methods and let
 * {@link com.lunamcdev.moonlib.command.annotation.AnnotatedCommands} build the
 * {@link com.lunamcdev.moonlib.command.MoonCommand}s, arguments, permissions, cooldowns and tab
 * completion from the annotations.
 *
 * <p>Annotated commands and hand-written {@code MoonCommand}s produce exactly the same objects, so
 * they share help pages, messages, usage lines and the per-plugin registry, and can be mixed
 * freely. See {@code docs/commands.md}.</p>
 *
 * @since 1.2.0
 */
package com.lunamcdev.moonlib.command.annotation;
