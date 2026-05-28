package www.ontologyutils.repair.strategy.weaker;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.Utils;
import www.ontologyutils.logging.GlobalLogger;

/**
 * Simple weaker-axiom selector that picks a random weakening.
 */
public class RandomWeakerAxiomSelector implements WeakerAxiomSelector {
    @Override
    public Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context) {
        try {
            var weakenings = weakener.weakerAxioms(badAxiom).collect(Collectors.toSet());
            weakenings.remove(badAxiom);
            if (weakenings.isEmpty()) {
                GlobalLogger.info("RandomWeakerAxiomSelector", "No weakenings found for axiom: " + Utils.prettyPrintAxiomDL(badAxiom));
                return Optional.empty();
            }
            var choice = Utils.randomChoice(weakenings);
            GlobalLogger.info("RandomWeakerAxiomSelector", "Selected weaker axiom: " + Utils.prettyPrintAxiomDL(choice));
            return Optional.of(choice);
        } catch (RuntimeException ex) {
            GlobalLogger.error("RandomWeakerAxiomSelector", "Error computing weakenings", ex);
            return Optional.empty();
        }
    }
}

