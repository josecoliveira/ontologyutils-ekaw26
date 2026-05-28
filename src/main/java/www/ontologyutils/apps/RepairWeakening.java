package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.repair.OntologyRepairBuilder;

/**
 * Repair the given ontology using the axiom weakening repair algorithm.
 */
public class RepairWeakening extends RepairApp {
    private boolean coherence = false;
    private RefOntologyStrategy refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
    private BadAxiomStrategy badAxiomStrategy = BadAxiomStrategy.IN_SOME_MUS;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private boolean enhanceRef = false;

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>();
        options.addAll(super.appOptions());
        options.add(
                OptionType.FLAG.create("coherence", b -> coherence = true, "make the ontology coherent"));
        options.add(OptionType.FLAG.create("fast", b -> {
            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
            badAxiomStrategy = BadAxiomStrategy.IN_ONE_MUS;
        }, "use fast methods for selection"));
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
                Map.of("one-mus", BadAxiomStrategy.IN_ONE_MUS,
                        "some-mus", BadAxiomStrategy.IN_SOME_MUS,
                        "most-mus", BadAxiomStrategy.IN_MOST_MUS,
                        "least-mcs", BadAxiomStrategy.IN_LEAST_MCS,
                        "largest-mcs", BadAxiomStrategy.NOT_IN_LARGEST_MCS,
                        "one-mcs", BadAxiomStrategy.NOT_IN_ONE_MCS,
                        "some-mcs", BadAxiomStrategy.NOT_IN_SOME_MCS,
                        "random", BadAxiomStrategy.RANDOM))
                .create("bad-axiom", method -> badAxiomStrategy = method,
                        "method for bad axiom selection"));
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
        options.add(OptionType.options(
                Map.of("troquard2018", 2018,
                        "confalonieri2020", 2020,
                        "bernard2023", 2023))
                .create("preset", preset -> {
                    // Note that these are not exactly the configurations used in the papers.
                    if (preset == 2018) {
                        refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                        badAxiomStrategy = BadAxiomStrategy.IN_SOME_MUS;
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                                | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    } else if (preset == 2020) {
                        refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                        badAxiomStrategy = BadAxiomStrategy.IN_SOME_MUS;
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                | AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    } else if (preset == 2023) {
                        refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                        badAxiomStrategy = BadAxiomStrategy.IN_SOME_MUS;
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    }
                }, "configuration approximating description in papers"));
        return options;
    }

    @Override
    protected OntologyRepair getRepair() {
        var builder = coherence ? OntologyRepairBuilder.forCoherence() : OntologyRepairBuilder.forConsistency();
        builder.withRefStrategy(refOntologyStrategy).withBadStrategy(badAxiomStrategy).withWeakeningFlags(weakeningFlags)
                .withEnhanceRef(enhanceRef);
        return builder.build();
    }

    /**
     * One argument must be given, corresponding to an OWL ontology file path. E.g.,
     * run with the parameter
     * src/test/resources/inconsistent/leftpolicies.owl
     *
     * @param args
     *            Must contain one or two argument representing the keyword "fast"
     *            or file path of an ontology.
     */
    public static void main(String[] args) {
        (new RepairWeakening()).launch(args);
    }
}
