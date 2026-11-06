package www.ontologyutils.repair.powerindex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Regression tests for the adaptive Monte Carlo estimator. When the target set
 * coincides with the universe (as it does when scoring bad axioms) the prefix of
 * the sampled permutation must still contain the other targets, otherwise every
 * marginal contribution degenerates to zero and the ranking is decided by the
 * tie-break only.
 */
public class ShapleyInconsistencyValueApproximateTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(ShapleyInconsistencyValueApproximateTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void adaptiveScoresAreNotDegenerateWhenTargetsAreTheWholeUniverse() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var universe = ontology.refutableAxioms().collect(Collectors.toSet());
            assertFalse(ontology.isConsistent(), "the test ontology must be inconsistent");

            var powerIndex = new ShapleyInconsistencyValueApproximate(735, 13L);
            var scores = powerIndex.computeScoresAdaptive(universe, universe, 50, 500, false);

            assertEquals(universe.size(), scores.size());
            var distinctValues = scores.values().stream().distinct().count();
            assertTrue(distinctValues > 1, "all bad-axiom scores are identical: " + scores);

            var byAxiom = new HashMap<String, Double>();
            for (var entry : scores.entrySet()) {
                byAxiom.put(Utils.prettyPrintAxiomDL(entry.getKey()), entry.getValue());
            }

            // The only inconsistent subset is {disjoint union, RaiseWages(Sweden),
            // RaiseWelfare(Sweden)}, so exactly these three axioms have a positive value.
            var musAxioms = java.util.List.of(
                    "RaiseWages(Sweden)",
                    "RaiseWelfare(Sweden)",
                    "LeftPolicy=RaiseWages \u2294 RaiseWelfare");
            for (var axiom : musAxioms) {
                assertTrue(byAxiom.containsKey(axiom), "missing axiom in scores: " + axiom + " in " + byAxiom);
                assertTrue(byAxiom.get(axiom) > 0.15,
                        "axiom in the single MUS should score about 1/3, got " + byAxiom.get(axiom) + " for " + axiom);
            }
            assertEquals(0.0, byAxiom.get("LeftPolicy(Sweden)"), 0.05,
                    "the irrelevant assertion should score about 0");

            // The selected bad axiom must be one of the axioms of the MUS, never the
            // alphabetically greatest axiom of the ontology.
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

            var powerIndex = new ShapleyInconsistencyValueApproximate(735, 13L);
            assertEquals(1.0 / 3.0, powerIndex.computeScore(universe, badAxiom), 0.1);
        }
    }

    /**
     * Every target must be scored in the game {@code axioms u {target}}, exactly
     * like {@link #computeScore(Set, OWLAxiom)}. Scoring the candidates in one
     * shared game would let a candidate influence the score of its siblings.
     */
    @Test
    public void batchScoresUseTheGameOfEachTargetAlone() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var axioms = ontology.refutableAxioms()
                    .collect(Collectors.toMap(Utils::prettyPrintAxiomDL, axiom -> axiom));
            var disjointUnion = axioms.get("LeftPolicy=RaiseWages \u2294 RaiseWelfare");
            var raiseWages = axioms.get("RaiseWages(Sweden)");
            var raiseWelfare = axioms.get("RaiseWelfare(Sweden)");
            var leftPolicy = axioms.get("LeftPolicy(Sweden)");

            var powerIndex = new ShapleyInconsistencyValueApproximate(735, 13L);

            // On their own the two candidates are harmless, so each scores 0 in its own
            // game; a shared game would make them score 1/2 each.
            var harmless = powerIndex.computeScores(Set.of(disjointUnion), Set.of(raiseWages, raiseWelfare));
            assertEquals(0.0, harmless.get(raiseWages), 1e-9,
                    "the sibling candidate must not be a player of the game");
            assertEquals(0.0, harmless.get(raiseWelfare), 1e-9,
                    "the sibling candidate must not be a player of the game");

            // One candidate completes the contradiction, the other one is inert. The value is
            // 1/3: the candidate only causes inconsistency when its prefix holds both the
            // disjoint union and RaiseWelfare(Sweden).
            var context = Set.of(disjointUnion, raiseWelfare);
            var mixed = powerIndex.computeScores(context, Set.of(raiseWages, leftPolicy));
            assertEquals(powerIndex.computeScore(context, raiseWages), mixed.get(raiseWages), 0.06);
            assertEquals(1.0 / 3.0, mixed.get(raiseWages), 0.06);
            assertEquals(0.0, mixed.get(leftPolicy), 0.06);
        }
    }
}
