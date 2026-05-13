package www.ontologyutils.repair.powerindex;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Utils;

/**
 * Computes an approximate Shapley inconsistency value for an axiom using Monte
 * Carlo sampling. This trades precision for speed by sampling permutations
 * instead of enumerating all subsets.
 */
public class ShapleyInconsistencyValueApproximate implements PowerIndex {
    private static final int DEFAULT_APPROXIMATION_SAMPLES = 735;
    private static final long DEFAULT_APPROXIMATION_SEED = 13L;
    private static final int PARALLEL_SAMPLING_THREADS = 6;
    private static final ExecutorService SAMPLING_EXECUTOR = Executors.newFixedThreadPool(PARALLEL_SAMPLING_THREADS,
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("shapley-approx-worker-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });

    private final ConcurrentMap<Set<OWLAxiom>, Integer> drasticCache;
    private final int approximationSamples;
    private final long approximationSeed;

    /**
     * Creates a new approximate Shapley inconsistency value computer with default
     * parameters (4096 samples, seed 13).
     */
    public ShapleyInconsistencyValueApproximate() {
        this(DEFAULT_APPROXIMATION_SAMPLES, DEFAULT_APPROXIMATION_SEED);
    }

    /**
     * Creates a new approximate Shapley inconsistency value computer with custom
     * sampling parameters.
     *
     * @param approximationSamples
     *            The number of permutations to sample (must be positive).
     * @param approximationSeed
     *            The base seed for deterministic sampling.
     */
    public ShapleyInconsistencyValueApproximate(int approximationSamples, long approximationSeed) {
        if (approximationSamples <= 0) {
            throw new IllegalArgumentException("Approximation samples must be positive: " + approximationSamples);
        }
        this.drasticCache = new ConcurrentHashMap<>();
        this.approximationSamples = approximationSamples;
        this.approximationSeed = approximationSeed;
    }

    @Override
    public double computeScore(Set<OWLAxiom> axioms, OWLAxiom targetAxiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(targetAxiom);
        return approximateShapleyInconsistencyValue(universe, targetAxiom);
    }

    /**
     * Batch scoring for multiple targets. This shares permutations across all
     * targets while preserving the semantics that each target is evaluated in
     * the context of {@code axioms} (i.e. scoring each target as if it were
     * added alone to the base set).
     */
    public Map<OWLAxiom, Double> computeScores(Set<OWLAxiom> axioms, Set<OWLAxiom> targets) {
        if (targets == null || targets.isEmpty()) {
            return Map.of();
        }

        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.addAll(targets);

        // deterministic ordering
        List<OWLAxiom> orderedUniverse = universe.stream().sorted().collect(Collectors.toCollection(ArrayList::new));

        Map<OWLAxiom, Double> totals = new HashMap<>();
        for (OWLAxiom t : targets) {
            totals.put(t, 0.0d);
        }

        long baseSeed = seedFor(universe, targets);
        mergeTotals(totals, sampleTargetsInParallel(orderedUniverse, targets, approximationSamples, baseSeed));

        // average
        Map<OWLAxiom, Double> averaged = new HashMap<>();
        for (Map.Entry<OWLAxiom, Double> e : totals.entrySet()) {
            averaged.put(e.getKey(), e.getValue() / approximationSamples);
        }
        return averaged;
    }

    /**
     * Adaptive scoring that samples in chunks and stops when the ranking of
     * targets stabilizes (or when maxSamples is reached).
     * This returns the latest averaged scores for all targets.
     */
    public Map<OWLAxiom, Double> computeScoresAdaptive(Set<OWLAxiom> axioms, Set<OWLAxiom> targets,
            int chunkSize, int maxSamples) {
        return computeScoresAdaptive(axioms, targets, chunkSize, maxSamples, true);
    }

    /**
     * Adaptive scoring with explicit ranking direction.
     * @param ascending
     *            true for smaller scores being better (weakenings), false for
     *            larger scores being better (bad axioms).
     */
    public Map<OWLAxiom, Double> computeScoresAdaptive(Set<OWLAxiom> axioms, Set<OWLAxiom> targets,
            int chunkSize, int maxSamples, boolean ascending) {
        if (targets == null || targets.isEmpty()) {
            return Map.of();
        }
        if (chunkSize <= 0 || maxSamples <= 0) {
            throw new IllegalArgumentException("chunkSize and maxSamples must be positive");
        }

        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.addAll(targets);
        List<OWLAxiom> orderedUniverse = universe.stream().sorted().collect(Collectors.toCollection(ArrayList::new));

        Map<OWLAxiom, Double> totals = new HashMap<>();
        for (OWLAxiom t : targets) {
            totals.put(t, 0.0d);
        }

        long baseSeed = seedFor(universe, targets);
        int used = 0;
        List<OWLAxiom> previousRanking = null;

        while (used < maxSamples) {
            int batch = Math.min(chunkSize, maxSamples - used);
            mergeTotals(totals, sampleTargetsInParallel(orderedUniverse, targets, batch, mixSeed(baseSeed, used)));
            used += batch;

            // compute averaged scores so far
            Map<OWLAxiom, Double> averaged = new HashMap<>();
            for (Map.Entry<OWLAxiom, Double> e : totals.entrySet()) {
                averaged.put(e.getKey(), e.getValue() / (double) used);
            }

            // compute ranking using the requested direction.
            Comparator<Map.Entry<OWLAxiom, Double>> comparator = Comparator
                    .<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                    .thenComparing(e -> Utils.prettyPrintAxiomDL(e.getKey()));
            if (!ascending) {
                comparator = comparator.reversed();
            }
            List<OWLAxiom> ranking = averaged.entrySet().stream()
                    .sorted(comparator)
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (ranking.equals(previousRanking)) {
                return averaged;
            }
            previousRanking = ranking;
        }

        // final averaged
        Map<OWLAxiom, Double> finalAveraged = new HashMap<>();
        for (Map.Entry<OWLAxiom, Double> e : totals.entrySet()) {
            finalAveraged.put(e.getKey(), e.getValue() / (double) used);
        }
        return finalAveraged;
    }

    private double approximateShapleyInconsistencyValue(Set<OWLAxiom> universe, OWLAxiom axiom) {
        // Sort once so seeded sampling is deterministic across JVM runs.
        List<OWLAxiom> orderedUniverse = universe.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
        double totalMarginal = sampleSingleTargetInParallel(orderedUniverse, axiom, approximationSamples,
                seedFor(universe, axiom));
        return totalMarginal / approximationSamples;
    }

    private long seedFor(Set<OWLAxiom> universe, OWLAxiom axiom) {
        long seed = approximationSeed;
        seed = 31L * seed + canonicalize(universe).hashCode();
        seed = 31L * seed + axiom.hashCode();
        return seed;
    }

    private long seedFor(Set<OWLAxiom> universe, Set<OWLAxiom> targets) {
        long seed = approximationSeed;
        seed = 31L * seed + canonicalize(universe).hashCode();
        seed = 31L * seed + canonicalize(targets).hashCode();
        return seed;
    }

    private static String canonicalize(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().map(Object::toString).collect(Collectors.joining("|"));
    }

    private int drasticInconsistencyValue(Set<OWLAxiom> subset) {
        Set<OWLAxiom> key = Set.copyOf(subset);
        return drasticCache.computeIfAbsent(key, k -> Utils.isConsistent(k) ? 0 : 1);
    }

    private Map<OWLAxiom, Double> sampleTargetsInParallel(List<OWLAxiom> orderedUniverse, Set<OWLAxiom> targets,
            int samples, long seed) {
        var tasks = new ArrayList<Callable<Map<OWLAxiom, Double>>>();
        int workers = Math.min(PARALLEL_SAMPLING_THREADS, samples);
        int[] shares = shares(samples, workers);
        for (int worker = 0; worker < workers; worker++) {
            final int samplesForWorker = shares[worker];
            final int workerIndex = worker;
            tasks.add(() -> {
                List<OWLAxiom> permutation = new ArrayList<>(orderedUniverse);
                Random random = new Random(mixSeed(seed, workerIndex));
                Map<OWLAxiom, Double> localTotals = new HashMap<>();
                for (int i = 0; i < samplesForWorker; i++) {
                    Collections.shuffle(permutation, random);

                    Set<OWLAxiom> prefixBase = new HashSet<>();
                    for (OWLAxiom current : permutation) {
                        if (targets.contains(current)) {
                            Set<OWLAxiom> prefixWith = new HashSet<>(prefixBase);
                            prefixWith.add(current);
                            double marginal = drasticInconsistencyValue(prefixWith) - drasticInconsistencyValue(prefixBase);
                            localTotals.merge(current, marginal, Double::sum);
                        } else {
                            prefixBase.add(current);
                        }
                    }
                }
                return localTotals;
            });
        }
        return mergeWorkerMaps(invokeAll(tasks));
    }

    private double sampleSingleTargetInParallel(List<OWLAxiom> orderedUniverse, OWLAxiom axiom, int samples, long seed) {
        var tasks = new ArrayList<Callable<Double>>();
        int workers = Math.min(PARALLEL_SAMPLING_THREADS, samples);
        int[] shares = shares(samples, workers);
        for (int worker = 0; worker < workers; worker++) {
            final int samplesForWorker = shares[worker];
            final int workerIndex = worker;
            tasks.add(() -> {
                List<OWLAxiom> permutation = new ArrayList<>(orderedUniverse);
                Random random = new Random(mixSeed(seed, workerIndex));
                double localTotal = 0.0d;
                for (int i = 0; i < samplesForWorker; i++) {
                    Collections.shuffle(permutation, random);

                    Set<OWLAxiom> prefix = new HashSet<>();
                    for (OWLAxiom current : permutation) {
                        if (current.equals(axiom)) {
                            Set<OWLAxiom> prefixWithAxiom = new HashSet<>(prefix);
                            prefixWithAxiom.add(axiom);
                            localTotal += drasticInconsistencyValue(prefixWithAxiom) - drasticInconsistencyValue(prefix);
                            break;
                        }
                        prefix.add(current);
                    }
                }
                return localTotal;
            });
        }
        return invokeAll(tasks).stream().mapToDouble(Double::doubleValue).sum();
    }

    private <T> List<T> invokeAll(List<Callable<T>> tasks) {
        try {
            List<Future<T>> futures = SAMPLING_EXECUTOR.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sampling approximate Shapley values.", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Failed to sample approximate Shapley values.", ex.getCause());
        }
    }

    private static Map<OWLAxiom, Double> mergeWorkerMaps(List<Map<OWLAxiom, Double>> workerTotals) {
        Map<OWLAxiom, Double> merged = new HashMap<>();
        for (Map<OWLAxiom, Double> localTotals : workerTotals) {
            mergeTotals(merged, localTotals);
        }
        return merged;
    }

    private static void mergeTotals(Map<OWLAxiom, Double> totals, Map<OWLAxiom, Double> partial) {
        for (Map.Entry<OWLAxiom, Double> entry : partial.entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    private static int[] shares(int samples, int workers) {
        int[] result = new int[workers];
        int base = samples / workers;
        int remainder = samples % workers;
        for (int worker = 0; worker < workers; worker++) {
            result[worker] = base + (worker < remainder ? 1 : 0);
        }
        return result;
    }

    private static long mixSeed(long seed, int salt) {
        long mixed = seed ^ (0x9E3779B97F4A7C15L * (salt + 1L));
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);
        return mixed;
    }
}

