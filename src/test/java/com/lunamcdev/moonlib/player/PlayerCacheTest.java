package com.lunamcdev.moonlib.player;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCacheTest {

    static final class Data {
        volatile int tokens;
    }

    final Map<UUID, Integer> disk = new ConcurrentHashMap<>();
    final List<String> log = new CopyOnWriteArrayList<>();

    PlayerCache<Data> cache(AtomicBoolean failLoads, CountDownLatch saveGate) {
        return PlayerCache.<Data>builder(null)
                .loader((id, name) -> {
                    if (failLoads.get()) {
                        throw new IllegalStateException("database down");
                    }
                    log.add("load " + name);
                    Data d = new Data();
                    d.tokens = disk.getOrDefault(id, 0);
                    return d;
                })
                .saver((id, value) -> {
                    if (saveGate != null) {
                        saveGate.await(5, TimeUnit.SECONDS);
                    }
                    disk.put(id, value.tokens);
                    log.add("save " + value.tokens);
                })
                .autosave(0, TimeUnit.SECONDS)
                .build();
    }

    /** A login event as a 26.3 server fires it: name, IP address, UUID, not a server transfer. */
    static AsyncPlayerPreLoginEvent login(String name, UUID id) {
        return new AsyncPlayerPreLoginEvent(name, InetAddress.getLoopbackAddress(), id, false);
    }

    static Player player(UUID id, String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (p, m, a) -> m.getName().equals("getUniqueId") ? id : m.getName().equals("getName") ? name : null);
    }

    @Test
    void loadsBeforeJoinAndSavesOnQuit() throws Exception {
        UUID id = UUID.randomUUID();
        disk.put(id, 7);
        PlayerCache<Data> cache = cache(new AtomicBoolean(), null);
        AsyncPlayerPreLoginEvent login = login("Luna", id);
        cache.handlePreLogin(login);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, login.getLoginResult());
        assertTrue(cache.isLoaded(id));
        Player luna = player(id, "Luna");
        cache.handleJoin(luna);
        cache.get(luna).tokens += 10;

        cache.unload(id).get(5, TimeUnit.SECONDS);
        assertEquals(17, (int) disk.get(id));
        assertFalse(cache.isLoaded(id));
        cache.close();
    }

    @Test
    void failedLoadTurnsThePlayerAway() {
        PlayerCache<Data> cache = cache(new AtomicBoolean(true), null);
        UUID id = UUID.randomUUID();
        AsyncPlayerPreLoginEvent login = login("Luna", id);
        cache.handlePreLogin(login);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, login.getLoginResult());
        assertTrue(login.getKickMessage().contains("rejoin"), login.getKickMessage());
        assertFalse(cache.isLoaded(id));
        cache.close();
    }

    @Test
    void quickRejoinKeepsInMemoryDataInsteadOfStaleDisk() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch gate = new CountDownLatch(1);
        PlayerCache<Data> cache = cache(new AtomicBoolean(), gate);
        cache.handlePreLogin(login("Luna", id));
        Player luna = player(id, "Luna");
        cache.handleJoin(luna);
        cache.get(luna).tokens = 50;

        cache.handleQuit(luna);                       // save is now blocked on the gate
        cache.handlePreLogin(login("Luna", id));
        cache.handleJoin(luna);
        assertEquals(50, cache.get(luna).tokens, "rejoin sees the latest data");
        gate.countDown();
        cache.saveAll().get(5, TimeUnit.SECONDS);
        assertTrue(cache.isLoaded(id), "the finished quit-save did not evict the rejoined player");
        assertEquals(1, count("load Luna"), "data was loaded once: " + log);
        cache.get(luna).tokens = 60;
        cache.close();                                // final save on shutdown
        assertEquals(60, (int) disk.get(id));
    }

    private int count(String entry) {
        int n = 0;
        for (String line : log) {
            if (line.equals(entry)) {
                n++;
            }
        }
        return n;
    }
}
