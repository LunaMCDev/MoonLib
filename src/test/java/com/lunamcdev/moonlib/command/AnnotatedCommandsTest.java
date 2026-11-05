package com.lunamcdev.moonlib.command;

import com.lunamcdev.moonlib.command.annotation.AnnotatedCommands;
import com.lunamcdev.moonlib.command.annotation.Arg;
import com.lunamcdev.moonlib.command.annotation.Command;
import com.lunamcdev.moonlib.command.annotation.Execute;
import com.lunamcdev.moonlib.command.annotation.Greedy;
import com.lunamcdev.moonlib.command.annotation.Range;
import com.lunamcdev.moonlib.command.annotation.Subcommand;
import com.lunamcdev.moonlib.command.annotation.Suggest;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatedCommandsTest {

    enum Mode { EASY, HARD }

    @Command(value = "mine", aliases = "m", description = "Mines")
    static final class MineCommand {
        final List<String> calls = new ArrayList<>();

        @Execute
        String root(CommandSender sender) {
            calls.add("root");
            return "&aroot";
        }

        @Subcommand(value = "reset", aliases = "r", description = "Reset")
        void reset(CommandSender sender, @Suggest("names") @Arg("mine") String mine,
                   @Arg(value = "delay", def = "5") @Range(min = 0, max = 60) int delay) {
            calls.add("reset " + mine + " " + delay);
        }

        @Subcommand("admin setblocks")
        void setBlocks(CommandSender sender, @Arg("mine") String mine, @Greedy @Arg("blocks") String blocks) {
            calls.add("set " + mine + " [" + blocks + "]");
        }

        @Subcommand("mode")
        boolean mode(@Arg("mode") Mode mode) {
            calls.add("mode " + mode);
            return mode == Mode.EASY;
        }

        List<String> names(CommandSender sender) {
            return Arrays.asList("a", "b", "bee");
        }
    }

    @Command("broken")
    static final class OptionalPrimitive {
        @Execute
        void run(@Arg(value = "n", optional = true) int n) {
        }
    }

    @Command("broken2")
    static final class MissingSuggester {
        @Execute
        void run(@Suggest("nope") @Arg("x") String x) {
        }
    }

    private static CommandSender sender(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "sendMessage":
                            messages.add(String.valueOf(a[0]));
                            return null;
                        case "hasPermission":
                            return true;
                        case "getName":
                            return "Console";
                        default:
                            return null;
                    }
                });
    }

    @Test
    void buildsTreeAndDispatches() {
        MineCommand handler = new MineCommand();
        MoonCommand root = AnnotatedCommands.build(handler);
        List<String> messages = new ArrayList<>();
        CommandSender console = sender(messages);

        assertEquals("mine", root.name());
        assertEquals(Arrays.asList("m"), root.aliases());
        assertEquals(3, root.subcommands().size());

        root.dispatch(console, "mine", new String[0]);
        root.dispatch(console, "mine", new String[]{"reset", "a"});
        root.dispatch(console, "mine", new String[]{"r", "b", "30"});
        root.dispatch(console, "mine", new String[]{"admin", "setblocks", "a", "50%stone", "50%dirt"});
        root.dispatch(console, "mine", new String[]{"mode", "easy"});
        assertEquals(Arrays.asList("root", "reset a 5", "reset b 30", "set a [50%stone 50%dirt]", "mode EASY"),
                handler.calls);
        assertTrue(messages.get(0).endsWith("root"), "returned String is sent");

        int before = messages.size();
        root.dispatch(console, "mine", new String[]{"reset", "a", "99"});
        assertEquals(5, handler.calls.size());
        assertTrue(messages.size() > before, "out-of-range number is rejected with a message");

        before = messages.size();
        root.dispatch(console, "mine", new String[]{"mode", "hard"});
        assertTrue(messages.get(before).contains("/mine mode <mode>"), "false return shows usage: " + messages);
    }

    @Test
    void suggestsFromHandlerMethod() {
        MoonCommand root = AnnotatedCommands.build(new MineCommand());
        List<String> out = root.complete(sender(new ArrayList<>()), "mine", new String[]{"reset", "b"});
        assertEquals(Arrays.asList("b", "bee"), out);
        assertEquals(Arrays.asList("reset"), root.complete(sender(new ArrayList<>()), "mine", new String[]{"re"}));
    }

    @Test
    void reportsMistakesWhenBuilt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AnnotatedCommands.build(new OptionalPrimitive()));
        assertTrue(e.getMessage().contains("OptionalPrimitive.run()"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> AnnotatedCommands.build(new MissingSuggester()));
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AnnotatedCommands.build(new Object()));
    }
}
