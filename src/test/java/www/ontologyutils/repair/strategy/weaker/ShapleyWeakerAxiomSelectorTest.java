package www.ontologyutils.repair.strategy.weaker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * A weakening strategy whose notion of influence is exact can be checked against
 * the value of the candidate in its own game, and the helpers that every
 * weakening strategy shares can be checked without any sampling.
 */
public class ShapleyWeakerAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(ShapleyWeakerAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void aCandidateIsScoredInTheGameOfItsReplacement() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var byName = ontology.refutableAxioms()
                    .collect(java.util.stream.Collectors.toMap(Utils::prettyPrintAxiomDL, axiom -> axiom));
            var context = Set.of(byName.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare"),
                    byName.get("RaiseWelfare(Sweden)"));
            var candidates = Set.of(byName.get("RaiseWages(Sweden)"), byName.get("LeftPolicy(Sweden)"));

            var scores = new ShapleyWeakerAxiomSelector().score(context, candidates);

            assertEquals(1.0 / 3.0, scores.get(byName.get("RaiseWages(Sweden)")), 1e-9);
            assertEquals(0.0, scores.get(byName.get("LeftPolicy(Sweden)")), 1e-9);
        }
    }

    @Test
    public void theScoringContextExcludesTheAxiomBeingReplaced() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var axioms = ontology.refutableAxioms().toList();
            var badAxiom = axioms.get(0);
            var context = Set.copyOf(axioms);
            assertTrue(context.contains(badAxiom));

            var scoringContext = WeakerAxiomSelector.scoringContext(context, badAxiom);

            assertFalse(scoringContext.contains(badAxiom),
                    "every candidate is judged against the ontology its replacement produces");
            assertEquals(context.size() - 1, scoringContext.size());
        }
    }

    @Test
    public void tiesAreBrokenByThePrintedFormOfTheAxiom() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var firstTwo = ontology.refutableAxioms().limit(2).toList();
            var scores = new HashMap<OWLAxiom, Double>();
            for (var axiom : firstTwo) {
                scores.put(axiom, 1.0d);
            }
            var expected = firstTwo.stream().min(java.util.Comparator.comparing(Utils::prettyPrintAxiomDL))
                    .orElseThrow();

            assertEquals(expected, WeakerAxiomSelector.lowestScoring(scores));
        }
    }

    @Test
    public void theLowestScoreWinsRegardlessOfTheOtherValues() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var axioms = ontology.refutableAxioms().limit(3).toList();

            var winner = WeakerAxiomSelector.lowestScoring(
                    java.util.Map.of(axioms.get(0), 0.5d, axioms.get(1), 0.1d, axioms.get(2), 0.2d));

            assertEquals(axioms.get(1), winner);
        }
    }

    @Test
    public void anEmptyScoreMapIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WeakerAxiomSelector.lowestScoring(java.util.Map.<OWLAxiom, Double> of()));
    }
}
