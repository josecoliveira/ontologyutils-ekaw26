package www.ontologyutils.repair.strategy.weaker;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.powerindex.PowerIndex;
import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.Utils;
// Use simple stderr logging here; GlobalLogger wrapper exists but is postponed for integration.
import java.io.PrintStream;

/**
 * Generic PowerIndex-backed weaker-axiom selector. Returns the axiom with the
 * lowest power-index score (less influence).
 *
 * Each candidate is scored in the ontology the replacement would produce, that
 * is the reference set without the bad axiom plus the candidate alone.
 */
public class PowerIndexWeakerAxiomSelector implements WeakerAxiomSelector {
    private final PowerIndex powerIndex;
    private final int chunkSize;
    private final int maxSamples;

    public PowerIndexWeakerAxiomSelector(PowerIndex powerIndex) {
        this(powerIndex, 50, 1000);
    }

    public PowerIndexWeakerAxiomSelector(PowerIndex powerIndex, int chunkSize, int maxSamples) {
        this.powerIndex = powerIndex;
        this.chunkSize = chunkSize;
        this.maxSamples = maxSamples;
    }

    @Override
    public Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context) {
        try {
            var weakenings = weakener.weakerAxioms(badAxiom).collect(Collectors.toSet());
            weakenings.remove(badAxiom);
            if (weakenings.isEmpty()) {
                System.err.println("[PowerIndexWeakerAxiomSelector] No weakenings found for axiom: " + Utils.prettyPrintAxiomDL(badAxiom));
                 return Optional.empty();
             }
            System.err.println("[PowerIndexWeakerAxiomSelector] Scoring " + weakenings.size() + " candidate weakenings for " + Utils.prettyPrintAxiomDL(badAxiom));
            // The candidates are scored in the ontology that the replacement produces, so
            // the bad axiom they replace must not be part of the game; otherwise every
            // candidate is judged against an ontology that is still inconsistent because of
            // the very axiom it is meant to replace.
            var scoringContext = new HashSet<>(context);
            scoringContext.remove(badAxiom);
            Map<OWLAxiom, Double> scores = powerIndex.computeScoresAdaptive(scoringContext, weakenings, chunkSize, maxSamples, true);
             var best = scores.entrySet().stream()
                     .min(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                             .thenComparing(e -> Utils.prettyPrintAxiomDL(e.getKey())))
                     .get().getKey();
            System.err.println("[PowerIndexWeakerAxiomSelector] Selected weaker axiom: " + Utils.prettyPrintAxiomDL(best));
             return Optional.of(best);
         } catch (RuntimeException ex) {
            ex.printStackTrace();
             return Optional.empty();
         }
     }
 }
