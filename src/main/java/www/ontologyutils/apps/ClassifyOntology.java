package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.toolbox.*;

/**
 * Check the different OWL2 profiles against the given ontology. Outputting the
 * report results.
 */
public class ClassifyOntology extends App {
    private String inputFile;

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
        return options;
    }

    @Override
    public void run() {
        // No reasoner is selected: everything printed below is computed from the
        // axioms alone, and instantiating HermiT here would make the run fail for
        // ontologies it refuses to normalise (SWRL rules with built-in atoms,
        // non-simple properties in cardinality restrictions).
        var ontology = Ontology.loadOntology(inputFile);
        System.out.println("Axioms: " + ontology.logicalAxioms().count() + "; Concept names: "
                + ontology.conceptsInSignature().count() + "; Role names: " + ontology.rolesInSignature().count()
                + "; Subconcepts: " + ontology.subConcepts().count());
        // The DL expressivity is printed before the profile check because that is
        // what the ontology filter uses. The profile reports are informational only,
        // so a failure there must not discard the classification.
        System.out.print("DL languages: ");
        ontology.checkDlExpressivity().forEach(language -> {
            System.out.print(language.name() + "; ");
        });
        System.out.println();
        System.out.print("OWL 2 profiles: ");
        try {
            var reports = ontology.checkOwlProfiles();
            for (var report : reports) {
                if (!report.isInProfile() && report.getProfile().getName().endsWith("DL")) {
                    System.out.print(report);
                }
            }
            for (var report : reports) {
                if (report.isInProfile()) {
                    System.out.print(report.getProfile().getName() + "; ");
                }
            }
        } catch (RuntimeException e) {
            System.out.print("check failed: " + e);
        }
        System.out.println();
        ontology.close();
    }

    /**
     * One argument must be given, corresponding to an OWL ontology file path. E.g.,
     * run with the parameter resources/inconsistent-leftpolicies-small.owl
     *
     * @param args
     *            Must contain one argument representing the file path of an
     *            ontology.
     */
    public static void main(String[] args) {
        (new ClassifyOntology()).launch(args);
    }
}
