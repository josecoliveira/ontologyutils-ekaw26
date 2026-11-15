package www.ontologyutils.repair.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.strategy.bad.ShapleyApproxBadAxiomSelector;
import www.ontologyutils.repair.strategy.weaker.ShapleyApproxWeakerAxiomSelector;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * The two selection stages compute different games, so they must have different
 * strategies and must score the same axiom differently.
 *
 * <p>
 * This is the test that the design is meant to make impossible to get wrong:
 * there is no class that could answer for both stages, and the numbers below show
 * why one class could not serve both anyway.
 */
public class GameSeparationTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(GameSeparationTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void theTwoStagesScoreTheSameAxiomDifferently() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = ontology.refutableAxioms()
                    .collect(java.util.stream.Collectors.toMap(Utils::prettyPrintAxiomDL, axiom -> axiom));
            var culprit = byName.get("RaiseWages(Sweden)");
            var sibling = byName.get("RaiseWelfare(Sweden)");
            var players = Set.<OWLAxiom> copyOf(byName.values());

            // Bad-axiom stage: the culprit is a player of the whole ontology, where it
            // shares the only inconsistent subset with its sibling, so it is worth 1/3.
            double asPlayer = new ShapleyApproxBadAxiomSelector(735, 735, 13L).score(players).get(culprit);

            // Weakening stage: the same axiom is a candidate in its own game, played by
            // the ontology without the culprit and without the sibling it needs, so it
            // cannot cause any inconsistency and is worth 0.
            var context = new HashSet<>(players);
            context.remove(culprit);
            context.remove(sibling);
            double asCandidate = new ShapleyApproxWeakerAxiomSelector().score(context, Set.of(culprit)).get(culprit);

            assertEquals(1.0 / 3.0, asPlayer, 0.06);
            assertEquals(0.0, asCandidate, 1e-9);
            assertNotEquals(asPlayer, asCandidate,
                    "one strategy for both stages would have to return one of these two numbers");
        }
    }

}
