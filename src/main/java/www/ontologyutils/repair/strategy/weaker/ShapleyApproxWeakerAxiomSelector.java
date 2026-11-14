package www.ontologyutils.repair.strategy.weaker;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.LruCache;
import www.ontologyutils.toolbox.Utils;

/**
 * Weakening strategy that returns the candidate with the lowest approximate
 * Shapley value.
 *
 * <p>
 * Every candidate is scored in the game that its own replacement would produce: a
 * permutation of the context is sampled and the candidate is inserted at a
 * uniformly drawn cut, which is the same as sampling a permutation of the context
 * plus that candidate. The other candidates are not players of that game, so a
 * candidate cannot influence the score of its siblings.
 *
 * <p>
 * The class is self-contained on purpose. The consistency checks it makes, the
 * seeds it derives, the workers it uses and the rule that makes it stop early all
 * belong to this strategy alone, so nothing here is influenced by, or influences,
 * the bad-axiom strategies or another arm of the same experiment.
 */
public class ShapleyApproxWeakerAxiomSelector implements WeakerAxiomSelector {
    /**
     * How many permutations are drawn in one round of sampling.
     */
    private static final int CHUNK_SIZE = 50;
    /**
     * The largest number of permutations drawn for one selection.
     */
    private static final int MAX_SAMPLES = 1000;
    /**
     * How many coalitions the consistency cache keeps.
     */
    private static final int CACHE_SIZE = 16384;

    /**
     * The configured number of permutations per round.
     */
    private final int chunkSize;
    /**
     * The configured cap on the number of permutations.
     */
    private final int maxSamples;
    /**
     * The seed this instance samples with.
     */
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
    public ShapleyApproxWeakerAxiomSelector() {
        this(CHUNK_SIZE, MAX_SAMPLES, Utils.randomLong());
    }

    /**
     * Uses the given budget and seed, which lets a test draw the same permutations
     * twice.
     *
     * @param chunkSize
     *            The number of permutations per round.
     * @param maxSamples
     *            The largest number of permutations to draw.
     * @param seed
     *            The seed to sample with.
     */
    public ShapleyApproxWeakerAxiomSelector(int chunkSize, int maxSamples, long seed) {
        this.chunkSize = chunkSize;
        this.maxSamples = maxSamples;
        this.seed = seed;
    }

    /**
     * Scores every weakening of the axiom in the game that its own replacement would
     * produce and returns the one with the lowest value.
     *
     * <p>
     * A failure while weakening is reported and treated as if the axiom had no
     * weakening at all, so the repair loop can move on.
     *
     * @param badAxiom
     *            The axiom that is going to be replaced.
     * @param weakener
     *            The weakener that produces the candidates.
     * @param context
     *            The current refutable axioms, including the bad axiom.
     * @return The weakening that contributes least to the inconsistency, or empty if
     *         weakening fails or the axiom has no strict weakening.
     */
    @Override
    public Optional<OWLAxiom> selectBest(OWLAxiom badAxiom, AxiomWeakener weakener, Set<OWLAxiom> context) {
        try {
            var weakenings = WeakerAxiomSelector.weakeningsOf(badAxiom, weakener);
            if (weakenings.isEmpty()) {
                return Optional.empty();
            }
            System.err.println("[ShapleyApproxWeakerAxiomSelector] Scoring " + weakenings.size()
                    + " candidate weakenings for " + Utils.prettyPrintAxiomDL(badAxiom));
            Map<OWLAxiom, Double> scores = score(WeakerAxiomSelector.scoringContext(context, badAxiom), weakenings);
            var best = WeakerAxiomSelector.lowestScoring(scores);
            System.err.println("[ShapleyApproxWeakerAxiomSelector] Selected weaker axiom: " + Utils.prettyPrintAxiomDL(best));
            return Optional.of(best);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Estimates the value of every candidate by sampling permutations of the
     * context, inserting the candidate at a uniformly drawn cut.
     *
     * @param context
     *            The game that every replacement would produce, without the
     *            candidates.
     * @param candidates
     *            The weakenings to score, each in its own game.
     * @return The value of each candidate in its own game.
     */
    public Map<OWLAxiom, Double> score(Set<OWLAxiom> context, Set<OWLAxiom> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        var universe = union(context, candidates);
        var orderedContext = sorted(context);
        long baseSeed = seedFor(seed, universe, candidates);
        // The smallest values decide which weakening is selected.
        return adaptively(candidates, baseSeed, true,
                (samples, roundSeed) -> sampleInParallel(orderedContext, candidates, samples, roundSeed));
    }

    /**
     * Samples in chunks and stops as soon as the ranking of the smallest values
     * stays the same from one round to the next.
     *
     * @param targets
     *            The candidates whose values are accumulated.
     * @param baseSeed
     *            The seed the rounds are derived from.
     * @param ascending
     *            Whether the smallest values are the interesting ones.
     * @param sampler
     *            Draws the given number of permutations for one round.
     * @return The mean marginal contribution of every target over all the samples
     *         drawn.
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
     * Collects the candidates at the end of the ranking that decides the selection,
     * which is the part of the ranking that has to be stable before sampling stops.
     *
     * @param scores
     *            The current scores.
     * @param ascending
     *            Whether the smallest values are the interesting ones.
     * @return The five axioms at the end of the ranking that decides the
     *         selection, smallest first when scoring weakenings.
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

    /**
     * Draws the given number of permutations of the context, spread over the
     * sampling workers, and accumulates the marginal contribution of every candidate
     * at the cut it is inserted at in each of them.
     *
     * <p>
     * Inserting the candidate at a uniformly drawn cut into a permutation of the
     * context is the same as drawing a permutation of the context plus that
     * candidate, so the mean marginal is an unbiased estimate of its value. The
     * candidates share the permutation of a sample but not the cut, so their
     * estimates are correlated but never biased by their siblings. A coalition
     * that is already inconsistent cannot be swung by the candidate, so those
     * samples are skipped instead of being looked up again.
     *
     * @param orderedContext
     *            The context in a deterministic order.
     * @param candidates
     *            The weakenings whose marginals are accumulated.
     * @param samples
     *            The number of permutations to draw.
     * @param seed
     *            The seed of this round.
     * @return The sum of the marginal contributions of every candidate, to be
     *         divided by the number of samples by the caller.
     */
    private Map<OWLAxiom, Double> sampleInParallel(List<OWLAxiom> orderedContext, Set<OWLAxiom> candidates,
            int samples, long seed) {
        List<OWLAxiom> orderedCandidates = sorted(candidates);
        int workers = Math.min(samplingThreads(), samples);
        int[] shares = shares(samples, workers);
        var tasks = new ArrayList<Callable<Map<OWLAxiom, Double>>>(workers);
        for (int worker = 0; worker < workers; worker++) {
            final int samplesForWorker = shares[worker];
            final int workerIndex = worker;
            tasks.add(() -> {
                var permutation = new ArrayList<OWLAxiom>(orderedContext);
                var random = new Random(mixSeed(seed, workerIndex));
                Map<OWLAxiom, Double> localTotals = new HashMap<>();
                for (int i = 0; i < samplesForWorker; i++) {
                    Collections.shuffle(permutation, random);

                    for (OWLAxiom candidate : orderedCandidates) {
                        Set<OWLAxiom> prefix = new HashSet<>(
                                permutation.subList(0, random.nextInt(permutation.size() + 1)));
                        int valueWithoutCandidate = consistencyValue(prefix);
                        if (valueWithoutCandidate == 0) {
                            // The candidate can only swing a consistent coalition, so a
                            // prefix that is already inconsistent contributes nothing.
                            var prefixWith = new HashSet<>(prefix);
                            prefixWith.add(candidate);
                            localTotals.merge(candidate, (double) consistencyValue(prefixWith) - valueWithoutCandidate,
                                    Double::sum);
                        }
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
     *
     * @param tasks
     *            The sampling tasks, one per worker.
     * @param <T>
     *            The type the tasks return.
     * @return The result of the tasks, in the order they were given.
     */
    private <T> List<T> runInParallel(List<Callable<T>> tasks) {
        var pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            var thread = new Thread(runnable);
            thread.setName("shapley-weaker-sampling");
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
            throw new RuntimeException("Interrupted while sampling Shapley values for weakenings.", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Failed to sample Shapley values for weakenings.", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Looks the coalition up in the cache, checking it with a reasoner only the
     * first time it is seen.
     *
     * @param subset
     *            The coalition to check.
     * @return One if the coalition is inconsistent, zero otherwise.
     */
    private int consistencyValue(Set<OWLAxiom> subset) {
        return consistency.apply(Set.copyOf(subset));
    }

    /**
     * @return How many workers the samples of one round are spread over, as set by
     *         the {@code ontologyutils.samplingThreads} system property.
     */
    private static int samplingThreads() {
        return Math.max(1, Integer.getInteger("ontologyutils.samplingThreads", 16));
    }

    /**
     * Spreads the samples of one round over the workers as evenly as possible.
     *
     * @param samples
     *            The number of samples to spread.
     * @param workers
     *            The number of workers to spread them over.
     * @return For every worker, how many samples it draws; the values sum to
     *         {@code samples}.
     */
    private static int[] shares(int samples, int workers) {
        int[] result = new int[workers];
        int base = samples / workers;
        int remainder = samples % workers;
        for (int worker = 0; worker < workers; worker++) {
            result[worker] = base + (worker < remainder ? 1 : 0);
        }
        return result;
    }

    /**
     * Derives an independent seed for a worker or a round from the seed of the
     * enclosing scope.
     *
     * @param seed
     *            The seed to derive from.
     * @param salt
     *            The worker index or the number of samples already drawn.
     * @return A seed that is unrelated to the given one in its high and low bits.
     */
    private static long mixSeed(long seed, int salt) {
        long mixed = seed ^ (0x9E3779B97F4A7C15L * (salt + 1L));
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);
        return mixed;
    }

    /**
     * Makes the samples depend on the game as well as on the seed, so that two
     * weakenings scored in the same trial do not draw the same permutations.
     *
     * @param baseSeed
     *            The seed this instance was created with.
     * @param universe
     *            The context plus the candidates.
     * @param targets
     *            The candidates that are scored.
     * @return The seed the rounds of this scoring call are derived from.
     */
    private static long seedFor(long baseSeed, Set<OWLAxiom> universe, Set<OWLAxiom> targets) {
        long seed = baseSeed;
        seed = 31L * seed + canonicalize(universe).hashCode();
        seed = 31L * seed + canonicalize(targets).hashCode();
        return seed;
    }

    /**
     * Writes a set of axioms as one string, independently of the iteration order of
     * the set.
     *
     * @param axioms
     *            The axioms to write.
     * @return The sorted printed forms of the axioms, joined by a separator.
     */
    private static String canonicalize(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().map(Object::toString).collect(Collectors.joining("|"));
    }

    /**
     * Orders the axioms so that a permutation of them only depends on the seed, and
     * not on the order a set happens to iterate in.
     *
     * @param axioms
     *            The axioms to order.
     * @return The axioms in their natural order.
     */
    private static List<OWLAxiom> sorted(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @param context
     *            The game that every replacement would produce, without the
     *            candidates.
     * @param candidates
     *            The weakenings to score.
     * @return The context together with all the candidates.
     */
    private static Set<OWLAxiom> union(Set<OWLAxiom> context, Set<OWLAxiom> candidates) {
        Set<OWLAxiom> universe = new HashSet<>(context);
        universe.addAll(candidates);
        return universe;
    }

    /**
     * @param targets
     *            The candidates whose marginals are accumulated.
     * @return A zero total for every target.
     */
    private static Map<OWLAxiom, Double> emptyTotals(Set<OWLAxiom> targets) {
        Map<OWLAxiom, Double> totals = new HashMap<>();
        for (OWLAxiom target : targets) {
            totals.put(target, 0.0d);
        }
        return totals;
    }

    /**
     * Adds the totals of one round, or of one worker, to the totals of the whole
     * scoring call.
     *
     * @param totals
     *            The totals to add to, changed in place.
     * @param partial
     *            The totals to add.
     */
    private static void mergeTo(Map<OWLAxiom, Double> totals, Map<OWLAxiom, Double> partial) {
        for (Map.Entry<OWLAxiom, Double> entry : partial.entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    /**
     * Turns the accumulated marginals into the mean marginal contribution, which is
     * the estimate of the value.
     *
     * @param totals
     *            The sum of the marginal contributions of every target.
     * @param samples
     *            The number of samples the sums were taken over.
     * @return The mean marginal contribution of every target.
     */
    private static Map<OWLAxiom, Double> average(Map<OWLAxiom, Double> totals, int samples) {
        Map<OWLAxiom, Double> averaged = new HashMap<>();
        for (Map.Entry<OWLAxiom, Double> entry : totals.entrySet()) {
            averaged.put(entry.getKey(), entry.getValue() / (double) samples);
        }
        return averaged;
    }

    /**
     * Draws the samples of one round, so that the sampling loop can be written once
     * and used for both directions of the ranking.
     */
    @FunctionalInterface
    private interface Sampler {
        /**
         * @param samples
         *            The number of permutations to draw.
         * @param seed
         *            The seed of this round.
         * @return The sum of the marginal contributions of every target.
         */
        Map<OWLAxiom, Double> sample(int samples, long seed);
    }
}
