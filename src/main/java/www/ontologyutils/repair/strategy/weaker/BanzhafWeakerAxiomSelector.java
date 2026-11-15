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
 * Weakening strategy that returns the candidate with the lowest exact Banzhaf
 * value, computed by enumerating every coalition of each candidate's own game.
 *
 * <p>
 * Every candidate is scored in the game that its own replacement would produce:
 * the coalitions are drawn from the context and the candidate is added on top of
 * them, so the other candidates are never part of that candidate's game. The
 * Banzhaf value of the candidate is the average of its marginal contributions
 * over all the coalitions of the context, that is the sum divided by
 * {@code 2^(n-1)}; unlike the Shapley value the coalitions are not weighted by
 * their size.
 *
 * <p>
 * Exact enumeration is exponential in the number of axioms, so this is meant for
 * small ontologies and for checking the sampled strategies.
 *
 * <p>
 * The class is self-contained on purpose: the enumeration, the coalitions it has
 * already checked and its own arithmetic all belong to this strategy alone.
 */
public class BanzhafWeakerAxiomSelector implements WeakerAxiomSelector {
    private static final int CACHE_SIZE = 16384;

    /**
     * The coalitions this strategy has already checked, for as long as the strategy
     * lives, that is for the duration of one repair.
     */
    private final Function<Set<OWLAxiom>, Integer> consistency =
            LruCache.wrapFunction(subset -> Utils.isConsistent(subset) ? 0 : 1, CACHE_SIZE);

    @Override
    public Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context) {
        try {
            var weakenings = WeakerAxiomSelector.weakeningsOf(badAxiom, weakener);
            if (weakenings.isEmpty()) {
                return Optional.empty();
            }
            System.err.println("[BanzhafWeakerAxiomSelector] Scoring " + weakenings.size()
                    + " candidate weakenings for " + Utils.prettyPrintAxiomDL(badAxiom));
            Map<OWLAxiom, Double> scores = score(WeakerAxiomSelector.scoringContext(context, badAxiom), weakenings);
            var best = WeakerAxiomSelector.lowestScoring(scores);
            System.err.println(
                    "[BanzhafWeakerAxiomSelector] Selected weaker axiom: " + Utils.prettyPrintAxiomDL(best));
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
     * @return The exact Banzhaf value of {@code target} in that game.
     */
    private double valueOf(Set<OWLAxiom> universe, OWLAxiom target) {
        int n = universe.size();

        List<OWLAxiom> baseCoalition = new ArrayList<>(universe);
        baseCoalition.remove(target);

        // Every coalition of the other players contributes with weight one, so the
        // marginal contributions are summed as they are found.
        double[] total = { 0.0d };
        for (int subsetSize = 0; subsetSize <= baseCoalition.size(); subsetSize++) {
            generateSubsetsOfSize(baseCoalition, subsetSize, subset -> {
                Set<OWLAxiom> subsetKey = Set.copyOf(subset);
                int inconsistencyWithoutTarget = consistencyValue(subsetKey);
                if (inconsistencyWithoutTarget == 1) {
                    // Adding an axiom never restores consistency, so the marginal
                    // contribution of the target is zero here.
                    return;
                }
                Set<OWLAxiom> coalitionWithTarget = new HashSet<>(subsetKey);
                coalitionWithTarget.add(target);
                total[0] += consistencyValue(coalitionWithTarget) - inconsistencyWithoutTarget;
            });
        }
        return total[0] / Math.pow(2.0d, n - 1);
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
}
