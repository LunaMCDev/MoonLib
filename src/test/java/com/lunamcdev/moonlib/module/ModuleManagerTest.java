package com.lunamcdev.moonlib.module;

import com.lunamcdev.moonlib.config.DataFile;
import com.lunamcdev.moonlib.config.JsonFile;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerTest {

    static final List<String> EVENTS = new ArrayList<>();

    interface Economy {
        int balance();
    }

    static final class EconomyModule extends MoonModule {
        EconomyModule() {
            super("economy");
        }

        @Override
        protected void enable() {
            EVENTS.add("enable economy");
            provide(Economy.class, () -> 100);
            onDisable(() -> EVENTS.add("cleanup economy"));
        }

        @Override
        protected void disable() {
            EVENTS.add("disable economy");
        }
    }

    static final class RanksModule extends MoonModule {
        RanksModule() {
            super("ranks", EconomyModule.class);
        }

        @Override
        protected void load() {
            EVENTS.add("load ranks");
        }

        @Override
        protected void enable() {
            EVENTS.add("enable ranks " + service(Economy.class).balance() + " " + require(EconomyModule.class).id());
        }

        @Override
        protected void disable() {
            EVENTS.add("disable ranks");
        }
    }

    static final class BrokenModule extends MoonModule {
        BrokenModule() {
            super("broken");
        }

        @Override
        protected void enable() {
            onDisable(() -> EVENTS.add("cleanup broken"));
            throw new IllegalStateException("config missing");
        }
    }

    static final class NeedsBroken extends MoonModule {
        NeedsBroken() {
            super("needs-broken", BrokenModule.class);
        }

        @Override
        protected void enable() {
            EVENTS.add("should not run");
        }
    }

    static final class A extends MoonModule {
        A() {
            super("a", B.class);
        }

        @Override
        protected void enable() {
        }
    }

    static final class B extends MoonModule {
        B() {
            super("b", A.class);
        }

        @Override
        protected void enable() {
        }
    }

    static Plugin plugin() {
        Logger logger = Logger.getLogger("ModuleManagerTest");
        logger.setUseParentHandlers(false);
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (p, m, a) -> m.getName().equals("getLogger") ? logger : m.getName().equals("getName") ? "Test" : null);
    }

    @Test
    void enablesInDependencyOrderAndIsolatesFailures() {
        EVENTS.clear();
        ModuleManager manager = new ModuleManager(plugin())
                .register(new NeedsBroken(), new RanksModule(), new BrokenModule(), new EconomyModule());
        assertEquals(2, manager.enableAll());
        assertEquals(Arrays.asList("load ranks", "cleanup broken", "enable economy", "enable ranks 100 economy"), EVENTS);
        assertEquals(ModuleManager.State.FAILED, manager.state(manager.byId("broken")));
        assertEquals(ModuleManager.State.SKIPPED, manager.state(manager.get(NeedsBroken.class)));
        assertTrue(manager.problem(manager.get(NeedsBroken.class)).contains("broken"));
        assertTrue(manager.services().find(Economy.class).isPresent());

        EVENTS.clear();
        manager.reload("economy");   // dependants are stopped first and are not restarted automatically
        assertEquals(Arrays.asList("disable ranks", "disable economy", "cleanup economy", "enable economy"), EVENTS);
        assertFalse(manager.get(RanksModule.class).isEnabled());
        assertTrue(manager.reload("ranks"));

        EVENTS.clear();
        manager.disableAll();
        assertEquals(Arrays.asList("disable ranks", "disable economy", "cleanup economy"), EVENTS);
        assertFalse(manager.services().find(Economy.class).isPresent(), "services are withdrawn on disable");
    }

    @Test
    void rejectsCyclesAndDuplicates() {
        ModuleManager manager = new ModuleManager(plugin()).register(new A(), new B());
        IllegalStateException e = assertThrows(IllegalStateException.class, manager::enableAll);
        assertTrue(e.getMessage().contains("a -> b -> a"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new ModuleManager(plugin()).register(new A(), new A()));
    }

    @Test
    void configTogglesTurnModulesOff() throws Exception {
        EVENTS.clear();
        File file = Files.createTempFile("modules", ".json").toFile();
        Files.write(file.toPath(), "{\"modules\": {\"ranks\": false}}".getBytes("UTF-8"));
        DataFile toggles = new JsonFile(file);
        ModuleManager manager = new ModuleManager(plugin()).toggles(toggles, "modules")
                .register(new EconomyModule(), new RanksModule());
        assertEquals(1, manager.enableAll());
        assertEquals(ModuleManager.State.TURNED_OFF, manager.state(manager.get(RanksModule.class)));
        assertTrue(new JsonFile(file).getBoolean("modules.economy", false), "missing toggles are written as true");
        manager.close();
        file.delete();
    }
}
