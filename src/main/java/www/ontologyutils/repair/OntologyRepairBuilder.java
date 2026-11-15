package www.ontologyutils.repair;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.DefaultBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.RandomWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;
import www.ontologyutils.toolbox.Ontology;

/**
 * Fluent builder for creating {@link OntologyRepairWeakening} instances.
 *
 * <p>
 * The two selection steps are configured independently, each with any
 * {@link BadAxiomSelector} or {@link WeakerAxiomSelector}. The builder knows
 * nothing about power indexes: a selection rule based on one is passed in like
 * any other, which keeps the two interfaces the only contract to implement.
 */
public class OntologyRepairBuilder {
    private java.util.function.Predicate<Ontology> isRepaired;
    private OntologyRepairWeakening.RefOntologyStrategy refOntologyStrategy =
            OntologyRepairWeakening.RefOntologyStrategy.ONE_MCS;
    private BadAxiomSelector badSelector;
    private WeakerAxiomSelector weakerSelector;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private boolean enhanceRef = true;

    private OntologyRepairBuilder(java.util.function.Predicate<Ontology> isRepaired) {
        this.isRepaired = isRepaired;
    }

    public static OntologyRepairBuilder forConsistency() {
        return new OntologyRepairBuilder(Ontology::isConsistent);
    }

    public static OntologyRepairBuilder forCoherence() {
        return new OntologyRepairBuilder(Ontology::isCoherent);
    }

    public OntologyRepairBuilder withRefStrategy(OntologyRepairWeakening.RefOntologyStrategy strategy) {
        this.refOntologyStrategy = strategy;
        return this;
    }

    public OntologyRepairBuilder withBadSelector(BadAxiomSelector selector) {
        this.badSelector = selector;
        return this;
    }

    public OntologyRepairBuilder withBadStrategy(OntologyRepairRemoval.BadAxiomStrategy strategy) {
        this.badSelector = new DefaultBadAxiomSelector(strategy);
        return this;
    }

    public OntologyRepairBuilder withWeakerSelector(WeakerAxiomSelector selector) {
        this.weakerSelector = selector;
        return this;
    }

    public OntologyRepairBuilder withWeakeningFlags(int flags) {
        this.weakeningFlags = flags;
        return this;
    }

    public OntologyRepairBuilder withEnhanceRef(boolean enhanceRef) {
        this.enhanceRef = enhanceRef;
        return this;
    }

    public OntologyRepair build() {
        var bad = badSelector != null
                ? badSelector
                : new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS);
        var weaker = weakerSelector != null ? weakerSelector : new RandomWeakerAxiomSelector();
        return new OntologyRepairWeakening(isRepaired, refOntologyStrategy, bad, weaker, weakeningFlags, enhanceRef);
    }
}
