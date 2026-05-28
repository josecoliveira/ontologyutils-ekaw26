package www.ontologyutils.repair.strategy.weaker;

import java.util.Optional;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;

/**
 * Strategy interface for selecting the single best weaker axiom to replace a
 * bad axiom.
 */
public interface WeakerAxiomSelector {
    /**
     * Select the single best weakening for {@code badAxiom} using the provided
     * {@code weakener} and the current {@code context} of axioms.
     *
     * @param badAxiom the axiom to weaken
     * @param weakener the axiom weakener to compute candidates
     * @param context  the current set of refutable axioms (context)
     * @return Optional containing the chosen weakening, or empty if none
     */
    Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context);
}

