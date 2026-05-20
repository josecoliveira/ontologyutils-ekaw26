package www.ontologyutils.apps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import openllet.owlapi.OpenlletReasonerFactory;
import uk.ac.manchester.cs.factplusplus.owlapi.FaCTPlusPlusReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;
import www.ontologyutils.normalization.SroiqNormalization;
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
    private static final int CONSECUTIVE_FAILURE_CAP = 3;
    private static final long BASE_SEED = 13L;
    private static final long REMOVAL_TIMEOUT_SECONDS = 300L;
    private static final long WEAKENING_TIMEOUT_SECONDS = 300L;
    private static final long POWER_INDEX_TIMEOUT_SECONDS = 60L;
    private static final long MAKE_INCONSISTENT_TIMEOUT_SECONDS = 300L;

    private final List<String> inputFiles = new ArrayList<>();
    private OWLReasonerFactory reasonerFactory = new FaCTPlusPlusReasonerFactory();
    private int initialSuccessfulTrials = 0;
    private int initialAttemptedTrials = 0;

    @Override
    protected List<Option<?>> appOptions() {
        var options = new ArrayList<>(super.appOptions());
        options.add(OptionType.FILE.createDefault(file -> inputFiles.add(file.toString()),
                "the files containing the original ontologies"));
        options.add(OptionType.options(
                Map.of("hermit", new ReasonerFactory(),
                        "jfact", new JFactFactory(),
                        "openllet", OpenlletReasonerFactory.getInstance(),
                        "fact++", new FaCTPlusPlusReasonerFactory()))
                .create("reasoner", r -> reasonerFactory = r, "the reasoner to use"));
        options.add(OptionType.UINT.create("start-successful-trials", n -> initialSuccessfulTrials = n,
                "successful trials already completed for the current ontology"));
        options.add(OptionType.UINT.create("start-attempted-trials", n -> initialAttemptedTrials = n,
                "attempted trials already consumed for the current ontology"));
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

    private OntologyRepairRemoval createRandomRemovalRepair() {
        return new OntologyRepairRemoval(Ontology::isConsistent, OntologyRepairRemoval.BadAxiomStrategy.RANDOM);
    }

    private OntologyRepairRemoval createLargestMcsRemovalRepair() {
        return new OntologyRepairRemoval(Ontology::isConsistent,
                OntologyRepairRemoval.BadAxiomStrategy.NOT_IN_LARGEST_MCS);
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

    private void logOntologySummary(Ontology ontology) {
        try (var classifiedOntology = ontology.cloneWithHermit()) {
            logMessage("Ontology summary after normalization:");
            logMessage("Axioms: " + classifiedOntology.logicalAxioms().count() + "; Concept names: "
                    + classifiedOntology.conceptsInSignature().count() + "; Role names: "
                    + classifiedOntology.rolesInSignature().count() + "; Subconcepts: "
                    + classifiedOntology.subConcepts().count());

            var reports = classifiedOntology.checkOwlProfiles();
            var owl2Profiles = new StringBuilder();
            for (var report : reports) {
                if (!report.isInProfile() && report.getProfile().getName().endsWith("DL")) {
                    logMessage(report.toString());
                }
            }
            for (var report : reports) {
                if (report.isInProfile()) {
                    owl2Profiles.append(report.getProfile().getName()).append("; ");
                }
            }
            logMessage("OWL 2 profiles: " + owl2Profiles);

            var dlLanguages = new StringBuilder();
            classifiedOntology.checkDlExpressivity().forEach(language -> dlLanguages.append(language.name()).append("; "));
            logMessage("DL languages: " + dlLanguages);
        }
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

    private String extractErrorMessage(Throwable e) {
        if (e.getCause() != null) {
            String causeMsg = e.getCause().getMessage();
            String causeClass = e.getCause().getClass().getSimpleName();
            if (causeMsg != null && causeMsg.contains("Could not weaken")) {
                return "Repair failed: " + causeMsg;
            }
            return "Repair failed: " + causeClass + (causeMsg != null ? ": " + causeMsg : "");
        }
        return "Repair failed: " + e.getClass().getSimpleName();
    }

    private boolean isCancellationThrowable(Throwable e) {
        var current = e;
        while (current != null) {
            if (current instanceof CanceledException
                    || current instanceof InterruptedException
                    || current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void applyRepairWithTimeout(Supplier<? extends OntologyRepair> repairSupplier, Ontology ontology,
            String repairName, long timeoutSeconds, long seed) throws TimeoutException {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "repair-thread-" + repairName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            var future = executor.submit(() -> {
                Utils.randomSeed(seed);
                repairSupplier.get().apply(ontology);
                return null;
            });
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException("Repair timeout: " + repairName + " exceeded " + timeoutSeconds + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CanceledException();
        } catch (ExecutionException e) {
            if (isCancellationThrowable(e)) {
                Thread.currentThread().interrupt();
                throw new CanceledException();
            }
            // Convert ExecutionException to TimeoutException to be handled uniformly as retry trigger
            TimeoutException retryTrigger = new TimeoutException(extractErrorMessage(e));
            retryTrigger.initCause(e);
            throw retryTrigger;
        } finally {
            executor.shutdownNow();
            awaitRepairTermination(executor, repairName);
        }
    }

    private void applyMakeInconsistentWithTimeout(Ontology ontology, long seed) throws TimeoutException {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "repair-thread-make-inconsistent");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var future = executor.submit(() -> {
                Utils.randomSeed(seed);
                makeInconsistent(ontology, seed);
                return null;
            });
            try {
                future.get(MAKE_INCONSISTENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException(
                        "Make-inconsistent timeout: exceeded " + MAKE_INCONSISTENT_TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CanceledException();
        } catch (ExecutionException e) {
            if (isCancellationThrowable(e)) {
                Thread.currentThread().interrupt();
                throw new CanceledException();
            }
            TimeoutException retryTrigger = new TimeoutException(extractErrorMessage(e));
            retryTrigger.initCause(e);
            throw retryTrigger;
        } finally {
            executor.shutdownNow();
            awaitRepairTermination(executor, "make-inconsistent");
        }
    }

    private void awaitRepairTermination(ExecutorService executor, String repairName) {
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logMessage("  WARNING: Repair thread for " + repairName
                        + " did not terminate promptly after cancellation.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CanceledException();
        }
    }

    private void createCsvFileWithHeader(Path file) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("iic_power_vs_random,iic_power_vs_not_in_largest_mcs,iic_power_vs_weakening");
            writer.newLine();
        } catch (IOException e) {
            throw Utils.panic(e);
        }
    }

    private void writeCsvLine(Path file, double v1, double v2, double v3) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(Double.toString(v1));
            writer.write(",");
            writer.write(Double.toString(v2));
            writer.write(",");
            writer.write(Double.toString(v3));
            writer.newLine();
        } catch (IOException e) {
            throw Utils.panic(e);
        }
    }

    private Path createTimestampedCsvPath(String ontologyFileName, String runId) {
        var base = Path.of("output", "iic-" + ontologyFileName + "-" + runId + ".csv");
        if (!Files.exists(base)) {
            return base;
        }
        int index = 1;
        while (true) {
            var candidate = Path.of("output", "iic-" + ontologyFileName + "-" + runId + "-" + index + ".csv");
            if (!Files.exists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    @Override
    protected void run() {
        var startTime = System.nanoTime();
        var startDate = new Date();
        var runId = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(startDate);
        try {
            Files.createDirectories(Path.of("output"));
        } catch (IOException e) {
            throw Utils.panic(e);
        }

        if (inputFiles.isEmpty()) {
            throw new IllegalArgumentException("No input files specified");
        }

        boolean abortedByUser = false;
        for (var input : inputFiles) {
            var ontologyStartTime = System.nanoTime();
            // Extract ontology filename (without path and extension)
            var ontologyFileName = new File(input).getName();
            ontologyFileName = ontologyFileName.replaceAll("\\.owl$", "");
            var csvFile = createTimestampedCsvPath(ontologyFileName, runId);
            createCsvFileWithHeader(csvFile);

            logMessage("Running experiments for " + input);
            logMessage("CSV output: " + csvFile);

            try (var ontology = Ontology.loadOntology(input, reasonerFactory)) {
                logMessage("Loaded...");
                logMessage("Normalizing ontology with SROIQ normalization...");
                new SroiqNormalization(true, false).apply(ontology);
                logMessage("Normalized ontology with SROIQ normalization.");
                logOntologySummary(ontology);

                int successfulTrials = initialSuccessfulTrials;
                int attemptedTrials = initialAttemptedTrials;
                int consecutiveFailures = 0;
                boolean abortedEarly = false;

                if (successfulTrials > 0 || attemptedTrials > 0) {
                    logMessage("Resuming from successfulTrials=" + successfulTrials + ", attemptedTrials="
                            + attemptedTrials + " for " + ontologyFileName);
                }

                while (successfulTrials < TRIALS) {
                    try (var inconsistent = ontology.cloneWithSeparateCache()) {
                        int trialIndex = successfulTrials + 1;
                        logMessage("Trial " + trialIndex + "/" + TRIALS + " (file: " + ontologyFileName + ")");
                        var trialSeed = BASE_SEED + (attemptedTrials * 5L);

                        logMessage("  Making ontology inconsistent...");
                        try {
                            applyMakeInconsistentWithTimeout(inconsistent, trialSeed);
                        } catch (TimeoutException e) {
                            String msg = e.getMessage();
                            logMessage("  " + (msg != null ? msg : "Make-inconsistent failed (no error details)"));
                            consecutiveFailures++;
                            if (consecutiveFailures >= CONSECUTIVE_FAILURE_CAP) {
                                throw new IllegalStateException(
                                        "Make-inconsistent step failed " + consecutiveFailures
                                                + " times in a row. Aborting trials for this ontology.");
                            }
                            // abort this trial and retry with a fresh seed
                            attemptedTrials++;
                            continue;
                        }

                        boolean trialSucceeded = false;

                        try (var repairedOntologyWithRandomRemoval = inconsistent.cloneWithSeparateCache()) {
                            try {
                                logMessage("  Repairing with removal (random)...");
                                applyRepairWithTimeout(this::createRandomRemovalRepair, repairedOntologyWithRandomRemoval,
                                        "removal-random", REMOVAL_TIMEOUT_SECONDS, trialSeed + 1);

                                try (var repairedOntologyWithLargestMcsRemoval = inconsistent.cloneWithSeparateCache()) {
                                    try {
                                        logMessage("  Repairing with removal (not-in-largest-mcs)...");
                                        applyRepairWithTimeout(this::createLargestMcsRemovalRepair,
                                                repairedOntologyWithLargestMcsRemoval, "removal-not-in-largest-mcs",
                                                REMOVAL_TIMEOUT_SECONDS, trialSeed + 2);

                                        try (var repairedOntologyWithWeakening = inconsistent.cloneWithSeparateCache()) {
                                            try {
                                                logMessage("  Repairing with weakening (troquard2018)...");
                                                applyRepairWithTimeout(this::createWeakeningRepair,
                                                        repairedOntologyWithWeakening,
                                                        "weakening", WEAKENING_TIMEOUT_SECONDS, trialSeed + 3);

                                                try (var repairedOntologyWithPowerIndex = inconsistent.cloneWithSeparateCache()) {
                                                    try {
                                                        logMessage("  Repairing with power indexes (troquard2018-power-index)...");
                                                        applyRepairWithTimeout(this::createPowerIndexRepair,
                                                                repairedOntologyWithPowerIndex, "power-index",
                                                                POWER_INDEX_TIMEOUT_SECONDS, trialSeed + 4);

                                                        var subConcepts = collectSubConcepts(repairedOntologyWithRandomRemoval,
                                                                repairedOntologyWithLargestMcsRemoval,
                                                                repairedOntologyWithWeakening,
                                                                repairedOntologyWithPowerIndex);
                                                        var inferredRandomRemoval = inferredAxioms(repairedOntologyWithRandomRemoval,
                                                                subConcepts);
                                                        var inferredLargestMcsRemoval = inferredAxioms(
                                                                repairedOntologyWithLargestMcsRemoval, subConcepts);
                                                        var inferredWeakening = inferredAxioms(repairedOntologyWithWeakening,
                                                                subConcepts);
                                                        var inferredPowerIndex = inferredAxioms(repairedOntologyWithPowerIndex,
                                                                subConcepts);

                                                        var iicPowerVsRandom = Ontology.relativeInformationContent(
                                                                inferredPowerIndex,
                                                                inferredRandomRemoval);
                                                        var iicPowerVsLargestMcs = Ontology.relativeInformationContent(
                                                                inferredPowerIndex,
                                                                inferredLargestMcsRemoval);
                                                        var iicPowerVsWeakening = Ontology.relativeInformationContent(
                                                                inferredPowerIndex,
                                                                inferredWeakening);

                                                        writeCsvLine(csvFile, iicPowerVsRandom, iicPowerVsLargestMcs,
                                                                iicPowerVsWeakening);

                                                        logMessage("  IIC (Power index wrt Random removal): "
                                                                + iicPowerVsRandom);
                                                        logMessage("  IIC (Power index wrt Not-in-largest-MCS removal): "
                                                                + iicPowerVsLargestMcs);
                                                        logMessage("  IIC (Power index wrt Weakening): "
                                                                + iicPowerVsWeakening);
                                                        trialSucceeded = true;

                                                    } catch (TimeoutException e) {
                                                        String msg = e.getMessage();
                                                        logMessage("  " + (msg != null ? msg : "Power index repair failed (no error details)"));
                                                        consecutiveFailures++;
                                                        if (consecutiveFailures >= CONSECUTIVE_FAILURE_CAP) {
                                                            throw new IllegalStateException(
                                                                    "Power index repair failed " + consecutiveFailures
                                                                    + " times in a row. Aborting trials for this ontology.");
                                                        }
                                                    }
                                                }
                                            } catch (TimeoutException e) {
                                                String msg = e.getMessage();
                                                logMessage("  " + (msg != null ? msg : "Weakening repair failed (no error details)"));
                                                consecutiveFailures++;
                                                if (consecutiveFailures >= CONSECUTIVE_FAILURE_CAP) {
                                                    throw new IllegalStateException(
                                                            "Weakening repair failed " + consecutiveFailures
                                                            + " times in a row. Aborting trials for this ontology.");
                                                }
                                            }
                                        }

                                    } catch (TimeoutException e) {
                                        String msg = e.getMessage();
                                        logMessage("  " + (msg != null ? msg : "Not-in-largest-MCS removal repair failed (no error details)"));
                                        consecutiveFailures++;
                                        if (consecutiveFailures >= CONSECUTIVE_FAILURE_CAP) {
                                            throw new IllegalStateException(
                                                    "Not-in-largest-MCS removal repair failed " + consecutiveFailures
                                                    + " times in a row. Aborting trials for this ontology.");
                                        }
                                    }
                                }
                            } catch (TimeoutException e) {
                                String msg = e.getMessage();
                                logMessage("  " + (msg != null ? msg : "Random removal repair failed (no error details)"));
                                consecutiveFailures++;
                                if (consecutiveFailures >= CONSECUTIVE_FAILURE_CAP) {
                                    throw new IllegalStateException(
                                            "Random removal repair failed " + consecutiveFailures
                                            + " times in a row. Aborting trials for this ontology.");
                                }
                            }
                        }

                        if (trialSucceeded) {
                            successfulTrials++;
                            consecutiveFailures = 0;
                        }
                    } catch (IllegalStateException e) {
                        logMessage("ERROR: " + e.getMessage());
                        logMessage("Aborting ontology after successfulTrials=" + successfulTrials
                                + ", attemptedTrials=" + (attemptedTrials + 1) + ".");
                        logMessage("Resume this ontology with start-successful-trials=" + successfulTrials
                                + " and start-attempted-trials=" + (attemptedTrials + 1) + ".");
                        abortedEarly = true;
                        break;
                    } catch (CanceledException e) {
                        Thread.currentThread().interrupt();
                        attemptedTrials++;
                        logMessage("USER INTERRUPT: aborting whole experiment during ontology " + ontologyFileName + ".");
                        logMessage("Aborting ontology after successfulTrials=" + successfulTrials
                                + ", attemptedTrials=" + attemptedTrials + ".");
                        logMessage("Resume this ontology with start-successful-trials=" + successfulTrials
                                + " and start-attempted-trials=" + attemptedTrials + ".");
                        abortedEarly = true;
                        abortedByUser = true;
                        break;
                    }

                    attemptedTrials++;
                }

                if (!abortedEarly) {
                    logMessage("Completed ontology with successfulTrials=" + successfulTrials + ", attemptedTrials="
                            + attemptedTrials + ".");
                    logMessage("Resume point for " + ontologyFileName + ": start-successful-trials="
                            + successfulTrials + ", start-attempted-trials=" + attemptedTrials + ".");
                }

                logMessage("Finished trials for " + ontologyFileName + " (successfulTrials=" + successfulTrials
                        + ", attemptedTrials=" + attemptedTrials + ")");
            }

            var ontologyEndTime = System.nanoTime();
            logMessage("Ontology run time for " + ontologyFileName + ": "
                    + (ontologyEndTime - ontologyStartTime) / 1_000_000 + " ms; total run time: "
                    + (ontologyEndTime - startTime) / 1_000_000 + " ms)");

            if (abortedByUser) {
                break;
            }
        }


        var endTime = System.nanoTime();
        if (abortedByUser) {
            logMessage("Experiment aborted by user. (run time: " + (endTime - startTime) / 1_000_000
                    + " ms; total run time: " + (endTime - startTime) / 1_000_000 + " ms; "
                    + Ontology.reasonerCalls + " reasoner calls)");
        } else {
            logMessage("All trials done. (run time: " + (endTime - startTime) / 1_000_000
                    + " ms; total run time: " + (endTime - startTime) / 1_000_000 + " ms; "
                    + Ontology.reasonerCalls + " reasoner calls)");
        }
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



