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
import www.ontologyutils.repair.OntologyRepairBuilder;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.powerindex.PowerIndexRegistry;
import www.ontologyutils.repair.powerindex.PowerIndexType;
import www.ontologyutils.repair.strategy.bad.BadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.DefaultBadAxiomSelector;
import www.ontologyutils.repair.strategy.bad.PowerIndexBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.PowerIndexWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.RandomWeakerAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.WeakerAxiomSelector;
import www.ontologyutils.toolbox.*;

/**
 * Run a single trial and print one JSON object to stdout.
 *
 * CLI args:
 *   --ontology <path>  (required)
 *   --seed <long>      (required)
 *   --run-id <string>  (optional)
 *   --verbose          (optional) enable infoMessage logging from repairs
 *   --removal-timeout-secs <int>
 *   --weakening-timeout-secs <int>
 *   --power-index-timeout-secs <int>
 *   --make-inconsistent-timeout-secs <int>
 */
public class SingleTrialExperiment {
    private final OWLReasonerFactory reasonerFactory = new FaCTPlusPlusReasonerFactory();
    private boolean verbose = false;

    private static final List<String> A_REPAIRS = List.of("A1", "A2", "A3");

    private enum BadSelectorKind {
        IN_SOME_MUS,
        SHAPLEY,
        BANZHAF
    }

    private enum WeakerSelectorKind {
        RANDOM,
        SHAPLEY,
        BANZHAF
    }

    private static final class BRepairSpec {
        final String id;
        final BadSelectorKind badSelector;
        final WeakerSelectorKind weakerSelector;

        BRepairSpec(String id, BadSelectorKind badSelector, WeakerSelectorKind weakerSelector) {
            this.id = id;
            this.badSelector = badSelector;
            this.weakerSelector = weakerSelector;
        }
    }

    private static final List<BRepairSpec> B_REPAIR_SPECS = List.of(
            new BRepairSpec("B1", BadSelectorKind.IN_SOME_MUS, WeakerSelectorKind.RANDOM),
            new BRepairSpec("B2", BadSelectorKind.IN_SOME_MUS, WeakerSelectorKind.SHAPLEY),
            new BRepairSpec("B3", BadSelectorKind.IN_SOME_MUS, WeakerSelectorKind.BANZHAF),
            new BRepairSpec("B4", BadSelectorKind.SHAPLEY, WeakerSelectorKind.RANDOM),
            new BRepairSpec("B5", BadSelectorKind.SHAPLEY, WeakerSelectorKind.SHAPLEY),
            new BRepairSpec("B6", BadSelectorKind.SHAPLEY, WeakerSelectorKind.BANZHAF),
            new BRepairSpec("B7", BadSelectorKind.BANZHAF, WeakerSelectorKind.RANDOM),
            new BRepairSpec("B8", BadSelectorKind.BANZHAF, WeakerSelectorKind.SHAPLEY),
            new BRepairSpec("B9", BadSelectorKind.BANZHAF, WeakerSelectorKind.BANZHAF));

    private static final class RepairPlan {
        final String id;
        final String label;
        final Supplier<? extends OntologyRepair> supplier;
        final long timeoutSeconds;

        RepairPlan(String id, String label, Supplier<? extends OntologyRepair> supplier, long timeoutSeconds) {
            this.id = id;
            this.label = label;
            this.supplier = supplier;
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    private static final class TrialResult {
        long seed;
        String runId;
        String trialStatus;
        String errorType;
        String failureStage;
        String errorMessage;
        Map<String, Double> iicValues = new LinkedHashMap<>();
        Map<String, Long> repairRuntimesMs = new LinkedHashMap<>();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private OntologyRepair createWeakeningRepair() {
        return OntologyRepairBuilder.forConsistency()
                .withRefStrategy(RefOntologyStrategy.ONE_MCS)
                .withBadStrategy(OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS)
                .withWeakeningFlags(AxiomStrengthener.FLAG_SROIQ_STRICT | AxiomStrengthener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomStrengthener.FLAG_RIA_ONLY_SIMPLE | AxiomStrengthener.FLAG_ALC_STRICT
                        | AxiomStrengthener.FLAG_NO_ROLE_REFINEMENT | AxiomStrengthener.FLAG_OWL2_SET_OPERANDS)
                .withEnhanceRef(false)
                .build();
    }

    private OntologyRepairRemoval createRandomRemovalRepair() {
        return new OntologyRepairRemoval(Ontology::isConsistent, OntologyRepairRemoval.BadAxiomStrategy.RANDOM);
    }

    private OntologyRepairRemoval createLargestMcsRemovalRepair() {
        return new OntologyRepairRemoval(Ontology::isConsistent,
                OntologyRepairRemoval.BadAxiomStrategy.NOT_IN_LARGEST_MCS);
    }

    private OntologyRepair createPowerIndexRepair() {
        return OntologyRepairBuilder.forConsistency()
                .withRefStrategy(RefOntologyStrategy.ONE_MCS)
                .withPowerIndex(PowerIndexType.SHAPLEY_APPROXIMATE)
                .withWeakeningFlags(AxiomStrengthener.FLAG_SROIQ_STRICT | AxiomStrengthener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomStrengthener.FLAG_RIA_ONLY_SIMPLE | AxiomStrengthener.FLAG_ALC_STRICT
                        | AxiomStrengthener.FLAG_NO_ROLE_REFINEMENT | AxiomStrengthener.FLAG_OWL2_SET_OPERANDS)
                .withEnhanceRef(false)
                .build();
    }

    private OntologyRepair createBRepair(BadSelectorKind badSelectorKind, WeakerSelectorKind weakerSelectorKind) {
        BadAxiomSelector badSelector = switch (badSelectorKind) {
            case IN_SOME_MUS -> new DefaultBadAxiomSelector(OntologyRepairRemoval.BadAxiomStrategy.IN_SOME_MUS);
            case SHAPLEY -> new PowerIndexBadAxiomSelector(PowerIndexRegistry.create(PowerIndexType.SHAPLEY_APPROXIMATE));
            case BANZHAF -> new PowerIndexBadAxiomSelector(PowerIndexRegistry.create(PowerIndexType.BANZHAF_APPROXIMATE));
        };

        WeakerAxiomSelector weakerSelector = switch (weakerSelectorKind) {
            case RANDOM -> new RandomWeakerAxiomSelector();
            case SHAPLEY -> new PowerIndexWeakerAxiomSelector(PowerIndexRegistry.create(PowerIndexType.SHAPLEY_APPROXIMATE));
            case BANZHAF -> new PowerIndexWeakerAxiomSelector(PowerIndexRegistry.create(PowerIndexType.BANZHAF_APPROXIMATE));
        };

        return OntologyRepairBuilder.forConsistency()
                .withRefStrategy(RefOntologyStrategy.ONE_MCS)
                .withBadSelector(badSelector)
                .withWeakerSelector(weakerSelector)
                .withWeakeningFlags(AxiomStrengthener.FLAG_SROIQ_STRICT | AxiomStrengthener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomStrengthener.FLAG_RIA_ONLY_SIMPLE | AxiomStrengthener.FLAG_ALC_STRICT
                        | AxiomStrengthener.FLAG_NO_ROLE_REFINEMENT | AxiomStrengthener.FLAG_OWL2_SET_OPERANDS)
                .withEnhanceRef(false)
                .build();
    }

    private List<RepairPlan> buildRepairPlans(long removalTimeout, long weakeningTimeout, long powerIndexTimeout) {
        var plans = new ArrayList<RepairPlan>();
        plans.add(new RepairPlan("A1", "random-removal", this::createRandomRemovalRepair, removalTimeout));
        plans.add(new RepairPlan("A2", "not-in-largest-mcs-removal", this::createLargestMcsRemovalRepair, removalTimeout));
        plans.add(new RepairPlan("A3", "default-weakening", this::createWeakeningRepair, weakeningTimeout));
        for (var bSpec : B_REPAIR_SPECS) {
            plans.add(new RepairPlan(
                    bSpec.id,
                    "bad-" + bSpec.badSelector.name().toLowerCase(Locale.ROOT) + "-weaker-"
                            + bSpec.weakerSelector.name().toLowerCase(Locale.ROOT),
                    () -> createBRepair(bSpec.badSelector, bSpec.weakerSelector),
                    powerIndexTimeout));
        }
        return plans;
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
                var repair = repairSupplier.get();
                if (verbose) {
                    repair.setInfoCallback(this::logMessage);
                }
                repair.apply(ontology);
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

    private void logMessage(String msg) {
        System.err.println("[" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "] " + msg);
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

    private static void appendJsonNumberMap(StringBuilder sb, String key, Map<String, ? extends Number> values, boolean comma) {
        sb.append('"').append(jsonEscape(key)).append('"').append(':').append('{');
        boolean first = true;
        for (var entry : values.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            appendJsonField(sb, entry.getKey(), entry.getValue(), false);
            first = false;
        }
        sb.append('}');
        if (comma) {
            sb.append(',');
        }
    }

    private static String trialResultToJson(TrialResult result) {
        var sb = new StringBuilder(2048);
        sb.append('{');
        appendJsonField(sb, "seed", result.seed, true);
        appendJsonField(sb, "run_id", result.runId, true);
        appendJsonField(sb, "trial_status", result.trialStatus, true);
        appendJsonField(sb, "error_type", result.errorType, true);
        appendJsonField(sb, "failure_stage", result.failureStage, true);
        appendJsonField(sb, "error_message", result.errorMessage, true);
        appendJsonNumberMap(sb, "iic_values", result.iicValues, true);
        appendJsonNumberMap(sb, "repair_runtimes_ms", result.repairRuntimesMs, false);
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
            if (a.equals("--verbose")) {
                app.verbose = true;
            } else if (a.startsWith("--") && i + 1 < args.length) {
                map.put(a.substring(2), args[++i]);
            }
        }

        String ontologyPath = map.get("ontology");
        String seedStr = map.get("seed");
        String runId = map.getOrDefault("run-id", "");

        if (ontologyPath == null || seedStr == null) {
            System.err.println("Usage: SingleTrialExperiment --ontology <path> --seed <long> [--run-id <string>] [--verbose] [--removal-timeout-secs N] [--weakening-timeout-secs N] [--power-index-timeout-secs N] [--make-inconsistent-timeout-secs N]");
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
        long powerIndexTimeout = parseLongArg(map, "power-index-timeout-secs", 300L);
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

            if (ontology.isConsistent()) {
                failureStage = "make_inconsistent";
                app.log("Making ontology inconsistent...");
                app.applyMakeInconsistentWithTimeout(ontology, seed, makeInconsistentTimeout);
            } else {
                app.log("Ontology is already inconsistent, skipping make-inconsistent step.");
            }

            var repairPlans = app.buildRepairPlans(removalTimeout, weakeningTimeout, powerIndexTimeout);
            var repairedOntologies = new LinkedHashMap<String, Ontology>();
            long repairSeed = seed + 1;

            try {
                for (var plan : repairPlans) {
                    failureStage = plan.id;
                    var repaired = ontology.cloneWithSeparateCache();
                    repairedOntologies.put(plan.id, repaired);
                    final long seedForRepair = repairSeed++;
                    app.log("Repairing with " + plan.id + " (" + plan.label + ")...");
                    app.runWithTiming(
                            () -> app.applyRepairWithTimeout(plan.supplier, repaired, plan.id, plan.timeoutSeconds, seedForRepair),
                            duration -> result.repairRuntimesMs.put(plan.id, duration));
                }

                var subConcepts = app.collectSubConcepts(repairedOntologies.values().toArray(new Ontology[0]));
                var inferredByRepair = new LinkedHashMap<String, Set<OWLAxiom>>();
                for (var entry : repairedOntologies.entrySet()) {
                    inferredByRepair.put(entry.getKey(), app.inferredAxioms(entry.getValue(), subConcepts));
                }

                for (var bSpec : B_REPAIR_SPECS) {
                    for (var aRepair : A_REPAIRS) {
                        var iicKey = bSpec.id + "_vs_" + aRepair;
                        var iicValue = Ontology.relativeInformationContent(
                                inferredByRepair.get(bSpec.id),
                                inferredByRepair.get(aRepair));
                        result.iicValues.put(iicKey, iicValue);
                        app.log("IIC (" + bSpec.id + " wrt " + aRepair + "): " + iicValue);
                    }
                }

                result.trialStatus = "success";
                result.errorType = null;
                result.failureStage = null;
                result.errorMessage = null;

                app.log("SingleTrialExperiment completed successfully.");
                app.printTrialResult(result);
                return;
            } finally {
                for (var repairedOntology : repairedOntologies.values()) {
                    try {
                        repairedOntology.close();
                    } catch (Exception e) {
                        app.logErr("Failed to close repaired ontology: " + e.getMessage());
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
