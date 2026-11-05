package com.lunamcdev.moonlib;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoonLibTest {

    private static Plugin fakePlugin(String name) {
        return (Plugin) Proxy.newProxyInstance(MoonLibTest.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                        case "toString":
                            return name;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(MoonLib.compareVersions("1.10.0", "1.9.2") > 0);
        assertTrue(MoonLib.compareVersions("1.0.9", "1.1.0") < 0);
        assertEquals(0, MoonLib.compareVersions("1.1", "1.1.0"));
        assertEquals(0, MoonLib.compareVersions("1.2.0-SNAPSHOT", "1.2.0"));
        assertTrue(MoonLib.isVersionAtLeast("1.0.0"));
        assertFalse(MoonLib.isVersionAtLeast("99.0.0"));
    }

    @Test
    void runAsBindsAndRestoresTheOwner() {
        Plugin first = fakePlugin("First");
        Plugin second = fakePlugin("Second");
        assertEquals(first, MoonLib.callAs(first, MoonLib::plugin));
        Plugin inner = MoonLib.callAs(first, () -> MoonLib.callAs(second, MoonLib::plugin));
        assertEquals(second, inner);
        Plugin restored = MoonLib.callAs(first, () -> {
            MoonLib.callAs(second, MoonLib::plugin);
            return MoonLib.plugin();
        });
        assertEquals(first, restored);
    }

    @Test
    void reportsMissingInitialisationClearly() {
        assertFalse(MoonLib.isInitialised());
        assertEquals(MoonLib.Mode.ISOLATED, MoonLib.mode());
        IllegalStateException error = assertThrows(IllegalStateException.class, MoonLib::plugin);
        assertTrue(error.getMessage().contains("MoonLib.init"));
    }

    @Test
    void ignoresUnknownPluginsOnShutdown() {
        MoonLib.shutdown(fakePlugin("NeverInitialised"));
        assertFalse(MoonLib.isInitialised());
    }
}
