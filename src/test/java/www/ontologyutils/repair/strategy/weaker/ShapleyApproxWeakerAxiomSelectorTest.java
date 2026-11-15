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
 * The weakening stage scores each candidate in the game its own replacement
 * would produce.
 *
 * <p>
 * Scoring the candidates in one shared game would let a candidate influence the
 * score of its siblings; a harmless candidate would then look responsible for an
 * inconsistency that only its sibling completes.
 */
public class ShapleyApproxWeakerAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(ShapleyApproxWeakerAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void aCandidateDoesNotMakeItsSiblingsResponsibleForTheInconsistency() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var raiseWages = byName.get("RaiseWages(Sweden)");
            var raiseWelfare = byName.get("RaiseWelfare(Sweden)");

            // On their own the two candidates are harmless, so each scores 0 in its own
            // game. In a shared game they would score about 1/2 each.
            var scores = new ShapleyApproxWeakerAxiomSelector()
                    .score(Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare")),
                            Set.of(raiseWages, raiseWelfare));

            assertEquals(0.0, scores.get(raiseWages), 1e-9,
                    "the sibling candidate must not be a player of the game");
            assertEquals(0.0, scores.get(raiseWelfare), 1e-9,
                    "the sibling candidate must not be a player of the game");
        }
    }

    @Test
    public void aCandidateIsScoredInItsOwnGame() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var raiseWages = byName.get("RaiseWages(Sweden)");
            var leftPolicy = byName.get("LeftPolicy(Sweden)");

            // One candidate completes the contradiction, the other one is inert. The value
            // is 1/3: the candidate only causes inconsistency when its prefix holds both
            // the disjoint union and RaiseWelfare(Sweden). A single round of 735 samples
            // is used so the assertion is about the game, not about early stopping.
            var context = Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare"),
                    byName.get("RaiseWelfare(Sweden)"));
            var scores = new ShapleyApproxWeakerAxiomSelector(735, 735, 13L)
                    .score(context, Set.of(raiseWages, leftPolicy));

            assertEquals(1.0 / 3.0, scores.get(raiseWages), 0.06);
            assertEquals(0.0, scores.get(leftPolicy), 0.06);
        }
    }

    private static Map<String, OWLAxiom> axiomsByName(Ontology ontology) {
        return ontology.refutableAxioms().collect(java.util.stream.Collectors.toMap(Utils::prettyPrintAxiomDL,
                axiom -> axiom));
    }
}
