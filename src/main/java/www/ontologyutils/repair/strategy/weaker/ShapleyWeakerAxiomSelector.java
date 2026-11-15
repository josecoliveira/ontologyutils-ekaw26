package www.ontologyutils.repair.strategy.weaker;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.LruCache;
import www.ontologyutils.toolbox.Utils;

/**
 * Weakening strategy that returns the candidate with the lowest exact Shapley
 * value, computed by enumerating every coalition of each candidate's own game.
 *
 * <p>
 * Exact enumeration is exponential in the number of axioms, so this is meant for
 * small ontologies and for checking the sampled strategies.
 *
 * <p>
 * The class is self-contained on purpose: the enumeration, the coalitions it has
 * already checked and its own arithmetic all belong to this strategy alone.
 */
public class ShapleyWeakerAxiomSelector implements WeakerAxiomSelector {
    private static final int CACHE_SIZE = 16384;

    private final Function<Set<OWLAxiom>, Integer> consistency =
            LruCache.wrapFunction(subset -> Utils.isConsistent(subset) ? 0 : 1, CACHE_SIZE);
    private final List<Double> factorialCache = new ArrayList<>(List.of(1.0d));

    @Override
    public Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context) {
        try {
            var weakenings = WeakerAxiomSelector.weakeningsOf(badAxiom, weakener);
            if (weakenings.isEmpty()) {
                return Optional.empty();
            }
            System.err.println("[ShapleyWeakerAxiomSelector] Scoring " + weakenings.size()
                    + " candidate weakenings for " + Utils.prettyPrintAxiomDL(badAxiom));
            Map<OWLAxiom, Double> scores = score(WeakerAxiomSelector.scoringContext(context, badAxiom), weakenings);
            var best = WeakerAxiomSelector.lowestScoring(scores);
            System.err.println(
                    "[ShapleyWeakerAxiomSelector] Selected weaker axiom: " + Utils.prettyPrintAxiomDL(best));
            return Optional.of(best);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * @param context
     *            The game that every replacement would produce, without the
     *            candidates.
     * @param candidates
     *            The weakenings to score, each in its own game.
     * @return The exact value of each candidate in its own game.
     */
    public Map<OWLAxiom, Double> score(Set<OWLAxiom> context, Set<OWLAxiom> candidates) {
        Map<OWLAxiom, Double> values = new HashMap<>();
        for (OWLAxiom candidate : candidates) {
            var universe = new HashSet<>(context);
            universe.add(candidate);
            values.put(candidate, valueOf(universe, candidate));
        }
        return values;
    }

    /**
     * @param universe
     *            The players of the game, including {@code target}.
     * @param target
     *            The player whose value is wanted.
     * @return The exact Shapley value of {@code target} in that game.
     */
    private double valueOf(Set<OWLAxiom> universe, OWLAxiom target) {
        int n = universe.size();

        List<OWLAxiom> baseCoalition = new ArrayList<>(universe);
        baseCoalition.remove(target);

        Map<Set<OWLAxiom>, Integer> inconsistencyWithTargetBySubset = new HashMap<>();
        Set<Set<OWLAxiom>> visited = new HashSet<>();

        for (int subsetSize = 0; subsetSize <= baseCoalition.size(); subsetSize++) {
            generateSubsetsOfSize(baseCoalition, subsetSize, subset -> {
                Set<OWLAxiom> subsetKey = Set.copyOf(subset);
                if (visited.contains(subsetKey)) {
                    return;
                }

                Set<OWLAxiom> coalitionWithTarget = new HashSet<>(subsetKey);
                coalitionWithTarget.add(target);
                int inconsistencyWithTarget = consistencyValue(coalitionWithTarget);

                inconsistencyWithTargetBySubset.put(subsetKey, inconsistencyWithTarget);
                visited.add(subsetKey);

                if (inconsistencyWithTarget == 1) {
                    markSupersetsVisited(baseCoalition, subsetKey, visited, inconsistencyWithTargetBySubset);
                }
            });
        }

        double total = 0.0d;
        for (Map.Entry<Set<OWLAxiom>, Integer> entry : inconsistencyWithTargetBySubset.entrySet()) {
            Set<OWLAxiom> subset = entry.getKey();
            int c = subset.size() + 1;
            double weight = factorial(c - 1) * factorial(n - c);
            int marginalContribution = entry.getValue() - consistencyValue(subset);
            total += weight * marginalContribution;
        }
        return total / factorial(n);
    }

    private void markSupersetsVisited(List<OWLAxiom> universe, Set<OWLAxiom> subset, Set<Set<OWLAxiom>> visited,
            Map<Set<OWLAxiom>, Integer> inconsistencyWithTargetBySubset) {
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
                inconsistencyWithTargetBySubset.putIfAbsent(key, 1);
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

    private int consistencyValue(Set<OWLAxiom> subset) {
        return consistency.apply(Set.copyOf(subset));
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
}
