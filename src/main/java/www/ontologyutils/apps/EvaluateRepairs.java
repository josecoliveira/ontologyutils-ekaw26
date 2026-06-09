package www.ontologyutils.apps;

import java.util.*;
import java.util.stream.Stream;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import openllet.owlapi.OpenlletReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;
import www.ontologyutils.toolbox.*;

/**
 * Evaluate a set of consistent ontologies based on their inferred information
 * content.
 */
public class EvaluateRepairs extends App {
    private List<String> inputFiles = new ArrayList<>();
    private boolean extended = false;
    private boolean iicPairs = false;
    private OWLReasonerFactory reasonerFactory = new ReasonerFactory();

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>();
        options.addAll(super.appOptions());
        options.add(OptionType.FILE.createDefault(file -> {
            inputFiles.add(file.toString());
        }, "the files containing the ontologies"));
        options.add(OptionType.FLAG.create("extended", b -> extended = true,
                "compute both simple and extended iic"));
        options.add(OptionType.FLAG.create("iic-pairs", b -> iicPairs = true,
                "compute iic between all pars"));
        options.add(OptionType.options(
                Map.of("hermit", new ReasonerFactory(),
                        "jfact", new JFactFactory(),
                        "openllet", OpenlletReasonerFactory.getInstance()))
                .create("reasoner", r -> reasonerFactory = r, "the reasoner to use"));
        return options;
    }

    @Override
    protected void run() {
        var startTime = System.nanoTime();
        var df = Ontology.getDefaultDataFactory();
        var subConcepts = new HashSet<OWLClassExpression>();
        var subRoles = new HashSet<OWLObjectPropertyExpression>();
        subConcepts.add(df.getOWLThing());
        subConcepts.add(df.getOWLNothing());
        for (var file : inputFiles) {
            try (var ontology = Ontology.loadOntology(file, reasonerFactory)) {
                if (extended) {
                    subConcepts.addAll(Utils.toList(ontology.subConcepts()));
                    subRoles.addAll(Utils.toList(ontology.subRoles()));
                } else {
                    subConcepts.addAll(Utils.toList(ontology.conceptsInSignature()));
                }
            }
        }
        var inferred = new HashMap<String, Set<OWLAxiom>>();
        for (var file : inputFiles) {
            try (var ontology = Ontology.loadOntology(file, reasonerFactory)) {
                assert ontology.isConsistent();
                inferred.put(file, Utils.toSet(Stream.concat(
                        ontology.inferredSubClassAxiomsOver(subConcepts),
                        ontology.inferredSubRoleAxiomsOver(subRoles))));
            }
        }
        if (iicPairs) {
            inferred.forEach((name1, inf1) -> {
                inferred.forEach((name2, inf2) -> {
                    System.out.println(name1 + ";" + inf1.size() + ";" + name2 + ";" + inf2.size() + ";"
                            + Ontology.relativeInformationContent(inf1, inf2));
                });
            });
        } else {
            inferred.forEach((name, inf) -> {
                System.out.println(name + ";" + inf.size());
            });
        }
        var endTime = System.nanoTime();
        System.err.println(
                "Done. (" + (endTime - startTime) / 1_000_000 + " ms; " + Ontology.reasonerCalls + " reasoner calls)");
    }

    /**
     * @param args
     *            Must contain one argument representing the file path of an
     *            ontology.
     */
    public static void main(String[] args) {
        (new EvaluateRepairs()).launch(args);
    }
}
