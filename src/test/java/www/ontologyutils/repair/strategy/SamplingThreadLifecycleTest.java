package www.ontologyutils.repair.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import www.ontologyutils.repair.strategy.bad.ShapleyApproxBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.ShapleyApproxWeakerAxiomSelector;
import www.ontologyutils.toolbox.Ontology;

/**
 * The sampling strategies run their workers on a pool that belongs to one scoring
 * call, so a strategy that is created, used and dropped must not leave threads
 * behind.
 *
 * <p>
 * This matters because an experiment creates a new strategy for every arm of every
 * trial, and the experiments that run many trials in one JVM would otherwise
 * accumulate pools until the process cannot create native threads.
 */
public class SamplingThreadLifecycleTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(SamplingThreadLifecycleTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void repeatedSamplingDoesNotAccumulateThreads() throws InterruptedException {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());

            // One warm-up round, so the reasoner threads that any strategy creates are
            // already counted in the baseline.
            new ShapleyApproxBadAxiomSelector(10, 10, 13L).score(players);
            int before = liveThreads();

            for (int i = 0; i < 40; i++) {
                var badSelector = new ShapleyApproxBadAxiomSelector(10, 10, 13L + i);
                assertFalse(badSelector.score(players).isEmpty(), "the strategy must produce scores");
            }

            assertTrue(awaitThreadCount(before + 4),
                    "sampling left threads behind: " + before + " before, " + liveThreads() + " after");
        }
    }

    @Test
    public void aStrategyCanBeDroppedAfterUse() throws InterruptedException {
        // Different strategy classes, each of which owns its own workers.
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());
            int before = liveThreads();

            for (int i = 0; i < 10; i++) {
                new ShapleyApproxBadAxiomSelector(10, 10, 13L).score(players);
                var context = players.stream().limit(1).collect(Collectors.toSet());
                var candidates = players.stream().skip(1).limit(2).collect(Collectors.toSet());
                if (!context.isEmpty() && !candidates.isEmpty()) {
                    new ShapleyApproxWeakerAxiomSelector(10, 10, 13L).score(context, candidates);
                }
            }

            assertTrue(awaitThreadCount(before + 4),
                    "sampling left threads behind: " + before + " before, " + liveThreads() + " after");
        }
    }

    private static int liveThreads() {
        return Thread.getAllStackTraces().size();
    }

    private static boolean awaitThreadCount(int limit) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (liveThreads() <= limit) {
                return true;
            }
            Thread.sleep(20);
        }
        return liveThreads() <= limit;
    }
}
