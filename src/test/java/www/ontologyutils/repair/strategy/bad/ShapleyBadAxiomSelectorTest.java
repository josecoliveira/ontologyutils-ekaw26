package www.ontologyutils.repair.strategy.bad;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * A strategy whose notion of influence is exact can be checked against the
 * Hand-and-Shapley invariant, and the tie-break of the shared selection helper
 * can be checked without any sampling at all.
 */
public class ShapleyBadAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(ShapleyBadAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void exactValuesOfOneGameSumToOne() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(java.util.stream.Collectors.toSet());

            var scores = new ShapleyBadAxiomSelector().score(players);

            assertEquals(players.size(), scores.size());
            assertEquals(1.0, scores.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9,
                    "the exact Shapley values of one game must sum to one");
        }
    }

    @Test
    public void exactValueOfTheMUSMemberIsOneThird() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(java.util.stream.Collectors.toSet());
            OWLAxiom culprit = players.stream()
                    .filter(axiom -> Utils.prettyPrintAxiomDL(axiom).equals("RaiseWages(Sweden)"))
                    .findFirst()
                    .orElseThrow();

            assertEquals(1.0 / 3.0, new ShapleyBadAxiomSelector().score(players).get(culprit), 1e-9);
        }
    }

    @Test
    public void selectsTheHighestScoringAxiomOfAnotherOntology() {
        var resourceName = "/inconsistent/leftpolicies-ok.owl";
        var path = Objects.requireNonNull(ShapleyBadAxiomSelectorTest.class.getResource(resourceName),
                () -> "Missing test resource: " + resourceName).getFile();
        try (var ontology = Ontology.loadOntology(path)) {
            var selected = new ShapleyBadAxiomSelector().selectBest(ontology, Ontology::isConsistent).orElseThrow();

            assertEquals("RaiseWages \u2291 \u00ac RaiseWelfare", Utils.prettyPrintAxiomDL(selected));
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
            var expected = firstTwo.stream().max(java.util.Comparator.comparing(Utils::prettyPrintAxiomDL)).orElseThrow();

            assertEquals(expected, BadAxiomSelector.highestScoring(scores));
        }
    }

    @Test
    public void theHighestScoreWinsRegardlessOfTheOtherValues() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var axioms = ontology.refutableAxioms().limit(3).toList();
            Map<OWLAxiom, Double> scores = Map.of(axioms.get(0), 0.1d, axioms.get(1), 0.7d, axioms.get(2), 0.2d);

            assertEquals(axioms.get(1), BadAxiomSelector.highestScoring(scores));
        }
    }

    @Test
    public void anEmptyScoreMapIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BadAxiomSelector.highestScoring(Map.<OWLAxiom, Double> of()));
    }
}
