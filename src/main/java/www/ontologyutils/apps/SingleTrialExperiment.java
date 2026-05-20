package www.ontologyutils.apps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
 * Run a single trial and write one-row CSV.
 *
 * CLI args:
 *   --ontology <path>  (required)
 *   --seed <long>      (required)
 *   --out <path>       (required)
 *   --removal-timeout-secs <int>
 *   --weakening-timeout-secs <int>
 *   --power-index-timeout-secs <int>
 *   --make-inconsistent-timeout-secs <int>
 */
public class SingleTrialExperiment {
    private OWLReasonerFactory reasonerFactory = new FaCTPlusPlusReasonerFactory();

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

    private void awaitRepairTermination(ExecutorService executor, String repairName) {
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.err.println("  WARNING: Repair thread for " + repairName
                        + " did not terminate promptly after cancellation.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CanceledException();
        }
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
            TimeoutException retryTrigger = new TimeoutException(extractErrorMessage(e));
            retryTrigger.initCause(e);
            throw retryTrigger;
        } finally {
            executor.shutdownNow();
            awaitRepairTermination(executor, repairName);
        }
    }

    private void applyMakeInconsistentWithTimeout(Ontology ontology, long seed, long timeoutSeconds) throws TimeoutException {
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
                future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException(
                        "Make-inconsistent timeout: exceeded " + timeoutSeconds + " seconds");
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

    private void createCsvFileWithHeader(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("iic_power_vs_random,iic_power_vs_not_in_largest_mcs,iic_power_vs_weakening,run_id");
            writer.newLine();
        }
    }

    private void writeCsvLine(Path file, double v1, double v2, double v3, String runId) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(Double.toString(v1));
            writer.write(",");
            writer.write(Double.toString(v2));
            writer.write(",");
            writer.write(Double.toString(v3));
            writer.write(",");
            writer.write(runId != null ? runId : "");
            writer.newLine();
        }
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private void logErr(String msg) {
        System.err.println(msg);
    }

    private static long parseLongArg(Map<String, String> map, String key, long defaultValue) {
        if (map.containsKey(key)) {
            try {
                return Long.parseLong(map.get(key));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric value for " + key + ": " + map.get(key));
            }
        }
        return defaultValue;
    }

    public static void main(String[] args) {
        var app = new SingleTrialExperiment();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length) {
                map.put(a.substring(2), args[++i]);
            }
        }

        String ontologyPath = map.get("ontology");
        String seedStr = map.get("seed");
        String outPath = map.get("out");

        if (ontologyPath == null || seedStr == null || outPath == null) {
            System.err.println("Usage: SingleTrialExperiment --ontology <path> --seed <long> --out <path> [--removal-timeout-secs N] [--weakening-timeout-secs N] [--power-index-timeout-secs N] [--make-inconsistent-timeout-secs N]");
            System.exit(2);
        }

        long seed;
        try {
            seed = Long.parseLong(seedStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid seed: " + seedStr);
            System.exit(2);
            return;
        }

        long removalTimeout = parseLongArg(map, "removal-timeout-secs", 300L);
        long weakeningTimeout = parseLongArg(map, "weakening-timeout-secs", 300L);
        long powerIndexTimeout = parseLongArg(map, "power-index-timeout-secs", 60L);
        long makeInconsistentTimeout = parseLongArg(map, "make-inconsistent-timeout-secs", 300L);

        Path out = Path.of(outPath);
        try {
            Files.createDirectories(out.getParent());
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            System.exit(3);
        }

        app.log("SingleTrialExperiment starting: ontology=" + ontologyPath + ", seed=" + seed + ", out=" + outPath);
        System.out.println("TRIAL_SEED: " + seed);

        try (var ontology = Ontology.loadOntology(ontologyPath, app.reasonerFactory)) {
            app.log("Loaded...");
            app.log("Normalizing ontology with SROIQ normalization...");
            new SroiqNormalization(true, false).apply(ontology);
            app.log("Normalized ontology with SROIQ normalization.");

            // Make inconsistent
            app.log("  Making ontology inconsistent...");
            try {
                app.applyMakeInconsistentWithTimeout(ontology, seed, makeInconsistentTimeout);
            } catch (TimeoutException e) {
                String msg = e.getMessage();
                String m = (msg != null ? msg : "Make-inconsistent failed (no error details)");
                app.logErr("  " + m);
                System.err.println("TRIAL_STATUS: FAIL: " + m);
                System.exit(4);
            } catch (CanceledException e) {
                String m = "Make-inconsistent cancelled by interrupt";
                app.logErr(m);
                System.err.println("TRIAL_STATUS: FAIL: " + m);
                System.exit(5);
            }

            // Run repairs on separate clones
            try (var repairedRandom = ontology.cloneWithSeparateCache()) {
                app.log("  Repairing with removal (random)...");
                app.applyRepairWithTimeout(app::createRandomRemovalRepair, repairedRandom, "removal-random",
                        removalTimeout, seed + 1);

                try (var repairedMcs = ontology.cloneWithSeparateCache()) {
                    app.log("  Repairing with removal (not-in-largest-mcs)...");
                    app.applyRepairWithTimeout(app::createLargestMcsRemovalRepair, repairedMcs,
                            "removal-not-in-largest-mcs", removalTimeout, seed + 2);

                    try (var repairedWeak = ontology.cloneWithSeparateCache()) {
                        app.log("  Repairing with weakening (troquard2018)...");
                        app.applyRepairWithTimeout(app::createWeakeningRepair, repairedWeak, "weakening",
                                weakeningTimeout, seed + 3);

                        try (var repairedPower = ontology.cloneWithSeparateCache()) {
                            app.log("  Repairing with power indexes (troquard2018-power-index)...");
                            app.applyRepairWithTimeout(app::createPowerIndexRepair, repairedPower, "power-index",
                                    powerIndexTimeout, seed + 4);

                            var subConcepts = app.collectSubConcepts(repairedRandom, repairedMcs, repairedWeak, repairedPower);
                            var inferredRandom = app.inferredAxioms(repairedRandom, subConcepts);
                            var inferredMcs = app.inferredAxioms(repairedMcs, subConcepts);
                            var inferredWeak = app.inferredAxioms(repairedWeak, subConcepts);
                            var inferredPower = app.inferredAxioms(repairedPower, subConcepts);

                            var iicPowerVsRandom = Ontology.relativeInformationContent(inferredPower, inferredRandom);
                            var iicPowerVsMcs = Ontology.relativeInformationContent(inferredPower, inferredMcs);
                            var iicPowerVsWeak = Ontology.relativeInformationContent(inferredPower, inferredWeak);

                            // Write CSV header + single line
                                                            if (!Files.exists(out)) {
                                                                app.createCsvFileWithHeader(out);
                                                            }
                                                            String runId = map.get("run-id");
                                                            app.writeCsvLine(out, iicPowerVsRandom, iicPowerVsMcs, iicPowerVsWeak, runId);

                            app.log("  IIC (Power index wrt Random removal): " + iicPowerVsRandom);
                            app.log("  IIC (Power index wrt Not-in-largest-MCS removal): " + iicPowerVsMcs);
                            app.log("  IIC (Power index wrt Weakening): " + iicPowerVsWeak);

                            app.log("SingleTrialExperiment completed successfully.");
                            System.out.println("TRIAL_STATUS: SUCCESS");
                            System.exit(0);

                        } // repairedPower
                    } // repairedWeak
                } // repairedMcs
            } // repairedRandom

        } catch (TimeoutException e) {
            String m = "Timeout while running repairs: " + e.getMessage();
            app.logErr(m);
            System.err.println("TRIAL_STATUS: FAIL: " + m);
            System.exit(6);
        } catch (IOException e) {
            String m = "I/O error: " + e.getMessage();
            app.logErr(m);
            System.err.println("TRIAL_STATUS: FAIL: " + m);
            System.exit(8);
        } catch (Exception e) {
            String m = "Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            app.logErr(m);
            System.err.println("TRIAL_STATUS: FAIL: " + m);
            System.exit(9);
        }
    }
}






