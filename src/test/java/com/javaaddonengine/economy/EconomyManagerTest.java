package com.javaaddonengine.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyManagerTest {

    @Test
    void internalFallback() {
        EconomyManager m = new EconomyManager();
        UUID player = UUID.randomUUID();
        assertEquals(0.0, m.balance(player), 1e-9);
        m.deposit(player, 50);
        assertEquals(50.0, m.balance(player), 1e-9);
        assertTrue(m.withdraw(player, 20));
        assertEquals(30.0, m.balance(player), 1e-9);
        assertFalse(m.withdraw(player, 100));
        assertFalse(m.withdraw(player, -5));
        m.deposit(player, -5);
        assertEquals(30.0, m.balance(player), 1e-9);
    }

    @Test
    void vaultWithoutProviderStaysInternal() {
        EconomyManager m = new EconomyManager();
        m.setProvider(EconomyManager.Provider.VAULT);
        assertEquals(EconomyManager.Provider.INTERNAL, m.getActive());
    }
}
