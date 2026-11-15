package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.BanzhafApproxBadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.BanzhafBadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.ShapleyApproxBadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.ShapleyBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.BanzhafApproxWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.BanzhafWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.ShapleyApproxWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.ShapleyWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;
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

    /**
     * The inconsistency-value notions this app offers for each of the two stages.
     */
    private enum Method {
        SHAPLEY_EXACT,
        SHAPLEY_APPROX,
        BANZHAF_EXACT,
        BANZHAF_APPROX
    }

    private boolean coherence = false;
    private RefOntologyStrategy refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
    private Method badAxiomStrategy = Method.BANZHAF_APPROX;
    private Method weakerAxiomStrategy = Method.BANZHAF_APPROX;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private boolean enhanceRef = false;

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>();
        options.addAll(super.appOptions());
        options.add(OptionType.FLAG.create("coherence", b -> coherence = true, "make the ontology coherent"));
        options.add(OptionType.FLAG.create("power-index-shapley-exact", b -> {
            badAxiomStrategy = Method.SHAPLEY_EXACT;
            weakerAxiomStrategy = Method.SHAPLEY_EXACT;
        }, "use exact Shapley value for bad and weaker axiom selection"));
        options.add(OptionType.FLAG.create("power-index-shapley-approx", b -> {
            badAxiomStrategy = Method.SHAPLEY_APPROX;
            weakerAxiomStrategy = Method.SHAPLEY_APPROX;
        }, "use approximate Shapley value for bad and weaker axiom selection"));
        options.add(OptionType.FLAG.create("power-index-banzhaf-exact", b -> {
            badAxiomStrategy = Method.BANZHAF_EXACT;
            weakerAxiomStrategy = Method.BANZHAF_EXACT;
        }, "use exact Banzhaf value for bad and weaker axiom selection"));
        options.add(OptionType.FLAG.create("power-index-banzhaf-approx", b -> {
            badAxiomStrategy = Method.BANZHAF_APPROX;
            weakerAxiomStrategy = Method.BANZHAF_APPROX;
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
                Map.of("shapley-exact", Method.SHAPLEY_EXACT,
                        "shapley-approx", Method.SHAPLEY_APPROX,
                        "banzhaf-exact", Method.BANZHAF_EXACT,
                        "banzhaf-approx", Method.BANZHAF_APPROX))
                .create("bad-axiom", method -> badAxiomStrategy = method,
                        "method for bad axiom selection"));
        options.add(OptionType.options(
                Map.of("shapley-exact", Method.SHAPLEY_EXACT,
                        "shapley-approx", Method.SHAPLEY_APPROX,
                        "banzhaf-exact", Method.BANZHAF_EXACT,
                        "banzhaf-approx", Method.BANZHAF_APPROX))
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
        builder.withRefStrategy(refOntologyStrategy)
                .withBadSelector(badAxiomSelector(badAxiomStrategy))
                .withWeakerSelector(weakerAxiomSelector(weakerAxiomStrategy))
                .withWeakeningFlags(weakeningFlags)
                .withEnhanceRef(enhanceRef);
        return builder.build();
    }

    private static BadAxiomSelector badAxiomSelector(Method method) {
        return switch (method) {
            case SHAPLEY_EXACT -> new ShapleyBadAxiomSelector();
            case SHAPLEY_APPROX -> new ShapleyApproxBadAxiomSelector();
            case BANZHAF_EXACT -> new BanzhafBadAxiomSelector();
            case BANZHAF_APPROX -> new BanzhafApproxBadAxiomSelector();
        };
    }

    private static WeakerAxiomSelector weakerAxiomSelector(Method method) {
        return switch (method) {
            case SHAPLEY_EXACT -> new ShapleyWeakerAxiomSelector();
            case SHAPLEY_APPROX -> new ShapleyApproxWeakerAxiomSelector();
            case BANZHAF_EXACT -> new BanzhafWeakerAxiomSelector();
            case BANZHAF_APPROX -> new BanzhafApproxWeakerAxiomSelector();
        };
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