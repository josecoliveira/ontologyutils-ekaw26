package www.ontologyutils.repair;

import java.util.HashMap;
import java.util.Map;

import www.ontologyutils.repair.strategy.ref.McsRefOntologySelector;
import www.ontologyutils.repair.strategy.ref.RefOntologySelector;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.DefaultBadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.PowerIndexBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.PowerIndexWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.RandomWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.powerindex.PowerIndexRegistry;
import www.ontologyutils.repair.powerindex.PowerIndexType;
import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.Ontology;

/**
 * Fluent builder for creating OntologyRepairWeakening instances using selector
 * strategies and power-index configuration.
 */
public class OntologyRepairBuilder {
    private java.util.function.Predicate<Ontology> isRepaired;
    private RefOntologySelector refSelector;
    private BadAxiomSelector badSelector;
    private WeakerAxiomSelector weakerSelector;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private boolean enhanceRef = true;
    private PowerIndexType powerIndexType;
    private Map<String, Object> powerIndexConfig = new HashMap<>();
    private int chunkSize = 50;
    private int maxSamples = 1000;

    private OntologyRepairBuilder(java.util.function.Predicate<Ontology> isRepaired) {
        this.isRepaired = isRepaired;
    }

    public static OntologyRepairBuilder forConsistency() {
        return new OntologyRepairBuilder(Ontology::isConsistent);
    }

    public static OntologyRepairBuilder forCoherence() {
        return new OntologyRepairBuilder(Ontology::isCoherent);
    }

    public OntologyRepairBuilder withRefSelector(RefOntologySelector selector) {
        this.refSelector = selector;
        return this;
    }

    public OntologyRepairBuilder withRefStrategy(OntologyRepairWeakening.RefOntologyStrategy strategy) {
        this.refSelector = new McsRefOntologySelector(strategy);
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

    public OntologyRepairBuilder withWeakerStrategyRandom() {
        this.weakerSelector = new RandomWeakerAxiomSelector();
        return this;
    }

    public OntologyRepairBuilder withPowerIndex(PowerIndexType type) {
        this.powerIndexType = type;
        return this;
    }

    public OntologyRepairBuilder withPowerIndexConfig(Map<String, Object> config) {
        this.powerIndexConfig = config != null ? config : Map.of();
        return this;
    }

    public OntologyRepairBuilder withPowerIndexSampling(int chunkSize, int maxSamples) {
        this.chunkSize = chunkSize;
        this.maxSamples = maxSamples;
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
        // defaults
        if (refSelector == null) {
            refSelector = new McsRefOntologySelector(OntologyRepairWeakening.RefOntologyStrategy.ONE_MCS);
        }
        if (badSelector == null) {
            if (powerIndexType != null) {
                var pi = PowerIndexRegistry.create(powerIndexType, powerIndexConfig);
                badSelector = new PowerIndexBadAxiomSelector(pi, chunkSize, maxSamples);
            } else {
                badSelector = new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS);
            }
        }
        if (weakerSelector == null) {
            if (powerIndexType != null) {
                var pi = PowerIndexRegistry.create(powerIndexType, powerIndexConfig);
                weakerSelector = new PowerIndexWeakerAxiomSelector(pi, chunkSize, maxSamples);
            } else {
                weakerSelector = new RandomWeakerAxiomSelector();
            }
        }

        return new OntologyRepairWeakening(isRepaired, refSelector, badSelector, weakerSelector, weakeningFlags, enhanceRef);
    }
}

