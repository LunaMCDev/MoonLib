# Scoreboards

Package: `com.lunamcdev.moonlib.scoreboard`

## One board

```java
Board board = Board.create(player);
board.title("&d&lMOON PRISON");
board.lines(
        "",
        "&fRank: &dA",
        "&fBalance: &a$1.2M",
        "",
        "&7moonprison.net");

board.line(2, "&fBalance: &a$1.3M");   // update one line
board.delete();                         // restores the main scoreboard
```

## A board for everyone (`BoardManager`)

```java
public final class PrisonBoard implements BoardProvider {
    @Override
    public String title(Player player) {
        return "&d&lMOON PRISON";
    }

    @Override
    public List<String> lines(Player player) {
        PrisonPlayer data = cache.get(player.getUniqueId());
        return Arrays.asList(
                "&7" + LocalDate.now(),
                "",
                "&fRank: &d" + data.rank(),
                "&fPrestige: &5" + Numbers.roman(data.prestige()),
                "&fBlocks: &e" + Numbers.commas(data.blocks()),
                "&fTokens: &b" + Numbers.compact(data.tokens()),
                "",
                "&dmoonprison.net");
    }
}

BoardManager boards = new BoardManager(new PrisonBoard(), 20)   // ticks between updates
        .usePlaceholderApi(true)                                 // optional
        .start();
```

The manager:

- creates a board for every online player and for each player who joins (one tick after joining,
  so other plugins setting scoreboards on join don't overwrite it),
- deletes boards on quit,
- updates every board on the timer; call `boards.update(player)` for an instant refresh after data
  changes,
- lets players toggle their board: `boards.setHidden(player, true)`,
- belongs to the plugin that created it and is stopped automatically when that plugin disables.

Only one plugin should give a player a sidebar at a time. If two do, the most recent one is shown.

Returning `null` from `lines(player)` hides that player's board until the next update returns lines.

Keep providers fast: they run on the main thread for every player at every update. Read cached
values; never hit a database inside them.

## Why it doesn't flicker

Each line is a fixed, invisible scoreboard entry whose visible text lives in a team's prefix and
suffix. Changing a line only re-sends that team, and only if the text actually changed. Scores are
re-sent only when the number of lines changes.

## Limits by version

| | 1.8 to 1.12 | 1.13+ |
|---|---|---|
| Lines | 15 | 15 |
| Characters per line (colour codes count) | 32 | 128 |
| Title length | 32 | 128 |
| Score numbers on the right | shown | hidden automatically on Paper 1.20.3+ |

Long lines are split across prefix and suffix and trimmed without ever cutting a colour code in
half; colours carry over from the prefix into the suffix.

## Accessing the Bukkit scoreboard

`board.scoreboard()` returns the player's `Scoreboard`, so you can add name-tag teams or below-name
objectives to the same board. Avoid team names starting with `moonlib_`.

## Folia

Folia does not implement the Bukkit scoreboard API. `BoardManager.start()` logs a warning and does
nothing there.
