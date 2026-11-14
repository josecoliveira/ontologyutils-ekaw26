package www.ontologyutils.repair.strategy.bad;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * The exact Banzhaf strategy enumerates every coalition of the shared game, so
 * its values can be checked against a hand-computed game and against the sampled
 * strategy it is meant to validate.
 */
public class BanzhafBadAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(BanzhafBadAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void exactValuesMatchTheHandComputedGame() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());
            assertFalse(ontology.isConsistent(), "the test ontology must be inconsistent");

            var scores = new BanzhafBadAxiomSelector().score(players);

            assertEquals(players.size(), scores.size());
            var byAxiom = new HashMap<String, Double>();
            for (var entry : scores.entrySet()) {
                byAxiom.put(Utils.prettyPrintAxiomDL(entry.getKey()), entry.getValue());
            }
            // Each axiom of the only inconsistent subset needs the other two in the
            // coalition, which leaves one free axiom, so two of the eight coalitions of
            // the other players swing it: 2/8 = 0.25.
            for (var axiom : List.of("RaiseWages(Sweden)", "RaiseWelfare(Sweden)",
                    "LeftPolicy=RaiseWages \u2294 RaiseWelfare")) {
                assertTrue(byAxiom.containsKey(axiom), "missing axiom in scores: " + axiom + " in " + byAxiom);
                assertEquals(0.25, byAxiom.get(axiom), 1e-9,
                        "axiom in the single MUS should score exactly 0.25, got " + byAxiom.get(axiom));
            }
            assertEquals(0.0, byAxiom.get("LeftPolicy(Sweden)"), 1e-9,
                    "the irrelevant assertion contributes to no inconsistency");
        }
    }

    @Test
    public void theSampledStrategyConvergesToTheExactOne() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = Set.copyOf(ontology.refutableAxioms().toList());

            var exact = new BanzhafBadAxiomSelector().score(players);
            // A single round of samples, so the comparison is about the game and not
            // about the early stopping of the adaptive strategy.
            var sampled = new BanzhafApproxBadAxiomSelector(4000, 4000, 13L).score(players);

            for (var player : players) {
                assertEquals(exact.get(player), sampled.get(player), 0.05,
                        "the sampled value of " + Utils.prettyPrintAxiomDL(player) + " did not converge");
            }
        }
    }

    @Test
    public void selectsAnAxiomThatActuallyContributesToTheInconsistency() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = Set.copyOf(ontology.refutableAxioms().toList());

            var selected = new BanzhafBadAxiomSelector().selectBest(ontology, Ontology::isConsistent).orElseThrow();

            assertTrue(players.contains(selected), "the strategy must return an axiom of the ontology");
            assertTrue(new BanzhafBadAxiomSelector().score(players).get(selected) > 0.15,
                    "the selected bad axiom has no influence on the inconsistency: "
                            + Utils.prettyPrintAxiomDL(selected));
        }
    }

    @Test
    public void anAxiomThatNoCoalitionNeedsScoresZero() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = Set.copyOf(ontology.refutableAxioms().toList());
            var scores = new BanzhafBadAxiomSelector().score(players);

            assertTrue(scores.values().stream().anyMatch(value -> value == 0.0),
                    "an axiom outside every MUS must score zero: " + scores);
            assertTrue(scores.values().stream().allMatch(value -> value >= 0.0 && value <= 1.0),
                    "Banzhaf values are averages of marginals of a 0/1 game: " + scores);
        }
    }
}
