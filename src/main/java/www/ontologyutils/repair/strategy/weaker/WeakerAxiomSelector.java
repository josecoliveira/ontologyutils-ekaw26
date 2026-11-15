package www.ontologyutils.repair.strategy.weaker;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.Utils;

/**
 * Strategy interface for selecting the single best weaker axiom to replace a bad
 * axiom.
 *
 * <p>
 * Nothing in this interface ties the choice to any particular notion of
 * influence: a strategy may rank the candidates with an inconsistency value,
 * with a structural heuristic, or at random.
 */
public interface WeakerAxiomSelector {
    /**
     * Select the single best weakening for {@code badAxiom} using the provided
     * {@code weakener} and the current {@code context} of axioms.
     *
     * @param badAxiom
     *            the axiom to weaken
     * @param weakener
     *            the axiom weakener to compute candidates
     * @param context
     *            the current set of refutable axioms (context)
     * @return Optional containing the chosen weakening, or empty if none
     */
    Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context);

    /**
     * @param badAxiom
     *            The axiom to weaken.
     * @param weakener
     *            The axiom weakener to compute the candidates with.
     * @return The strict weakenings of {@code badAxiom}, that is without the axiom
     *         itself.
     */
    static Set<OWLAxiom> weakeningsOf(OWLAxiom badAxiom, AxiomWeakener weakener) {
        var weakenings = weakener.weakerAxioms(badAxiom).collect(java.util.stream.Collectors.toSet());
        weakenings.remove(badAxiom);
        return weakenings;
    }

    /**
     * @param context
     *            The current refutable axioms.
     * @param badAxiom
     *            The axiom that is going to be replaced.
     * @return The context without the bad axiom, which is the ontology that the
     *         replacement produces and therefore the one to score candidates in.
     */
    static Set<OWLAxiom> scoringContext(Set<OWLAxiom> context, OWLAxiom badAxiom) {
        var scoringContext = new HashSet<>(context);
        scoringContext.remove(badAxiom);
        return scoringContext;
    }

    /**
     * @param scores
     *            The score of every candidate weakening.
     * @return The candidate with the lowest score, ties broken by the printed form
     *         of the axiom so that a run is reproducible.
     */
    static OWLAxiom lowestScoring(Map<OWLAxiom, Double> scores) {
        return scores.entrySet().stream()
                .min(Comparator.<Map.Entry<OWLAxiom, Double>> comparingDouble(Map.Entry::getValue)
                        .thenComparing(entry -> Utils.prettyPrintAxiomDL(entry.getKey())))
                .orElseThrow(() -> new IllegalArgumentException("There is no weakening to select from."))
                .getKey();
    }
}
