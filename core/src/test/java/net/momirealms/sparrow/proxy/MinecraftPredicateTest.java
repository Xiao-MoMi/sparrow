package net.momirealms.sparrow.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftPredicateTest {

    @Test
    void evaluatesOperatorsWithWhitespaceAndPrecedence() {
        MinecraftPredicate predicate = new MinecraftPredicate("1.21.11", List.of("paper"));

        assertTrue(predicate.test("min_version=1.21.10 && (has_patch=paper || version=1.21.8)"));
        assertFalse(predicate.test("min_version=1.21.10 && !has_patch=paper"));
        assertTrue(predicate.test("   min_version=1.21.10      &&    has_patch=paper   "));
    }

    @Test
    void parsesOnlyTheFirstThreeVersionComponents() {
        assertEquals(10000, MinecraftPredicate.parseVersionToInteger("1"));
        assertEquals(12000, MinecraftPredicate.parseVersionToInteger("1.20"));
        assertEquals(12004, MinecraftPredicate.parseVersionToInteger("1.20.4"));
        assertEquals(12004, MinecraftPredicate.parseVersionToInteger("1.20.4.5"));
    }
}
