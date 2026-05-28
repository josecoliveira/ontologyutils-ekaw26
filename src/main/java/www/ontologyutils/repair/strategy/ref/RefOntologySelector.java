package www.ontologyutils.repair.strategy.ref;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;

/**
 * Strategy interface for selecting reference ontology axioms.
 */
public interface RefOntologySelector {
    /**
     * Select one or more candidate sets of axioms to be used as a reference
     * ontology when repairing {@code ontology}.
     *
     * @param ontology   the ontology to inspect
     * @param isRepaired predicate used to test repair condition
     * @return a stream of candidate axiom sets
     */
    Stream<Set<OWLAxiom>> select(Ontology ontology, Predicate<Ontology> isRepaired);
}

