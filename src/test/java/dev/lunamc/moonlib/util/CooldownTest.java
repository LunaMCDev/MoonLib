package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTest {

    @Test
    void firstUseSucceedsSecondFails() {
        Cooldown<String> cooldown = Cooldown.of(1, TimeUnit.MINUTES);
        assertTrue(cooldown.tryUse("luna"));
        assertFalse(cooldown.tryUse("luna"));
        assertTrue(cooldown.isActive("luna"));
        assertTrue(cooldown.tryUse("someone-else"));
    }

    @Test
    void resetClearsCooldown() {
        Cooldown<String> cooldown = Cooldown.of(1, TimeUnit.MINUTES);
        cooldown.put("luna");
        cooldown.reset("luna");
        assertFalse(cooldown.isActive("luna"));
        assertEquals(0, cooldown.remainingMillis("luna"));
    }

    @Test
    void expiresAfterDuration() throws InterruptedException {
        Cooldown<String> cooldown = Cooldown.of(20, TimeUnit.MILLISECONDS);
        assertTrue(cooldown.tryUse("luna"));
        Thread.sleep(40);
        assertTrue(cooldown.tryUse("luna"));
    }

    @Test
    void formatsRemainingRoundingUp() {
        Cooldown<String> cooldown = Cooldown.of(90, TimeUnit.SECONDS);
        cooldown.put("luna");
        assertEquals("1m 30s", cooldown.formatRemaining("luna"));
    }
}
