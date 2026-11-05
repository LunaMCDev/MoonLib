# Rich text

*Since 1.1.1-alpha.* `RichText` formats MiniMessage-style tags into coloured text that works on **every**
server version. It needs no Adventure library and outputs plain strings, so it works with
`sendMessage`, scoreboards, item names, titles, boss bars and anything else that takes a string.

```java
player.sendMessage(RichText.parse("<gradient:#7F7FD5:#91EAE4><bold>MOON PRISON</bold></gradient>"));
player.sendMessage(RichText.parse("<gray>You have <gold><tokens></gold> tokens, <white><player></white>.",
        "tokens", Numbers.commas(tokens), "player", player.getName()));
```

## Tags

| Tag | Example | Notes |
|---|---|---|
| Named colours | `<red>`, `<dark_aqua>`, `<gray>`/`<grey>` | all 16 |
| Hex colours | `<#ff8800>`, `<color:#ff8800>`, `<c:gold>` | |
| Decorations | `<bold>`/`<b>`, `<italic>`/`<i>`/`<em>`, `<underlined>`/`<u>`, `<strikethrough>`/`<st>`, `<obfuscated>`/`<obf>` | `<!bold>` turns one off inside another |
| Gradient | `<gradient:#ff0000:#0000ff>`, `<gradient:red:gold:yellow>` | two or more stops; decorations inside are kept on every character |
| Rainbow | `<rainbow>`, `<rainbow:!>` (reversed), `<rainbow:3>` (phase) | |
| Reset | `<reset>`, `<r>` | closes everything that is open |
| Line break | `<newline>`, `<br>` | formatting restarts on the new line, as Minecraft requires |
| Placeholder | `<player>` | any name you pass to `parse` |

Close tags with `</tag>`. Closing a tag also closes the tags opened inside it, so
`<red><bold>A</red>B` gives a red bold `A` and a plain `B`. Unclosed tags run to the end.

## Things that never break

- **Unknown tags are printed as typed.** `I <3 you` and `<html>` stay readable, so you can run any
  player text through `parse` safely.
- **`\<` is a literal `<`.** `RichText.escape(text)` escapes a whole string.
- **Legacy codes keep working.** `&c`, `&l` and `&#rrggbb` can be mixed with tags, so existing
  configs can be migrated one line at a time.
- **Placeholder values are literal.** A player named `<red>Bob` or `&cBob` shows up exactly like
  that and cannot inject formatting. Wrap a value in `RichText.parsed(...)` when you *want* it
  formatted:

  ```java
  RichText.parse("<gray>Rank: <rank>", "rank", RichText.parsed(rank.displayTag()));   // e.g. "<gold>VIP"
  ```

## Versions

On 1.16+ colours are exact. Below 1.16 every colour, including each step of a gradient, becomes the
nearest of the 16 legacy colours, so one config looks right everywhere. Use `parseLegacy` to force
the 16-colour output on any version (useful where some clients do not render hex).

## In messages.yml

```yaml
rich-text: true
prefix: "<gradient:#7F7FD5:#91EAE4><bold>Moon</bold></gradient> <dark_gray>» <gray>"
rankup: "{prefix}<green>You ranked up to <gold>{rank}</gold>!"
```

`Messages` replaces `{placeholders}` first (escaping their values) and then formats the tags. See
[config-and-messages.md](config-and-messages.md).

## Other helpers

```java
RichText.strip("<red>Hi <b>there")   // "Hi there": what the player reads
RichText.hasTags(text)               // cheap check before parsing
RichText.parse(lines, "player", name) // a whole List<String>
```
