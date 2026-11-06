package www.ontologyutils.repair.powerindex;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * Represents a power index for computing the score (influence) of an axiom
 * within a set of axioms. Power indexes measure how much an axiom contributes
 * to the inconsistency of the ontology.
 */
public interface PowerIndex {
    /**
     * Computes the power index score of a target axiom within a set of axioms.
     * The score indicates the influence of the axiom on the inconsistency of the
     * set.
     *
     * @param axioms
     *            The set of axioms to evaluate the target axiom within.
     * @param targetAxiom
     *            The axiom for which to compute the power index score.
     * @return The power index score of the target axiom. Higher scores indicate
     *         greater influence on inconsistency.
     */
    double computeScore(Set<OWLAxiom> axioms, OWLAxiom targetAxiom);

    /**
     * Compute scores for multiple target axioms. Each target is scored in the
     * game {@code axioms u {target}}, i.e. exactly as
     * {@link #computeScore(Set, OWLAxiom)} scores it, so that the batch result
     * never depends on which other targets are scored. Default implementation
     * falls back to calling {@link #computeScore(Set, OWLAxiom)} repeatedly.
     */
    default Map<OWLAxiom, Double> computeScores(Set<OWLAxiom> axioms, Set<OWLAxiom> targets) {
        if (targets == null || targets.isEmpty()) {
            return Map.of();
        }
        Map<OWLAxiom, Double> result = new HashMap<>();
        for (OWLAxiom t : targets) {
            result.put(t, computeScore(axioms, t));
        }
        return result;
    }

    /**
     * Adaptive scoring that can stop early when a ranking stabilizes. Default
     * implementation delegates to {@link #computeScores(Set, Set)} without
     * adaptivity.
     *
     * @param axioms    base universe (each target is scored in {@code axioms u
     *                  {target}})
     * @param targets   targets to rank/score
     * @param chunkSize number of samples per chunk
     * @param maxSamples maximum total samples to use
     */
    default Map<OWLAxiom, Double> computeScoresAdaptive(Set<OWLAxiom> axioms, Set<OWLAxiom> targets,
            int chunkSize, int maxSamples) {
        return computeScores(axioms, targets);
    }

    /**
     * Adaptive scoring with explicit ranking direction. Default implementation
     * delegates to {@link #computeScoresAdaptive(Set, Set, int, int)}.
     */
    default Map<OWLAxiom, Double> computeScoresAdaptive(Set<OWLAxiom> axioms, Set<OWLAxiom> targets,
            int chunkSize, int maxSamples, boolean ascending) {
        return computeScoresAdaptive(axioms, targets, chunkSize, maxSamples);
    }
}

