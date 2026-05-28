package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.WeakerAxiomStrategy;
import www.ontologyutils.toolbox.Ontology;

/**
 * Repair the given ontology using power indexes (like Shapley values) to select
 * bad axioms and weaker replacements.
 */
public class RepairWithPowerIndexes extends RepairApp {
    private static final Map<String, Integer> PRESET_WEAKENING_FLAGS = Map.of(
            "troquard2018", AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                    | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                    | AxiomWeakener.FLAG_OWL2_SET_OPERANDS,
            "confalonieri2020", AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                    | AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_OWL2_SET_OPERANDS,
            "bernard2023", AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_OWL2_SET_OPERANDS);

    private boolean coherence = false;
    private RefOntologyStrategy refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
    private BadAxiomStrategy badAxiomStrategy = BadAxiomStrategy.BANZHAF_APPROXIMATE;
    private WeakerAxiomStrategy weakerAxiomStrategy = WeakerAxiomStrategy.BANZHAF_APPROXIMATE;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private boolean enhanceRef = false;

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>();
        options.addAll(super.appOptions());
        options.add(OptionType.FLAG.create("coherence", b -> coherence = true, "make the ontology coherent"));
        options.add(OptionType.FLAG.create("power-index-shapley-exact", b -> {
            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_EXACT;
            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_EXACT;
        }, "use exact Shapley value for bad and weaker axiom selection"));
        options.add(OptionType.FLAG.create("power-index-shapley-approximate", b -> {
            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_APPROXIMATE;
            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_APPROXIMATE;
        }, "use approximate Shapley value for bad and weaker axiom selection"));
        options.add(OptionType.FLAG.create("power-index-banzhaf-approximate", b -> {
            badAxiomStrategy = BadAxiomStrategy.BANZHAF_APPROXIMATE;
            weakerAxiomStrategy = WeakerAxiomStrategy.BANZHAF_APPROXIMATE;
        }, "use approximate Banzhaf value for bad and weaker axiom selection"));
        options.add(OptionType.options(
                Map.of("intersect", RefOntologyStrategy.INTERSECTION_OF_MCS,
                        "intersect-of-some", RefOntologyStrategy.INTERSECTION_OF_SOME_MCS,
                        "largest", RefOntologyStrategy.LARGEST_MCS,
                        "any", RefOntologyStrategy.ONE_MCS,
                        "random", RefOntologyStrategy.RANDOM_MCS,
                        "random-of-some", RefOntologyStrategy.SOME_MCS))
                .create("ref-ontology", method -> refOntologyStrategy = method,
                        "method for reference ontology selection"));
        options.add(OptionType.options(
                Map.of("shapley-exact", BadAxiomStrategy.SHAPLEY_EXACT,
                        "shapley-approximate", BadAxiomStrategy.SHAPLEY_APPROXIMATE,
                        "banzhaf-approximate", BadAxiomStrategy.BANZHAF_APPROXIMATE))
                .create("bad-axiom", method -> badAxiomStrategy = method,
                        "method for bad axiom selection"));
        options.add(OptionType.options(
                Map.of("shapley-exact", WeakerAxiomStrategy.SHAPLEY_EXACT,
                        "shapley-approximate", WeakerAxiomStrategy.SHAPLEY_APPROXIMATE,
                        "banzhaf-approximate", WeakerAxiomStrategy.BANZHAF_APPROXIMATE))
                .create("weaker-axiom", method -> weakerAxiomStrategy = method,
                        "method for weaker axiom selection"));
        options.add(OptionType.FLAG.create("strict-nnf", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_NNF_STRICT;
        }, "accept and produce only NNF axioms"));
        options.add(OptionType.FLAG.create("strict-alc", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_ALC_STRICT;
        }, "accept and produce only ALC axioms"));
        options.add(OptionType.FLAG.create("strict-sroiq", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_SROIQ_STRICT;
        }, "accept and produce only SROIQ axioms"));
        options.add(OptionType.FLAG.create("strict-simple-roles", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT;
        }, "use only simple roles in upward and downward covers"));
        options.add(OptionType.FLAG.create("uncached", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_UNCACHED;
        }, "do not use any caches for the covers"));
        options.add(OptionType.FLAG.create("basic-cache", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_BASIC_CACHED;
        }, "use only a basic cache"));
        options.add(OptionType.FLAG.create("strict-owl2", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
        }, "do not produce intersection and union with a single operand"));
        options.add(OptionType.FLAG.create("simple-ria-weakening", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_RIA_ONLY_SIMPLE;
        }, "do not use the more advanced RIA weakening"));
        options.add(OptionType.FLAG.create("no-role-refinement", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_NO_ROLE_REFINEMENT;
        }, "do not refine roles in any context"));
        options.add(OptionType.FLAG.create("enhance-ref", b -> {
            enhanceRef = true;
        }, "keep the reference ontology as static axioms in the output"));
        options.add(OptionType.options(Map.of(
                "troquard2018", "troquard2018",
                "confalonieri2020", "confalonieri2020",
                "bernard2023", "bernard2023"))
                .create("preset", this::applyPreset, "configuration approximating description in papers"));
        return options;
    }

    private void applyPreset(String presetName) {
        // Presets now only configure weakening-related flags.
        weakeningFlags = PRESET_WEAKENING_FLAGS.getOrDefault(presetName, weakeningFlags);
    }

    @Override
    protected OntologyRepair getRepair() {
        var builder = coherence ? OntologyRepairBuilder.forCoherence() : OntologyRepairBuilder.forConsistency();
        builder.withRefStrategy(refOntologyStrategy);
        builder.withWeakeningFlags(weakeningFlags);
        builder.withEnhanceRef(enhanceRef);
        // Map power-index selection to PowerIndexType if requested
        switch (badAxiomStrategy) {
            case SHAPLEY_EXACT -> builder.withPowerIndex(www.ontologyutils.repair.powerindex.PowerIndexType.SHAPLEY_EXACT);
            case SHAPLEY_APPROXIMATE -> builder.withPowerIndex(www.ontologyutils.repair.powerindex.PowerIndexType.SHAPLEY_APPROXIMATE);
            case BANZHAF_APPROXIMATE -> builder.withPowerIndex(www.ontologyutils.repair.powerindex.PowerIndexType.BANZHAF_APPROXIMATE);
            default -> {
                // no-op: use default bad-axiom selector
            }
        }
        // For weaker strategy we prefer to use the same power-index type when applicable
        switch (weakerAxiomStrategy) {
            case SHAPLEY_EXACT, SHAPLEY_APPROXIMATE, BANZHAF_APPROXIMATE -> builder.withPowerIndex(www.ontologyutils.repair.powerindex.PowerIndexType.valueOf(weakerAxiomStrategy.name()));
            default -> {}
        }
        return builder.build();
    }

    /**
     * One argument must be given, corresponding to an OWL ontology file path. E.g.,
     * run with the parameter src/test/resources/inconsistent/leftpolicies.owl
     *
     * @param args
     *            Must contain a file path of an ontology.
     */
    public static void main(String[] args) {
        (new RepairWithPowerIndexes()).launch(args);
    }
}