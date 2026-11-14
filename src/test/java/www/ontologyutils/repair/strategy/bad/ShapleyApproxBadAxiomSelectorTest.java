package www.ontologyutils.repair.strategy.bad;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * The bad-axiom stage scores all the axioms as players of one shared game.
 *
 * <p>
 * When the axioms were instead scored outside that game every value was zero and
 * the selection fell back to the tie-break, so these tests assert both that the
 * values are not degenerate and that they sum to one.
 */
public class ShapleyApproxBadAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(ShapleyApproxBadAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    private static final List<String> MUS_AXIOMS = List.of(
            "RaiseWages(Sweden)",
            "RaiseWelfare(Sweden)",
            "LeftPolicy=RaiseWages \u2294 RaiseWelfare");

    @Test
    public void approximateValuesAreNotDegenerateAndTheCulpritsScoreHighest() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());
            assertFalse(ontology.isConsistent(), "the test ontology must be inconsistent");

            var scores = new ShapleyApproxBadAxiomSelector(735, 735, 13L).score(players);

            assertEquals(players.size(), scores.size());
            assertTrue(scores.values().stream().distinct().count() > 1,
                    "all bad-axiom scores are identical: " + scores);

            var byAxiom = new HashMap<String, Double>();
            for (var entry : scores.entrySet()) {
                byAxiom.put(Utils.prettyPrintAxiomDL(entry.getKey()), entry.getValue());
            }
            // The only inconsistent subset is {disjoint union, RaiseWages(Sweden),
            // RaiseWelfare(Sweden)}, so exactly these three axioms have a positive value.
            for (var axiom : MUS_AXIOMS) {
                assertTrue(byAxiom.containsKey(axiom), "missing axiom in scores: " + axiom + " in " + byAxiom);
                assertTrue(byAxiom.get(axiom) > 0.15,
                        "axiom in the single MUS should score about 1/3, got " + byAxiom.get(axiom) + " for " + axiom);
            }
            assertEquals(0.0, byAxiom.get("LeftPolicy(Sweden)"), 0.05,
                    "the irrelevant assertion should score about 0");
        }
    }

    @Test
    public void approximateValuesOfOneGameSumToOne() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());

            var scores = new ShapleyApproxBadAxiomSelector(735, 735, 13L).score(players);

            assertEquals(1.0, sum(scores), 0.15, "the Shapley values of one game must sum to about one");
        }
    }

    @Test
    public void approximateValuesMatchTheExactValuesOfTheSameGame() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());

            var exact = new ShapleyBadAxiomSelector().score(players);
            // A single round of samples, so the comparison is about the estimator and
            // not about the early stopping. The sample skips every axiom that enters
            // an already inconsistent coalition, which must not change the estimate.
            var approxim = new ShapleyApproxBadAxiomSelector(735, 735, 13L).score(players);

            for (var player : players) {
                assertEquals(exact.get(player), approxim.get(player), 0.1,
                        "the sampled value of " + Utils.prettyPrintAxiomDL(player)
                                + " disagrees with its exact value");
            }
        }
    }

    @Test
    public void selectsTheCulpritRatherThanTheTieBreakWinner() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var selected = new ShapleyApproxBadAxiomSelector().selectBest(ontology, Ontology::isConsistent).orElseThrow();

            assertTrue(MUS_AXIOMS.contains(Utils.prettyPrintAxiomDL(selected)),
                    "the selected bad axiom is not responsible for the inconsistency: "
                            + Utils.prettyPrintAxiomDL(selected));
        }
    }

    private static double sum(java.util.Map<OWLAxiom, Double> values) {
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
