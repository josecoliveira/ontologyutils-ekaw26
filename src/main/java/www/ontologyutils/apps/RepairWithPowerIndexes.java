package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.WeakerAxiomStrategy;
import www.ontologyutils.toolbox.Ontology;

/**
 * Repair the given ontology using power indexes (like Shapley values) to select
 * bad axioms and weaker replacements.
 */
public class RepairWithPowerIndexes extends RepairApp {
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
        options.add(OptionType.options(
                Map.of("troquard2018-shapley-exact", "troquard2018-shapley-exact",
                        "troquard2018-shapley-approximate", "troquard2018-shapley-approximate",
                        "troquard2018-banzhaf-approximate", "troquard2018-banzhaf-approximate",
                        "confalonieri2020-shapley-exact", "confalonieri2020-shapley-exact",
                        "confalonieri2020-shapley-approximate", "confalonieri2020-shapley-approximate",
                        "bernard2023-shapley-exact", "bernard2023-shapley-exact",
                        "bernard2023-shapley-approximate", "bernard2023-shapley-approximate"))
                .create("preset", preset -> {
                    // Note that these are not exactly the configurations used in the papers.
                    switch (preset) {
                        case "troquard2018-shapley-exact" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_EXACT;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_EXACT;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                    | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                                    | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "troquard2018-shapley-approximate" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                    | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                                    | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "troquard2018-banzhaf-approximate" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.BANZHAF_APPROXIMATE;
                            weakerAxiomStrategy = WeakerAxiomStrategy.BANZHAF_APPROXIMATE;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                    | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                                    | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "confalonieri2020-shapley-exact" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_EXACT;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_EXACT;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                    | AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "confalonieri2020-shapley-approximate" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                                    | AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "bernard2023-shapley-exact" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_EXACT;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_EXACT;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                        case "bernard2023-shapley-approximate" -> {
                            refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                            badAxiomStrategy = BadAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakerAxiomStrategy = WeakerAxiomStrategy.SHAPLEY_APPROXIMATE;
                            weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                    | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                        }
                    }
                }, "configuration approximating description in papers"));
        return options;
    }

    @Override
    protected OntologyRepair getRepair() {
        // PowerIndex powerIndex = "exact".equals(powerIndexType)
        //         ? new ShapleyInconsistencyValueExact()
        //         : new ShapleyInconsistencyValueApproximate();

        // return new OntologyRepairWithPowerIndexes(
        //         coherence ? Ontology::isCoherent : Ontology::isConsistent,
        //         refOntologyStrategy,
        //         badAxiomStrategy,
        //         weakeningFlags,
        //         enhanceRef,
        //         powerIndex);
        return new OntologyRepairWithPowerIndexes(
                coherence ? Ontology::isCoherent : Ontology::isConsistent,
                refOntologyStrategy,
                badAxiomStrategy,
                weakerAxiomStrategy,
                weakeningFlags,
                enhanceRef
        );
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

