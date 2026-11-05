/**
 * Per-player state: {@link dev.lunamc.moonlib.player.PlayerCache} loads each player's data
 * before they join, keeps it in memory while they play and saves it safely when they leave, on a
 * timer and on shutdown. See {@code docs/player-data.md}.
 *
 * @since 1.4.0
 */
package dev.lunamc.moonlib.player;
