package www.ontologyutils.apps;

import java.util.*;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import openllet.owlapi.OpenlletReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;
import www.ontologyutils.refinement.*;
import www.ontologyutils.toolbox.*;

/**
 * Small benchmark for cache effectiveness.
 */
public class BenchCache extends App {
    private String inputFile;
    private int runs;
    private int groupSize;
    private int weakeningFlags = AxiomWeakener.FLAG_DEFAULT;
    private OWLReasonerFactory reasonerFactory = new ReasonerFactory();

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>();
        options.addAll(super.appOptions());
        options.add(OptionType.FILE.createDefault(file -> {
            if (inputFile != null) {
                throw new IllegalArgumentException("multiple input files specified");
            }
            inputFile = file.toString();
        }, "the file containing the original ontology"));
        options.add(OptionType.INT.create('n', "runs", n -> runs = n,
                "the number of runs to execute"));
        options.add(OptionType.INT.create('s', "size", n -> groupSize = n,
                "the number of axioms in each group"));
        options.add(OptionType.FLAG.create("uncached", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_UNCACHED;
        }, "do not use any caches for the covers"));
        options.add(OptionType.FLAG.create("basic-cache", b -> {
            weakeningFlags |= AxiomWeakener.FLAG_BASIC_CACHED;
        }, "use only a basic cache"));
        options.add(OptionType.options(
                Map.of("hermit", new ReasonerFactory(),
                        "jfact", new JFactFactory(),
                        "openllet", OpenlletReasonerFactory.getInstance()))
                .create("reasoner", r -> reasonerFactory = r, "the reasoner to use"));
        options.add(OptionType.options(
                Map.of("troquard2018", 2018,
                        "confalonieri2020", 2020,
                        "bernard2023", 2023))
                .create("preset", preset -> {
                    // Note that these are not exactly the configurations used in the papers.
                    if (preset == 2018) {
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_NNF_STRICT
                                | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                                | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    } else if (preset == 2020) {
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_NNF_STRICT
                                | AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    } else if (preset == 2023) {
                        weakeningFlags = AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                                | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
                    }
                }, "configuration approximating description in papers"));
        return options;
    }

    @Override
    protected void run() {
        var ontology = Ontology.loadOntology(inputFile, reasonerFactory);
        for (int r = 0; r < runs; r++) {
            try (var onto = ontology.cloneWithSeparateCache()) {
                var weakener = new AxiomWeakener(onto, weakeningFlags);
                var startTime = System.nanoTime();
                Ontology.reasonerCalls = 0;
                for (int i = 0; i < groupSize; i++) {
                    var toWeaken = Utils.randomChoice(ontology.logicalAxioms());
                    var weaker = Utils.toSet(weakener.weakerAxioms(toWeaken));
                    System.err.println("Weakened axiom. (" + weaker.size() + " possible weakenings)");
                }
                var endTime = System.nanoTime();
                System.err.println(
                        "Done. (" + (endTime - startTime) / 1_000_000 + " ms; " + Ontology.reasonerCalls
                                + " reasoner calls)");
            }
        }
        ontology.close();
    }

    /**
     * One argument must be given, corresponding to an OWL ontology file path. E.g.,
     * run with the parameter
     * src/test/resources/www/ontologyutils/inconsistent-leftpolicies.owl
     *
     * @param args
     *            Must contain one argument representing the file path of an
     *            ontology.
     */
    public static void main(String[] args) {
        (new BenchCache()).launch(args);
    }
}
