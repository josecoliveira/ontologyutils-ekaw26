package www.ontologyutils.apps;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import uk.ac.manchester.cs.factplusplus.owlapi.FaCTPlusPlusReasonerFactory;
import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.refinement.AxiomStrengthener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWithPowerIndexes.WeakerAxiomStrategy;
import www.ontologyutils.toolbox.*;

/**
 * Run a single trial and print one JSON object to stdout.
 *
 * CLI args:
 *   --ontology <path>  (required)
 *   --seed <long>      (required)
 *   --run-id <string>  (optional)
 *   --removal-timeout-secs <int>
 *   --weakening-timeout-secs <int>
 *   --power-index-timeout-secs <int>
 *   --make-inconsistent-timeout-secs <int>
 */
public class SingleTrialExperiment {
    private final OWLReasonerFactory reasonerFactory = new FaCTPlusPlusReasonerFactory();

    private static final class TrialResult {
        long seed;
        String runId;
        String trialStatus;
        String errorType;
        String failureStage;
        String errorMessage;
        Double iicPowerVsRandom;
        Double iicPowerVsMcs;
        Double iicPowerVsWeak;
        Long randomRemovalMs;
        Long notInLargestMcsRemovalMs;
        Long weakeningMs;
        Long powerIndexMs;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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
            throw new IllegalStateException(extractErrorMessage(e), e);
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
            throw new IllegalStateException(extractErrorMessage(e), e);
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

    private void log(String msg) {
        System.err.println(msg);
    }

    private void logErr(String msg) {
        System.err.println(msg);
    }

    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        var sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(jsonEscape(key)).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(jsonEscape(value)).append('"');
        }
        if (comma) {
            sb.append(',');
        }
    }

    private static void appendJsonField(StringBuilder sb, String key, Number value, boolean comma) {
        sb.append('"').append(jsonEscape(key)).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else {
            sb.append(value.toString());
        }
        if (comma) {
            sb.append(',');
        }
    }

    private static String trialResultToJson(TrialResult result) {
        var sb = new StringBuilder(512);
        sb.append('{');
        appendJsonField(sb, "seed", result.seed, true);
        appendJsonField(sb, "run_id", result.runId, true);
        appendJsonField(sb, "trial_status", result.trialStatus, true);
        appendJsonField(sb, "error_type", result.errorType, true);
        appendJsonField(sb, "failure_stage", result.failureStage, true);
        appendJsonField(sb, "error_message", result.errorMessage, true);
        sb.append('"').append("iic_values").append('"').append(':').append('{');
        appendJsonField(sb, "power_vs_random", result.iicPowerVsRandom, true);
        appendJsonField(sb, "power_vs_not_in_largest_mcs", result.iicPowerVsMcs, true);
        appendJsonField(sb, "power_vs_weakening", result.iicPowerVsWeak, false);
        sb.append('}').append(',');
        sb.append('"').append("repair_runtimes_ms").append('"').append(':').append('{');
        appendJsonField(sb, "random_removal", result.randomRemovalMs, true);
        appendJsonField(sb, "not_in_largest_mcs_removal", result.notInLargestMcsRemovalMs, true);
        appendJsonField(sb, "weakening", result.weakeningMs, true);
        appendJsonField(sb, "power_index", result.powerIndexMs, false);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private void printTrialResult(TrialResult result) {
        System.out.println(trialResultToJson(result));
    }

    private void runWithTiming(ThrowingRunnable action, LongConsumer sink) throws Exception {
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            sink.accept(nanosToMillis(System.nanoTime() - start));
        }
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
        String runId = map.getOrDefault("run-id", "");

        if (ontologyPath == null || seedStr == null) {
            System.err.println("Usage: SingleTrialExperiment --ontology <path> --seed <long> [--run-id <string>] [--removal-timeout-secs N] [--weakening-timeout-secs N] [--power-index-timeout-secs N] [--make-inconsistent-timeout-secs N]");
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

        app.log("SingleTrialExperiment starting: ontology=" + ontologyPath + ", seed=" + seed + ", run_id=" + runId);

        TrialResult result = new TrialResult();
        result.seed = seed;
        result.runId = runId;

        String failureStage = null;
        try (var ontology = Ontology.loadOntology(ontologyPath, app.reasonerFactory)) {
            app.log("Loaded ontology.");
            app.log("Normalizing ontology with SROIQ normalization...");
            new SroiqNormalization(true, false).apply(ontology);
            app.log("Normalized ontology with SROIQ normalization.");

            failureStage = "make_inconsistent";
            app.log("Making ontology inconsistent...");
            app.applyMakeInconsistentWithTimeout(ontology, seed, makeInconsistentTimeout);

            try (var repairedRandom = ontology.cloneWithSeparateCache()) {
                failureStage = "random_removal";
                app.log("Repairing with removal (random)...");
                app.runWithTiming(() -> app.applyRepairWithTimeout(app::createRandomRemovalRepair, repairedRandom,
                        "removal-random", removalTimeout, seed + 1), duration -> result.randomRemovalMs = duration);

                try (var repairedMcs = ontology.cloneWithSeparateCache()) {
                    failureStage = "not_in_largest_mcs_removal";
                    app.log("Repairing with removal (not-in-largest-mcs)...");
                    app.runWithTiming(() -> app.applyRepairWithTimeout(app::createLargestMcsRemovalRepair, repairedMcs,
                            "removal-not-in-largest-mcs", removalTimeout, seed + 2),
                            duration -> result.notInLargestMcsRemovalMs = duration);

                    try (var repairedWeak = ontology.cloneWithSeparateCache()) {
                        failureStage = "weakening";
                        app.log("Repairing with weakening...");
                        app.runWithTiming(() -> app.applyRepairWithTimeout(app::createWeakeningRepair, repairedWeak,
                                "weakening", weakeningTimeout, seed + 3),
                                duration -> result.weakeningMs = duration);

                        try (var repairedPower = ontology.cloneWithSeparateCache()) {
                            failureStage = "power_index";
                            app.log("Repairing with power index...");
                            app.runWithTiming(() -> app.applyRepairWithTimeout(app::createPowerIndexRepair, repairedPower,
                                    "power-index", powerIndexTimeout, seed + 4),
                                    duration -> result.powerIndexMs = duration);

                            var subConcepts = app.collectSubConcepts(repairedRandom, repairedMcs, repairedWeak, repairedPower);
                            var inferredRandom = app.inferredAxioms(repairedRandom, subConcepts);
                            var inferredMcs = app.inferredAxioms(repairedMcs, subConcepts);
                            var inferredWeak = app.inferredAxioms(repairedWeak, subConcepts);
                            var inferredPower = app.inferredAxioms(repairedPower, subConcepts);

                            result.iicPowerVsRandom = Ontology.relativeInformationContent(inferredPower, inferredRandom);
                            result.iicPowerVsMcs = Ontology.relativeInformationContent(inferredPower, inferredMcs);
                            result.iicPowerVsWeak = Ontology.relativeInformationContent(inferredPower, inferredWeak);
                            result.trialStatus = "success";
                            result.errorType = null;
                            result.failureStage = null;
                            result.errorMessage = null;

                            app.log("IIC (Power index wrt Random removal): " + result.iicPowerVsRandom);
                            app.log("IIC (Power index wrt Not-in-largest-MCS removal): " + result.iicPowerVsMcs);
                            app.log("IIC (Power index wrt Weakening): " + result.iicPowerVsWeak);
                            app.log("SingleTrialExperiment completed successfully.");
                            app.printTrialResult(result);
                            return;
                        }
                    }
                }
            }
        } catch (TimeoutException e) {
            result.trialStatus = "time_limit_exceeded";
            result.errorType = "timeout";
            result.failureStage = failureStage;
            result.errorMessage = e.getMessage();
            app.logErr("Timeout: " + (e.getMessage() != null ? e.getMessage() : "no message"));
        } catch (OutOfMemoryError e) {
            result.trialStatus = "memory_limit_exceeded";
            result.errorType = "out_of_memory";
            result.failureStage = failureStage;
            result.errorMessage = e.getMessage();
            app.logErr("OutOfMemoryError: " + (e.getMessage() != null ? e.getMessage() : "no message"));
        } catch (Exception e) {
            result.trialStatus = "memory_limit_exceeded";
            result.errorType = "failure";
            result.failureStage = failureStage;
            result.errorMessage = app.extractErrorMessage(e);
            app.logErr("Failure: " + result.errorMessage);
        }

        app.printTrialResult(result);
        System.exit(0);
    }
}






