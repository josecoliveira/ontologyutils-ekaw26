package www.ontologyutils.apps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import openllet.owlapi.OpenlletReasonerFactory;
import uk.ac.manchester.cs.factplusplus.owlapi.FaCTPlusPlusReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;
import www.ontologyutils.refinement.AxiomStrengthener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.WeakerAxiomStrategy;
import www.ontologyutils.toolbox.*;

/**
 * Run repeated repair experiments on inconsistent ontologies and compare the
 * inferred information content of the resulting repairs.
 */
public class RepairComparisonExperiment extends App {
    private static final int TRIALS = 100;
    private static final long BASE_SEED = 13L;
    private static final long WEAKENING_TIMEOUT_SECONDS = 300L;
    private static final long POWER_INDEX_TIMEOUT_SECONDS = 600L;

    private List<String> inputFiles = new ArrayList<>();
    private OWLReasonerFactory reasonerFactory = new FaCTPlusPlusReasonerFactory();

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<Option<?>>(super.appOptions());
        options.add(OptionType.FILE.createDefault(file -> {
            inputFiles.add(file.toString());
        }, "the files containing the original ontologies"));
        options.add(OptionType.options(
                Map.of("hermit", new ReasonerFactory(),
                        "jfact", new JFactFactory(),
                        "openllet", OpenlletReasonerFactory.getInstance(),
                        "fact++", new FaCTPlusPlusReasonerFactory()))
                .create("reasoner", r -> reasonerFactory = r, "the reasoner to use"));
        return options;
    }

    private OntologyRepairWeakening createWeakeningRepair() {
        return new OntologyRepairWeakening(Ontology::isConsistent, RefOntologyStrategy.ONE_MCS,
                OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS,
                AxiomStrengthener.FLAG_SROIQ_STRICT | AxiomStrengthener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomStrengthener.FLAG_RIA_ONLY_SIMPLE | AxiomStrengthener.FLAG_ALC_STRICT
                        | AxiomStrengthener.FLAG_NO_ROLE_REFINEMENT | AxiomStrengthener.FLAG_OWL2_SET_OPERANDS,
                false);
    }

    private OntologyRepairWithPowerIndexes createPowerIndexRepair() {
        return new OntologyRepairWithPowerIndexes(Ontology::isConsistent,
                www.ontologyutils.repair.OntologyRepairWithPowerIndexes.RefOntologyStrategy.ONE_MCS,
                BadAxiomStrategy.SHAPLEY_APPROXIMATE, WeakerAxiomStrategy.SHAPLEY_APPROXIMATE,
                AxiomStrengthener.FLAG_SROIQ_STRICT | AxiomStrengthener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomStrengthener.FLAG_RIA_ONLY_SIMPLE | AxiomStrengthener.FLAG_ALC_STRICT
                        | AxiomStrengthener.FLAG_NO_ROLE_REFINEMENT | AxiomStrengthener.FLAG_OWL2_SET_OPERANDS,
                false);
    }

    private void makeInconsistent(Ontology ontology, long seed) {
        Utils.randomSeed(seed);
        if (!ontology.isConsistent()) {
            return;
        }
        if (Utils.toList(ontology.logicalAxioms()).isEmpty()) {
            throw new IllegalStateException("Cannot make an ontology inconsistent when it has no logical axioms.");
        }
        try (var emptyOntology = ontology.cloneOnlyStatic()) {
            var axiomStrengthener = new AxiomStrengthener(ontology, AxiomStrengthener.FLAG_DEFAULT);
            while (ontology.isConsistent()) {
                OWLAxiom axiom = Utils.randomChoice(ontology.logicalAxioms());
                var strongerAxioms = Utils.toSet(axiomStrengthener.strongerAxioms(axiom));
                strongerAxioms.removeAll(new HashSet<>(Utils.toList(ontology.axioms())));

                var tooStrong = new HashSet<OWLAxiom>();
                for (var strongerAxiom : strongerAxioms) {
                    emptyOntology.addAxioms(strongerAxiom);
                    if (!emptyOntology.isConsistent()) {
                        tooStrong.add(strongerAxiom);
                    }
                    emptyOntology.removeAxioms(strongerAxiom);
                }
                strongerAxioms.removeAll(tooStrong);

                var tautologies = new HashSet<OWLAxiom>();
                for (var strongerAxiom : strongerAxioms) {
                    if (emptyOntology.isEntailed(strongerAxiom)) {
                        tautologies.add(strongerAxiom);
                    }
                }
                strongerAxioms.removeAll(tautologies);

                if (!strongerAxioms.isEmpty()) {
                    ontology.addAxioms(Utils.randomChoice(strongerAxioms));
                }
            }
        }
    }

    private Set<OWLClassExpression> collectSubConcepts(Ontology... ontologies) {
        var df = Ontology.getDefaultDataFactory();
        var subConcepts = new HashSet<OWLClassExpression>();
        subConcepts.add(df.getOWLThing());
        subConcepts.add(df.getOWLNothing());
        for (var ontology : ontologies) {
            subConcepts.addAll(Utils.toList(ontology.conceptsInSignature()));
        }
        return subConcepts;
    }

    private Set<OWLAxiom> inferredAxioms(Ontology ontology, Set<OWLClassExpression> subConcepts) {
        return Utils.toSet(ontology.inferredSubClassAxiomsOver(subConcepts));
    }

    private void applyRepairWithTimeout(OntologyRepair repair, Ontology ontology, String repairName,
            long timeoutSeconds) throws TimeoutException {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "repair-thread-" + repairName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            var future = executor.submit(() -> repair.apply(ontology));
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw Utils.panic(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private void writeCsvLine(Path file, double v1, double v2) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(Double.toString(v1));
            writer.write(",");
            writer.write(Double.toString(v2));
            writer.newLine();
        } catch (IOException e) {
            throw Utils.panic(e);
        }
    }

    @Override
    protected void run() {
        var startTime = System.nanoTime();
        try {
            Files.createDirectories(Path.of("output"));
        } catch (IOException e) {
            throw Utils.panic(e);
        }

        if (inputFiles.isEmpty()) {
            throw new IllegalArgumentException("No input files specified");
        }

        for (var input : inputFiles) {
            // Extract ontology filename (without path and extension)
            var ontologyFileName = new File(input).getName();
            ontologyFileName = ontologyFileName.replaceAll("\\.owl$", "");
            var csvFile = Path.of("output", "iic-" + ontologyFileName + ".csv");

            logMessage("Running experiments for " + input);
            logMessage("CSV output: " + csvFile);

            try (var ontology = Ontology.loadOntology(input, reasonerFactory)) {
                logMessage("Loaded...");
                var weakeningRepair = createWeakeningRepair();
                var powerIndexRepair = createPowerIndexRepair();

                int successfulTrials = 0;
                int attemptedTrials = 0;

                while (successfulTrials < TRIALS) {
                    int trialIndex = successfulTrials + 1;
                    logMessage("Trial " + trialIndex + "/" + TRIALS + " (file: " + ontologyFileName + ")");
                    var trialSeed = BASE_SEED + (attemptedTrials * 3L);

                    try (var inconsistent = ontology.cloneWithSeparateCache()) {
                        logMessage("  Making ontology inconsistent...");
                        makeInconsistent(inconsistent, trialSeed);
                        boolean trialSucceeded = false;

                        try (var repairedOntologyWithWeakening = inconsistent.cloneWithSeparateCache()) {
                            try {
                                logMessage("  Repairing with weakening (troquard2018)...");
                                Utils.randomSeed(trialSeed + 1);
                                applyRepairWithTimeout(weakeningRepair, repairedOntologyWithWeakening, "weakening",
                                        WEAKENING_TIMEOUT_SECONDS);

                                try (var repairedOntologyWithPowerIndex = inconsistent.cloneWithSeparateCache()) {
                                    try {
                                        logMessage("  Repairing with power indexes (troquard2018-shapley-approximate)...");
                                        Utils.randomSeed(trialSeed + 2);
                                        applyRepairWithTimeout(powerIndexRepair, repairedOntologyWithPowerIndex,
                                                "power-index", POWER_INDEX_TIMEOUT_SECONDS);

                                        var subConcepts = collectSubConcepts(repairedOntologyWithWeakening,
                                                repairedOntologyWithPowerIndex);
                                        var inferredWeakening = inferredAxioms(repairedOntologyWithWeakening, subConcepts);
                                        var inferredPowerIndex = inferredAxioms(repairedOntologyWithPowerIndex,
                                                subConcepts);

                                        var iicShapley = Ontology.relativeInformationContent(inferredPowerIndex,
                                                inferredWeakening);
                                        var iicWeakening = Ontology.relativeInformationContent(inferredWeakening,
                                                inferredPowerIndex);

                                        writeCsvLine(csvFile, iicShapley, iicWeakening);

                                        logMessage("  IIC (Shapley wrt Weakening): " + iicShapley);
                                        logMessage("  IIC (Weakening wrt Shapley): " + iicWeakening);
                                        trialSucceeded = true;

                                    } catch (TimeoutException e) {
                                        logMessage("  TIMEOUT: Power index repair exceeded " + POWER_INDEX_TIMEOUT_SECONDS
                                                + " seconds. Aborting trial and retrying.");
                                    }
                                }
                            } catch (TimeoutException e) {
                                logMessage("  TIMEOUT: Weakening repair exceeded " + WEAKENING_TIMEOUT_SECONDS
                                        + " seconds. Aborting trial and retrying.");
                            }
                        }

                        if (trialSucceeded) {
                            successfulTrials++;
                        }
                    }

                    attemptedTrials++;
                }
            }
        }


        var endTime = System.nanoTime();
        logMessage("Done. (" + (endTime - startTime) / 1_000_000 + " ms; " + Ontology.reasonerCalls
                + " reasoner calls)");
    }

    /**
     * @param args
     *            Must contain one argument representing the file path of an
     *            ontology.
     */
    public static void main(String[] args) {
        (new RepairComparisonExperiment()).launch(args);
    }
}



