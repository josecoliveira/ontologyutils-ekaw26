package www.ontologyutils.repair.strategy.bad;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.LruCache;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Bad-axiom strategy that returns the axiom with the highest approximate Banzhaf
 * value.
 *
 * <p>
 * All the axioms are players of one shared game: a coalition of them is sampled
 * and every axiom is removed from it in turn, so one coalition scores all of
 * them.
 *
 * <p>
 * The class is self-contained on purpose. The consistency checks it makes, the
 * seeds it derives, the workers it uses and the rule that makes it stop early all
 * belong to this strategy alone, so nothing here is influenced by, or influences,
 * another strategy or another arm of the same experiment.
 */
public class BanzhafApproxBadAxiomSelector implements BadAxiomSelector {
    private static final int CHUNK_SIZE = 50;
    private static final int MAX_SAMPLES = 1000;
    private static final int CACHE_SIZE = 16384;

    private final int chunkSize;
    private final int maxSamples;
    private final long seed;
    /**
     * The coalitions this strategy has already checked, for as long as the strategy
     * lives, that is for the duration of one repair.
     */
    private final Function<Set<OWLAxiom>, Integer> consistency =
            LruCache.wrapFunction(subset -> Utils.isConsistent(subset) ? 0 : 1, CACHE_SIZE);

    /**
     * Seeds the sampling from the random generator of the current trial, so that a
     * repair depends only on the seed the trial was started with.
     */
    public BanzhafApproxBadAxiomSelector() {
        this(CHUNK_SIZE, MAX_SAMPLES, Utils.randomLong());
    }

    public BanzhafApproxBadAxiomSelector(int chunkSize, int maxSamples, long seed) {
        this.chunkSize = chunkSize;
        this.maxSamples = maxSamples;
        this.seed = seed;
    }

    @Override
    public Optional<OWLAxiom> selectBest(Ontology ontology, Predicate<Ontology> isRepaired) {
        Set<OWLAxiom> players = ontology.refutableAxioms().collect(Collectors.toSet());
        if (players.isEmpty()) {
            return Optional.empty();
        }
        System.err.println("[BanzhafApproxBadAxiomSelector] Scoring " + players.size() + " candidate bad axioms");
        Map<OWLAxiom, Double> scores = score(players);
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        var best = BadAxiomSelector.highestScoring(scores);
        System.err.println("[BanzhafApproxBadAxiomSelector] Selected bad axiom: " + Utils.prettyPrintAxiomDL(best));
        return Optional.of(best);
    }

    /**
     * @param players
     *            The axioms that are players of the game.
     * @return The value of every player.
     */
    public Map<OWLAxiom, Double> score(Set<OWLAxiom> players) {
        if (players.isEmpty()) {
            return Map.of();
        }
        var orderedPlayers = sorted(players);
        long baseSeed = seedFor(seed, players, players);
        // The largest values decide which bad axiom is selected.
        return adaptively(players, baseSeed, false,
                (samples, roundSeed) -> sampleInParallel(orderedPlayers, players, samples, roundSeed));
    }

    /**
     * Samples in chunks and stops as soon as the ranking of the largest values
     * stays the same from one round to the next.
     */
    private Map<OWLAxiom, Double> adaptively(Set<OWLAxiom> targets, long baseSeed, boolean ascending,
            Sampler sampler) {
        Map<OWLAxiom, Double> totals = emptyTotals(targets);
        int used = 0;
        List<OWLAxiom> previousRanking = null;
        while (used < maxSamples) {
            int batch = Math.min(chunkSize, maxSamples - used);
            mergeTo(totals, sampler.sample(batch, mixSeed(baseSeed, used)));
            used += batch;

            var averaged = average(totals, used);
            var ranking = decisiveEndOfRanking(averaged, ascending);
            if (ranking.equals(previousRanking)) {
                return averaged;
            }
            previousRanking = ranking;
        }
        return average(totals, used);
    }

    /**
     * @param scores
     *            The current scores.
     * @param ascending
     *            Whether the smallest values are the interesting ones.
     * @return The five axioms at the end of the ranking that decides the
     *         selection, largest first when scoring bad axioms.
     */
    private static List<OWLAxiom> decisiveEndOfRanking(Map<OWLAxiom, Double> scores, boolean ascending) {
        Comparator<Map.Entry<OWLAxiom, Double>> comparator = Comparator
                .<Map.Entry<OWLAxiom, Double>> comparingDouble(Map.Entry::getValue)
                .thenComparing(entry -> Utils.prettyPrintAxiomDL(entry.getKey()));
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return scores.entrySet().stream()
                .sorted(comparator)
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Map<OWLAxiom, Double> sampleInParallel(List<OWLAxiom> orderedPlayers, Set<OWLAxiom> targets, int samples,
            long seed) {
        int workers = Math.min(samplingThreads(), samples);
        int[] shares = shares(samples, workers);
        var tasks = new ArrayList<Callable<Map<OWLAxiom, Double>>>(workers);
        for (int worker = 0; worker < workers; worker++) {
            final int samplesForWorker = shares[worker];
            final int workerIndex = worker;
            tasks.add(() -> {
                var random = new Random(mixSeed(seed, workerIndex));
                Map<OWLAxiom, Double> localTotals = new HashMap<>();
                List<OWLAxiom> orderedTargets = sorted(targets);
                for (int i = 0; i < samplesForWorker; i++) {
                    Set<OWLAxiom> coalition = randomCoalition(orderedPlayers, random);

                    for (OWLAxiom target : orderedTargets) {
                        var coalitionBase = new HashSet<>(coalition);
                        coalitionBase.remove(target);
                        var coalitionWithTarget = new HashSet<>(coalitionBase);
                        coalitionWithTarget.add(target);
                        double marginal = consistencyValue(coalitionWithTarget) - consistencyValue(coalitionBase);
                        localTotals.merge(target, marginal, Double::sum);
                    }
                }
                return localTotals;
            });
        }
        Map<OWLAxiom, Double> merged = new HashMap<>();
        for (var localTotals : runInParallel(tasks)) {
            mergeTo(merged, localTotals);
        }
        return merged;
    }

    /**
     * Runs the sampling tasks on a pool that lives for this call only, so the
     * strategy never leaves threads behind.
     */
    private <T> List<T> runInParallel(List<Callable<T>> tasks) {
        var pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            var thread = new Thread(runnable);
            thread.setName("banzhaf-bad-sampling");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var futures = pool.invokeAll(tasks);
            var results = new ArrayList<T>(futures.size());
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sampling Banzhaf values for bad axioms.", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Failed to sample Banzhaf values for bad axioms.", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private int consistencyValue(Set<OWLAxiom> subset) {
        return consistency.apply(Set.copyOf(subset));
    }

    private static Set<OWLAxiom> randomCoalition(List<OWLAxiom> orderedUniverse, Random random) {
        Set<OWLAxiom> coalition = new HashSet<>();
        for (OWLAxiom candidate : orderedUniverse) {
            if (random.nextBoolean()) {
                coalition.add(candidate);
            }
        }
        return coalition;
    }

    private static int samplingThreads() {
        return Math.max(1, Integer.getInteger("ontologyutils.samplingThreads", 16));
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

    private static long seedFor(long baseSeed, Set<OWLAxiom> universe, Set<OWLAxiom> targets) {
        long seed = baseSeed;
        seed = 31L * seed + canonicalize(universe).hashCode();
        seed = 31L * seed + canonicalize(targets).hashCode();
        return seed;
    }

    private static String canonicalize(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().map(Object::toString).collect(Collectors.joining("|"));
    }

    private static List<OWLAxiom> sorted(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    }

    private static Map<OWLAxiom, Double> emptyTotals(Set<OWLAxiom> targets) {
        Map<OWLAxiom, Double> totals = new HashMap<>();
        for (OWLAxiom target : targets) {
            totals.put(target, 0.0d);
        }
        return totals;
    }

    private static void mergeTo(Map<OWLAxiom, Double> totals, Map<OWLAxiom, Double> partial) {
        for (Map.Entry<OWLAxiom, Double> entry : partial.entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    private static Map<OWLAxiom, Double> average(Map<OWLAxiom, Double> totals, int samples) {
        Map<OWLAxiom, Double> averaged = new HashMap<>();
        for (Map.Entry<OWLAxiom, Double> entry : totals.entrySet()) {
            averaged.put(entry.getKey(), entry.getValue() / (double) samples);
        }
        return averaged;
    }

    @FunctionalInterface
    private interface Sampler {
        Map<OWLAxiom, Double> sample(int samples, long seed);
    }
}
