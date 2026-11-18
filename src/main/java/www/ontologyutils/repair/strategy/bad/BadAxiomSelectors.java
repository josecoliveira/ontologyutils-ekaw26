package www.ontologyutils.repair.strategy.bad;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import www.ontologyutils.repair.OntologyRepairRemoval;

/**
 * Registry of the bad-axiom strategies, keyed by the names the command line uses.
 *
 * <p>
 * This is the only place that knows the concrete strategies, so adding one means
 * adding a class and one entry here: nothing that selects a strategy has to
 * change, neither the applications nor the experiments. The structural
 * strategies and the power indexes live in the same table, because they are the
 * same kind of choice.
 */
public final class BadAxiomSelectors {
    private static final Map<String, Supplier<BadAxiomSelector>> REGISTRY = registry();

    private BadAxiomSelectors() {
    }

    private static Map<String, Supplier<BadAxiomSelector>> registry() {
        var registry = new LinkedHashMap<String, Supplier<BadAxiomSelector>>();
        registry.put("one-mus",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_ONE_MUS));
        registry.put("some-mus",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS));
        registry.put("most-mus",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_MOST_MUS));
        registry.put("least-mcs",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_LEAST_MCS));
        registry.put("largest-mcs",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.NOT_IN_LARGEST_MCS));
        registry.put("one-mcs",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.NOT_IN_ONE_MCS));
        registry.put("some-mcs",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.NOT_IN_SOME_MCS));
        registry.put("random",
                () -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.RANDOM));
        registry.put("shapley", ShapleyBadAxiomSelector::new);
        registry.put("shapley-approx", ShapleyApproxBadAxiomSelector::new);
        registry.put("banzhaf", BanzhafBadAxiomSelector::new);
        registry.put("banzhaf-approx", BanzhafApproxBadAxiomSelector::new);
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
    public static BadAxiomSelector create(String name) {
        var supplier = REGISTRY.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException(
                    "Unknown bad-axiom strategy: " + name + ". Known strategies: " + names());
        }
        return supplier.get();
    }
}
