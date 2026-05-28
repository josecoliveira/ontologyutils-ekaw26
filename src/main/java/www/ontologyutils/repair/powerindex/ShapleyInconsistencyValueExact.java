package www.ontologyutils.repair.powerindex;

import java.util.*;
import java.util.function.Consumer;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Utils;
import www.ontologyutils.toolbox.LruCache;

/**
 * Computes the exact Shapley inconsistency value for an axiom. This uses the
 * drastic inconsistency measure and enumerates all possible subsets of the
 * axiom set to compute the exact Shapley value.
 */
public class ShapleyInconsistencyValueExact implements PowerIndex {
    // Use a bounded LRU cache to avoid unbounded memory usage on large ontologies.
    private final java.util.function.Function<Set<OWLAxiom>, Integer> drasticCached;
    private final List<Double> factorialCache;

    /**
     * Creates a new exact Shapley inconsistency value computer with empty caches.
     */
    public ShapleyInconsistencyValueExact() {
        this.drasticCached = LruCache.wrapFunction(subset -> Utils.isConsistent(subset) ? 0 : 1, 16384);
        this.factorialCache = new ArrayList<>();
        this.factorialCache.add(1.0d);
    }

    @Override
    public double computeScore(Set<OWLAxiom> axioms, OWLAxiom targetAxiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(targetAxiom);
        return exactShapleyInconsistencyValue(universe, targetAxiom);
    }

    private double exactShapleyInconsistencyValue(Set<OWLAxiom> universe, OWLAxiom axiom) {
        int n = universe.size();

        List<OWLAxiom> baseCoalition = new ArrayList<>(universe);
        baseCoalition.remove(axiom);

        Map<Set<OWLAxiom>, Integer> inconsistencyWithAxiomBySubset = new HashMap<>();
        Set<Set<OWLAxiom>> visited = new HashSet<>();

        for (int subsetSize = 0; subsetSize <= baseCoalition.size(); subsetSize++) {
            generateSubsetsOfSize(baseCoalition, subsetSize, subset -> {
                Set<OWLAxiom> subsetKey = Set.copyOf(subset);
                if (visited.contains(subsetKey)) {
                    return;
                }

                Set<OWLAxiom> coalitionWithAxiom = new HashSet<>(subsetKey);
                coalitionWithAxiom.add(axiom);
                int inconsistencyWithAxiom = drasticInconsistencyValue(coalitionWithAxiom);

                inconsistencyWithAxiomBySubset.put(subsetKey, inconsistencyWithAxiom);
                visited.add(subsetKey);

                if (inconsistencyWithAxiom == 1) {
                    markSupersetsVisited(baseCoalition, subsetKey, visited, inconsistencyWithAxiomBySubset);
                }
            });
        }

        double total = 0.0d;
        for (Map.Entry<Set<OWLAxiom>, Integer> entry : inconsistencyWithAxiomBySubset.entrySet()) {
            Set<OWLAxiom> subset = entry.getKey();
            int c = subset.size() + 1;
            double weight = factorial(c - 1) * factorial(n - c);
            int marginalContribution = entry.getValue() - drasticInconsistencyValue(subset);
            total += weight * marginalContribution;
        }
        return total / factorial(n);
    }

    private int drasticInconsistencyValue(Set<OWLAxiom> subset) {
        Set<OWLAxiom> key = Set.copyOf(subset);
        return drasticCached.apply(key);
    }

    private double factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial is undefined for negative numbers: " + n);
        }
        while (factorialCache.size() <= n) {
            int k = factorialCache.size();
            factorialCache.add(factorialCache.get(k - 1) * k);
        }
        return factorialCache.get(n);
    }

    private static void markSupersetsVisited(List<OWLAxiom> universe, Set<OWLAxiom> subset,
            Set<Set<OWLAxiom>> visited, Map<Set<OWLAxiom>, Integer> inconsistencyWithAxiomBySubset) {
        List<OWLAxiom> remaining = new ArrayList<>();
        for (OWLAxiom candidate : universe) {
            if (!subset.contains(candidate)) {
                remaining.add(candidate);
            }
        }

        for (int size = 0; size <= remaining.size(); size++) {
            generateSubsetsOfSize(remaining, size, extension -> {
                Set<OWLAxiom> superset = new HashSet<>(subset);
                superset.addAll(extension);
                Set<OWLAxiom> key = Set.copyOf(superset);
                visited.add(key);
                inconsistencyWithAxiomBySubset.putIfAbsent(key, 1);
            });
        }
    }

    private static <T> void generateSubsetsOfSize(List<T> elements, int targetSize, Consumer<Set<T>> consumer) {
        generateSubsetsOfSize(elements, targetSize, 0, new HashSet<>(), consumer);
    }

    private static <T> void generateSubsetsOfSize(List<T> elements, int targetSize, int index, Set<T> current,
            Consumer<Set<T>> consumer) {
        if (current.size() == targetSize) {
            consumer.accept(new HashSet<>(current));
            return;
        }
        if (index >= elements.size()) {
            return;
        }
        if (current.size() + (elements.size() - index) < targetSize) {
            return;
        }

        T element = elements.get(index);
        current.add(element);
        generateSubsetsOfSize(elements, targetSize, index + 1, current, consumer);
        current.remove(element);
        generateSubsetsOfSize(elements, targetSize, index + 1, current, consumer);
    }
}
