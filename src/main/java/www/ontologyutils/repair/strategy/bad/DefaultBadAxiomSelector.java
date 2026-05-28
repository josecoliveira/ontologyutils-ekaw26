package www.ontologyutils.repair.strategy.bad;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.OntologyRepairRemoval;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Default bad-axiom selector reusing existing heuristics from
 * {@link OntologyRepairRemoval}.
 */
public class DefaultBadAxiomSelector implements BadAxiomSelector {
    private final OntologyRepairRemoval.BadAxiomStrategy strategy;

    public DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public Optional<OWLAxiom> selectBest(Ontology ontology, Predicate<Ontology> isRepaired) {
        Stream<OWLAxiom> candidates;
        switch (strategy) {
            case IN_LEAST_MCS:
                candidates = mostFrequentIn(ontology.minimalCorrectionSubsets(isRepaired));
                break;
            case NOT_IN_LARGEST_MCS:
                candidates = mostFrequentIn(ontology.smallestMinimalCorrectionSubsets(isRepaired));
                break;
            case NOT_IN_SOME_MCS:
                candidates = mostFrequentIn(ontology.someMinimalCorrectionSubsets(isRepaired));
                break;
            case IN_SOME_MUS:
                candidates = mostFrequentIn(ontology.someMinimalUnsatisfiableSubsets(isRepaired));
                break;
            case IN_MOST_MUS:
                candidates = mostFrequentIn(ontology.minimalUnsatisfiableSubsets(isRepaired));
                break;
            case IN_ONE_MUS: {
                var mus = ontology.minimalUnsatisfiableSubset(isRepaired);
                candidates = mus == null ? Stream.of() : mus.stream();
                break;
            }
            case NOT_IN_ONE_MCS: {
                var mcs = ontology.minimalCorrectionSubset(isRepaired);
                candidates = mcs == null ? Stream.of() : mcs.stream();
                break;
            }
            case RANDOM:
            default:
                candidates = ontology.refutableAxioms();
                break;
        }
        var list = Utils.toList(candidates);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Utils.randomChoice(list));
    }

    private Stream<OWLAxiom> mostFrequentIn(Stream<java.util.Set<OWLAxiom>> sets) {
        var occurrences = sets.flatMap(set -> set.stream()).collect(java.util.stream.Collectors.groupingBy(java.util.function.Function.identity(), java.util.stream.Collectors.counting()));
        var max = occurrences.values().stream().max(Long::compareTo).orElse(0L);
        return occurrences.entrySet().stream().filter(entry -> entry.getValue() == max).map(entry -> entry.getKey());
    }
}

