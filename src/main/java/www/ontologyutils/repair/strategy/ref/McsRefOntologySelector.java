package www.ontologyutils.repair.strategy.ref;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.OntologyRepairWeakening;
import www.ontologyutils.toolbox.Ontology;

/**
 * Reference ontology selector based on maximal consistent subsets strategies.
 */
public class McsRefOntologySelector implements RefOntologySelector {
    private final OntologyRepairWeakening.RefOntologyStrategy strategy;

    public McsRefOntologySelector(OntologyRepairWeakening.RefOntologyStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public Stream<Set<OWLAxiom>> select(Ontology ontology, Predicate<Ontology> isRepaired) {
        switch (strategy) {
            case INTERSECTION_OF_MCS: {
                return Stream.of(
                        mcsIntersection(ontology.maximalConsistentSubsets(isRepaired)));
            }
            case INTERSECTION_OF_SOME_MCS: {
                return Stream.of(
                        mcsIntersection(ontology.someMaximalConsistentSubsets(isRepaired)));
            }
            case LARGEST_MCS:
                return ontology.largestMaximalConsistentSubsets(isRepaired);
            case RANDOM_MCS:
                return ontology.maximalConsistentSubsets(isRepaired);
            case SOME_MCS:
                return ontology.someMaximalConsistentSubsets(isRepaired);
            case ONE_MCS: {
                var mcs = ontology.maximalConsistentSubset(isRepaired);
                if (mcs == null) {
                    return Stream.of();
                } else {
                    return Stream.of(mcs);
                }
            }
            default:
                throw new IllegalArgumentException("Unimplemented reference ontology choice strategy.");
        }
    }

    private Set<OWLAxiom> mcsIntersection(Stream<Set<OWLAxiom>> sets) {
        return sets.reduce((a, b) -> {
            a.removeIf(axiom -> !b.contains(axiom));
            return a;
        }).orElse(Set.of());
    }
}

