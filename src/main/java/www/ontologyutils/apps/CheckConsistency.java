package www.ontologyutils.apps;

import java.util.*;

import www.ontologyutils.toolbox.Ontology;

/**
 * Class for checking the consistency of an ontology. Checks will be performed
 * using all supported reasoners.
 */
public class CheckConsistency extends App {
    private String inputFile;
    private boolean basic;

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
        options.add(OptionType.FLAG.create("basic", b -> basic = true,
                "no output, exit with 0 for consistent, 1 for inconsistent"));
        return options;
    }

    private static void printResult(boolean consistent) {
        if (consistent) {
            System.out.println("-> consistent");
        } else {
            System.out.println("-> inconsistent");
        }
    }

    @Override
    public void run() {
        try (var ontology = Ontology.loadOntology(inputFile)) {
            if (basic) {
                System.exit(ontology.isConsistent() ? 0 : 1);
            }
            System.err.println("** Consistency check: HERMIT");
            try (var withHermit = ontology.cloneWithHermit()) {
                printResult(withHermit.isConsistent());
            } catch (Exception e) {
                System.err.println("ERROR... " + e);
            }
            System.err.println("** Consistency check: FACT");
            try (var withFact = ontology.cloneWithJFact()) {
                printResult(withFact.isConsistent());
            } catch (Exception e) {
                System.out.println("ERROR... " + e);
            }
            System.err.println("** Consistency check: OPENLLET");
            try (var withOpenllet = ontology.cloneWithOpenllet()) {
                printResult(withOpenllet.isConsistent());
            } catch (Exception e) {
                System.err.println("ERROR... " + e);
            }
        }
    }

    /**
     * @param args
     *            Must contain one argument representing the file path of an
     *            ontology.
     */
    public static void main(String[] args) {
        (new CheckConsistency()).launch(args);
    }
}
