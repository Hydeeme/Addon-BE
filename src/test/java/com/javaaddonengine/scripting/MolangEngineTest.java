package com.javaaddonengine.scripting;

import com.javaaddonengine.core.AddonCompatibilityException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MolangEngineTest {

    private MolangEngine.Context ctx() {
        return new MolangEngine.Context()
                .query("health", 14)
                .query("max_health", 20)
                .query("is_on_ground", 1)
                .query("is_sneaking", 0)
                .variable("speed", 3);
    }

    @Test
    void arithmetic() throws Exception {
        assertEquals(7.0, MolangEngine.evaluate("3 + 4", ctx()), 1e-9);
        assertEquals(14.0, MolangEngine.evaluate("2 * 7", ctx()), 1e-9);
    }

    @Test
    void queryDivision() throws Exception {
        assertEquals(0.7, MolangEngine.evaluate("query.health / query.max_health", ctx()), 1e-9);
    }

    @Test
    void ternary() throws Exception {
        assertEquals(1.0, MolangEngine.evaluate("query.is_on_ground ? 1 : 0", ctx()), 1e-9);
        assertEquals(0.0, MolangEngine.evaluate("query.is_sneaking ? 1 : 0", ctx()), 1e-9);
    }

    @Test
    void comparisonsAndLogic() throws Exception {
        assertEquals(1.0, MolangEngine.evaluate("query.health > 10 && query.is_on_ground", ctx()), 1e-9);
        assertEquals(1.0, MolangEngine.evaluate("query.is_sneaking || query.is_on_ground", ctx()), 1e-9);
    }

    @Test
    void variables() throws Exception {
        assertEquals(6.0, MolangEngine.evaluate("variable.speed * 2", ctx()), 1e-9);
    }

    @Test
    void unknownQueryIsControlledError() {
        assertThrows(AddonCompatibilityException.class, () ->
                MolangEngine.evaluate("query.does_not_exist + 1", ctx()));
    }

    @Test
    void mathNamespaceIsControlledError() {
        assertThrows(AddonCompatibilityException.class, () ->
                MolangEngine.evaluate("math.sin(1)", ctx()));
    }

    @Test
    void emptyExpressionIsControlledError() {
        assertThrows(AddonCompatibilityException.class, () -> MolangEngine.evaluate("  ", ctx()));
    }

    @Test
    void divisionByZeroIsSafe() throws Exception {
        assertEquals(0.0, MolangEngine.evaluate("1 / 0", ctx()), 1e-9);
    }
}
