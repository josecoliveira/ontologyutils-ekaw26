package www.ontologyutils.repair.strategy.bad;

import java.util.Optional;
import java.util.function.Predicate;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;

/**
 * Strategy interface for selecting a single best bad axiom from an ontology.
 */
public interface BadAxiomSelector {
    /**
     * Select the single best bad axiom to act upon, or return {@link Optional#empty}
     * if no candidate exists.
     *
     * @param ontology   the ontology to inspect
     * @param isRepaired predicate used to test repair condition
     * @return optional best bad axiom
     */
    Optional<OWLAxiom> selectBest(Ontology ontology, Predicate<Ontology> isRepaired);
}

