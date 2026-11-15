package www.ontologyutils.repair.strategy.bad;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Strategy interface for selecting a single best bad axiom from an ontology.
 *
 * <p>
 * Nothing in this interface ties the choice to any particular notion of
 * influence: a strategy may rank the axioms with an inconsistency value, with a
 * structural heuristic, or at random.
 */
public interface BadAxiomSelector {
    /**
     * Select the single best bad axiom to act upon, or return {@link Optional#empty}
     * if no candidate exists.
     *
     * @param ontology
     *            the ontology to inspect
     * @param isRepaired
     *            predicate used to test repair condition
     * @return optional best bad axiom
     */
    Optional<OWLAxiom> selectBest(Ontology ontology, Predicate<Ontology> isRepaired);

    /**
     * @param scores
     *            The score of every candidate axiom.
     * @return The candidate with the highest score, ties broken by the printed
     *         form of the axiom so that a run is reproducible.
     */
    static OWLAxiom highestScoring(Map<OWLAxiom, Double> scores) {
        return scores.entrySet().stream()
                .max(Comparator.<Map.Entry<OWLAxiom, Double>> comparingDouble(Map.Entry::getValue)
                        .thenComparing(entry -> Utils.prettyPrintAxiomDL(entry.getKey())))
                .orElseThrow(() -> new IllegalArgumentException("There is no axiom to select from."))
                .getKey();
    }
}
