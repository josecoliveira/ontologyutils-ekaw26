package www.ontologyutils.repair.strategy.weaker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * The exact Banzhaf weakening strategy enumerates every coalition of each
 * candidate's own game, so its values can be checked against a hand-computed game
 * and against the sampled strategy it is meant to validate.
 */
public class BanzhafWeakerAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(BanzhafWeakerAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void exactValuesMatchTheHandComputedGame() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var raiseWages = byName.get("RaiseWages(Sweden)");
            var leftPolicy = byName.get("LeftPolicy(Sweden)");
            // The candidate only causes inconsistency when the coalition holds both the
            // disjoint union and RaiseWelfare(Sweden), which is one of the four coalitions
            // of the other two players in the game.
            var context = Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare"),
                    byName.get("RaiseWelfare(Sweden)"));

            var scores = new BanzhafWeakerAxiomSelector().score(context, Set.of(raiseWages, leftPolicy));

            assertEquals(0.25, scores.get(raiseWages), 1e-9);
            assertEquals(0.0, scores.get(leftPolicy), 1e-9);
        }
    }

    @Test
    public void aCandidateDoesNotMakeItsSiblingsResponsibleForTheInconsistency() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var raiseWages = byName.get("RaiseWages(Sweden)");
            var raiseWelfare = byName.get("RaiseWelfare(Sweden)");

            var scores = new BanzhafWeakerAxiomSelector()
                    .score(Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare")),
                            Set.of(raiseWages, raiseWelfare));

            assertEquals(0.0, scores.get(raiseWages), 1e-9,
                    "the sibling candidate must not be a player of the game");
            assertEquals(0.0, scores.get(raiseWelfare), 1e-9,
                    "the sibling candidate must not be a player of the game");
        }
    }

    @Test
    public void theSampledStrategyConvergesToTheExactOne() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var context = Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare"),
                    byName.get("RaiseWelfare(Sweden)"));
            var candidates = Set.of(byName.get("RaiseWages(Sweden)"), byName.get("LeftPolicy(Sweden)"));

            var exact = new BanzhafWeakerAxiomSelector().score(context, candidates);
            // A single round of samples, so the comparison is about the game and not
            // about the early stopping of the adaptive strategy.
            var sampled = new BanzhafApproxWeakerAxiomSelector(4000, 4000, 13L).score(context, candidates);

            for (var candidate : candidates) {
                assertEquals(exact.get(candidate), sampled.get(candidate), 0.05,
                        "the sampled value of " + Utils.prettyPrintAxiomDL(candidate) + " did not converge");
            }
        }
    }

    private static Map<String, OWLAxiom> axiomsByName(Ontology ontology) {
        return ontology.refutableAxioms().collect(java.util.stream.Collectors.toMap(Utils::prettyPrintAxiomDL,
                axiom -> axiom));
    }
}
