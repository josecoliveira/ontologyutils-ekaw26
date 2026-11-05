package www.ontologyutils.repair.powerindex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Regression tests for the adaptive Banzhaf estimator. The Banzhaf index
 * averages the marginal contribution over the coalitions of the other axioms,
 * so when the targets are the whole universe the coalition must still be able
 * to contain the other targets; excluding them left the coalition empty and all
 * scores at zero.
 */
public class BanzhafInconsistencyValueApproximateTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(BanzhafInconsistencyValueApproximateTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void adaptiveScoresAreNotDegenerateWhenTargetsAreTheWholeUniverse() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var universe = ontology.refutableAxioms().collect(Collectors.toSet());
            assertFalse(ontology.isConsistent(), "the test ontology must be inconsistent");

            var powerIndex = new BanzhafInconsistencyValueApproximate(735, 13L);
            var scores = powerIndex.computeScoresAdaptive(universe, universe, 50, 500, false);

            assertEquals(universe.size(), scores.size());
            assertTrue(scores.values().stream().distinct().count() > 1,
                    "all bad-axiom scores are identical: " + scores);

            var byAxiom = new HashMap<String, Double>();
            for (var entry : scores.entrySet()) {
                byAxiom.put(Utils.prettyPrintAxiomDL(entry.getKey()), entry.getValue());
            }

            // The only inconsistent subset has three axioms; for each of them the other
            // two must be in the coalition, so the Banzhaf index is 8/32 = 0.25.
            var musAxioms = List.of(
                    "RaiseWages(Sweden)",
                    "RaiseWelfare(Sweden)",
                    "LeftPolicy=RaiseWages \u2294 RaiseWelfare");
            for (var axiom : musAxioms) {
                assertTrue(byAxiom.containsKey(axiom), "missing axiom in scores: " + axiom + " in " + byAxiom);
                assertTrue(byAxiom.get(axiom) > 0.15,
                        "axiom in the single MUS should score about 0.25, got " + byAxiom.get(axiom) + " for " + axiom);
            }
            assertEquals(0.0, byAxiom.get("LeftPolicy(Sweden)"), 0.05,
                    "the irrelevant assertion should score about 0");

            var selected = scores.entrySet().stream()
                    .max(java.util.Comparator
                            .<java.util.Map.Entry<OWLAxiom, Double>>comparingDouble(java.util.Map.Entry::getValue)
                            .thenComparing(e -> Utils.prettyPrintAxiomDL(e.getKey())))
                    .orElseThrow()
                    .getKey();
            assertTrue(musAxioms.contains(Utils.prettyPrintAxiomDL(selected)),
                    "selected bad axiom is not responsible for the inconsistency: "
                            + Utils.prettyPrintAxiomDL(selected));
        }
    }

    @Test
    public void marginalContributionOfASingleTargetIsNotAlwaysZero() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var universe = ontology.refutableAxioms().collect(Collectors.toSet());
            var badAxiom = universe.stream()
                    .filter(axiom -> Utils.prettyPrintAxiomDL(axiom).equals("RaiseWages(Sweden)"))
                    .findFirst()
                    .orElseThrow();

            var powerIndex = new BanzhafInconsistencyValueApproximate(735, 13L);
            assertEquals(0.25, powerIndex.computeScore(universe, badAxiom), 0.08);
        }
    }
}
