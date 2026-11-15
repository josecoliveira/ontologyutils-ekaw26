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
 * The Banzhaf bad-axiom strategy draws coalitions of the whole ontology, so every
 * axiom is removed from the same coalition as its siblings.
 */
public class BanzhafApproxBadAxiomSelectorTest {
    private static final String RESOURCE = "/inconsistent/leftpolicies-small.owl";

    private static String resourcePath() {
        return Objects.requireNonNull(BanzhafApproxBadAxiomSelectorTest.class.getResource(RESOURCE),
                () -> "Missing test resource: " + RESOURCE).getFile();
    }

    @Test
    public void valuesAreNotDegenerateAndTheCulpritsScoreHighest() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = ontology.refutableAxioms().collect(Collectors.toSet());
            assertFalse(ontology.isConsistent(), "the test ontology must be inconsistent");

            var scores = new BanzhafApproxBadAxiomSelector(735, 735, 13L).score(players);

            assertEquals(players.size(), scores.size());
            assertTrue(scores.values().stream().distinct().count() > 1,
                    "all bad-axiom scores are identical: " + scores);

            var byAxiom = new HashMap<String, Double>();
            for (var entry : scores.entrySet()) {
                byAxiom.put(Utils.prettyPrintAxiomDL(entry.getKey()), entry.getValue());
            }
            // For each axiom of the only inconsistent subset the other two must be in the
            // coalitions, so the Banzhaf value is 8/32 = 0.25.
            for (var axiom : List.of("RaiseWages(Sweden)", "RaiseWelfare(Sweden)",
                    "LeftPolicy=RaiseWages \u2294 RaiseWelfare")) {
                assertTrue(byAxiom.containsKey(axiom), "missing axiom in scores: " + axiom + " in " + byAxiom);
                assertEquals(0.25, byAxiom.get(axiom), 0.06,
                        "axiom in the single MUS should score about 0.25, got " + byAxiom.get(axiom) + " for " + axiom);
            }
            assertEquals(0.0, byAxiom.get("LeftPolicy(Sweden)"), 0.05,
                    "the irrelevant assertion should score about 0");
        }
    }

    @Test
    public void selectsAnAxiomThatActuallyContributesToTheInconsistency() {
        try (var ontology = Ontology.loadOntology(resourcePath())) {
            var players = Set.copyOf(ontology.refutableAxioms().toList());

            var selected = new BanzhafApproxBadAxiomSelector().selectBest(ontology, Ontology::isConsistent).orElseThrow();

            assertTrue(players.contains(selected), "the strategy must return an axiom of the ontology");
            assertTrue(new BanzhafApproxBadAxiomSelector(735, 735, 13L).score(players).get(selected) > 0.15,
                    "the selected bad axiom has no influence on the inconsistency: "
                            + Utils.prettyPrintAxiomDL(selected));
        }
    }

    private static double sum(java.util.Map<OWLAxiom, Double> values) {
        return values.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
