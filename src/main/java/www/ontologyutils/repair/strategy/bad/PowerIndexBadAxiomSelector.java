package www.ontologyutils.repair.strategy.bad;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.powerindex.PowerIndex;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;
// Use simple stderr logging here; GlobalLogger wrapper exists but is postponed for integration.

/**
 * Generic PowerIndex-backed bad-axiom selector. Returns the single axiom with
 * the highest power-index score.
 */
public class PowerIndexBadAxiomSelector implements BadAxiomSelector {
    private final PowerIndex powerIndex;
    private final int chunkSize;
    private final int maxSamples;

    public PowerIndexBadAxiomSelector(PowerIndex powerIndex) {
        this(powerIndex, 50, 1000);
    }

    public PowerIndexBadAxiomSelector(PowerIndex powerIndex, int chunkSize, int maxSamples) {
        this.powerIndex = powerIndex;
        this.chunkSize = chunkSize;
        this.maxSamples = maxSamples;
    }

    @Override
    public Optional<OWLAxiom> selectBest(Ontology ontology, Predicate<Ontology> isRepaired) {
        Set<OWLAxiom> current = ontology.refutableAxioms().collect(Collectors.toSet());
        if (current == null || current.isEmpty()) {
            return Optional.empty();
        }
        System.err.println("[PowerIndexBadAxiomSelector] Computing power-index scores for " + current.size() + " candidates");
        Map<OWLAxiom, Double> scores = powerIndex.computeScoresAdaptive(current, current, chunkSize, maxSamples, false);
        if (scores == null || scores.isEmpty()) {
            return Optional.empty();
        }
        var best = scores.entrySet().stream()
                .max(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(entry -> Utils.prettyPrintAxiomDL(entry.getKey())))
                .get().getKey();
        System.err.println("[PowerIndexBadAxiomSelector] Selected bad axiom: " + Utils.prettyPrintAxiomDL(best));
        return Optional.of(best);
    }
}
