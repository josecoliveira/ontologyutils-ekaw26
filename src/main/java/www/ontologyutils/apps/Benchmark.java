package www.ontologyutils.apps;

import java.util.*;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLClassExpression;

import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.refinement.*;
import www.ontologyutils.toolbox.*;

/**
 * Small benchmark for computing repeated refinements on the same ontology.
 * Mainly useful for profiling and comparing performance of reasoners.
 */
public class Benchmark extends App {
    private String inputFile;
    private boolean normalizeSroiq = false;

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
        options.add(OptionType.FLAG.create('n', "normalize", b -> normalizeSroiq = true,
                "normalize the ontology before tests"));
        return options;
    }

    private void benchRun(Ontology ontology) {
        Utils.randomSeed(42);
        for (int i = 0; i < 10; i++) {
            var subConcepts = Utils.toSet(ontology.subConcepts());
            var subRoles = Utils.toSet(ontology.subRoles());
            var simpleRoles = Utils.toSet(ontology.simpleRoles());
            if (ontology.isConsistent()) {
                var covers = new Covers(ontology, subConcepts, subRoles, simpleRoles, 0);
                var refinement = new RefinementOperator(covers.upCover().cached(), covers.downCover().cached());
                for (int j = 0; j < 10; j++) {
                    OWLClassExpression toRefine = Ontology.getDefaultDataFactory().getOWLNothing();
                    for (int k = 0; k < 10; k++) {
                        toRefine = Utils.randomChoice(refinement.refine(toRefine));
                    }
                }
            } else {
                var mcs = Utils.randomChoice(ontology.someMaximalConsistentSubsets(Ontology::isConsistent));
                var refOnto = ontology.cloneWithRefutable(mcs);
                var covers = new Covers(refOnto, subConcepts, subRoles, simpleRoles, 0);
                var refinement = new RefinementOperator(covers.upCover().cached(), covers.downCover().cached());
                for (int j = 0; j < 10; j++) {
                    var badAxioms = ontology.someMinimalUnsatisfiableSubsets(Ontology::isConsistent)
                            .flatMap(s -> s.stream());
                    var badAxiom = Utils.randomChoice(badAxioms);
                    var toRefine = Utils.randomChoice(Stream.concat(badAxiom.nestedClassExpressions(),
                            Stream.of(Utils.randomChoice(subConcepts))));
                    for (int k = 0; k < 10; k++) {
                        toRefine = Utils.randomChoice(refinement.refine(toRefine));
                    }
                }
            }
        }
    }

    private void benchOntology(Ontology ontology) {
        var startTime = System.nanoTime();
        Ontology.reasonerCalls = 0;
        benchRun(ontology);
        var endTime = System.nanoTime();
        System.out.println((endTime - startTime) / 1_000_000 + " ms; " + Ontology.reasonerCalls + " reasoner calls");
    }

    @Override
    protected void run() {
        var ontology = Ontology.loadOntology(inputFile);
        System.err.println("Loaded...");
        if (normalizeSroiq) {
            var normalization = new SroiqNormalization(true, false);
            System.err.println("Normalizing to SROIQ...");
            normalization.apply(ontology);
        }
        System.err.println("** Benchmark: OPENLLET");
        try (var withOpenllet = ontology.cloneWithOpenllet()) {
            benchOntology(withOpenllet);
        } catch (Exception e) {
            System.err.println("ERROR... " + e);
            e.printStackTrace();
        }
        System.err.println("** Benchmark: FACT");
        try (var withFact = ontology.cloneWithJFact()) {
            benchOntology(withFact);
        } catch (Exception e) {
            System.out.println("ERROR... " + e);
            e.printStackTrace();
        }
        System.err.println("** Benchmark: HERMIT");
        try (var withHermit = ontology.cloneWithHermit()) {
            benchOntology(withHermit);
        } catch (Exception e) {
            System.err.println("ERROR... " + e);
            e.printStackTrace();
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
        (new Benchmark()).launch(args);
    }
}
