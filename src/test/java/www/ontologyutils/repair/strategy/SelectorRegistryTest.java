package www.ontologyutils.repair.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import www.ontologyutils.repair.strategy.bad.BadAxiomSelectors;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelectors;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;

/**
 * Tests for the strategy registries that the applications and experiments use to
 * choose a strategy by name.
 */
public class SelectorRegistryTest {
    private static final Set<String> BAD_NAMES = Set.of("one-mus", "some-mus", "most-mus", "least-mcs", "largest-mcs",
            "one-mcs", "some-mcs", "random", "shapley", "shapley-approx", "banzhaf", "banzhaf-approx");

    private static final Set<String> WEAKER_NAMES = Set.of("random", "shapley", "shapley-approx",
            "banzhaf", "banzhaf-approx");

    @Test
    public void badRegistryContainsAllStrategies() {
        assertEquals(BAD_NAMES, BadAxiomSelectors.names());
    }

    @Test
    public void weakerRegistryContainsAllStrategies() {
        assertEquals(WEAKER_NAMES, WeakerAxiomSelectors.names());
    }

    @ParameterizedTest
    @ValueSource(strings = {"one-mus", "some-mus", "most-mus", "least-mcs", "largest-mcs", "one-mcs", "some-mcs",
            "random", "shapley", "shapley-approx", "banzhaf", "banzhaf-approx"})
    public void everyBadNameResolves(String name) {
        BadAxiomSelector selector = BadAxiomSelectors.create(name);
        assertNotNull(selector);
    }

    @ParameterizedTest
    @ValueSource(strings = {"random", "shapley", "shapley-approx", "banzhaf", "banzhaf-approx"})
    public void everyWeakerNameResolves(String name) {
        WeakerAxiomSelector selector = WeakerAxiomSelectors.create(name);
        assertNotNull(selector);
    }

    @Test
    public void everyBadNameIsRegistered() {
        // Guards against a name being added to the documentation but not to the registry.
        BAD_NAMES.forEach(name -> assertTrue(BadAxiomSelectors.names().contains(name), name));
        WEAKER_NAMES.forEach(name -> assertTrue(WeakerAxiomSelectors.names().contains(name), name));
    }

    @Test
    public void creatingFromEachNameGivesIndependentInstances() {
        var first = BadAxiomSelectors.create("shapley-approx");
        var second = BadAxiomSelectors.create("shapley-approx");
        assertTrue(first != second, "each call must build a fresh selector");
    }

    @Test
    public void unknownBadNameIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> BadAxiomSelectors.create("shapley-exact"));
        assertTrue(exception.getMessage().contains("shapley-approx"),
                "the error should list the known strategies");
    }

    @Test
    public void unknownWeakerNameIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> WeakerAxiomSelectors.create("banzhaf-exact"));
        assertTrue(exception.getMessage().contains("banzhaf-approx"),
                "the error should list the known strategies");
    }
}
