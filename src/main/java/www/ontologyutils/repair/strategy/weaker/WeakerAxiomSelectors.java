package www.ontologyutils.repair.strategy.weaker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registry of the weakening strategies, keyed by the names the command line uses.
 *
 * <p>
 * This is the only place that knows the concrete strategies, so adding one means
 * adding a class and one entry here: nothing that selects a strategy has to
 * change, neither the applications nor the experiments. The random weakening is
 * the default of the repair, and it is registered like any other strategy.
 */
public final class WeakerAxiomSelectors {
    private static final Map<String, Supplier<WeakerAxiomSelector>> REGISTRY = registry();

    private WeakerAxiomSelectors() {
    }

    private static Map<String, Supplier<WeakerAxiomSelector>> registry() {
        var registry = new LinkedHashMap<String, Supplier<WeakerAxiomSelector>>();
        registry.put("random", RandomWeakerAxiomSelector::new);
        registry.put("shapley", ShapleyWeakerAxiomSelector::new);
        registry.put("shapley-approx", ShapleyApproxWeakerAxiomSelector::new);
        registry.put("banzhaf", BanzhafWeakerAxiomSelector::new);
        registry.put("banzhaf-approx", BanzhafApproxWeakerAxiomSelector::new);
        return Collections.unmodifiableMap(registry);
    }

    /**
     * @return The names of the available strategies, in the order they were
     *         registered.
     */
    public static Set<String> names() {
        return REGISTRY.keySet();
    }

    /**
     * @param name
     *            The name of a strategy, as listed by {@link #names()}.
     * @return A new instance of that strategy.
     * @throws IllegalArgumentException
     *             If no strategy is registered under that name.
     */
    public static WeakerAxiomSelector create(String name) {
        var supplier = REGISTRY.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException(
                    "Unknown weaker-axiom strategy: " + name + ". Known strategies: " + names());
        }
        return supplier.get();
    }
}
