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
 * The Banzhaf weakening strategy draws coalitions of the context and adds the
 * candidate on top, so a candidate is never a player of its sibling's game.
 */
public class BanzhafApproxWeakerAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(BanzhafApproxWeakerAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void aCandidateDoesNotMakeItsSiblingsResponsibleForTheInconsistency() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = axiomsByName(ontology);
            var raiseWages = byName.get("RaiseWages(Sweden)");
            var raiseWelfare = byName.get("RaiseWelfare(Sweden)");

            var scores = new BanzhafApproxWeakerAxiomSelector()
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

            // The candidate only causes inconsistency when the coalition holds both the
            // disjoint union and RaiseWelfare(Sweden), so the value is 1/4. A single round
            // of 735 samples is used so the assertion is about the game, not about early
            // stopping.
            var context = Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare"),
                    byName.get("RaiseWelfare(Sweden)"));
            var scores = new BanzhafApproxWeakerAxiomSelector(735, 735, 13L)
                    .score(context, Set.of(raiseWages, leftPolicy));

            assertEquals(0.25, scores.get(raiseWages), 0.06);
            assertEquals(0.0, scores.get(leftPolicy), 0.06);
        }
    }

    private static Map<String, OWLAxiom> axiomsByName(Ontology ontology) {
        return ontology.refutableAxioms().collect(java.util.stream.Collectors.toMap(Utils::prettyPrintAxiomDL,
                axiom -> axiom));
    }
}
