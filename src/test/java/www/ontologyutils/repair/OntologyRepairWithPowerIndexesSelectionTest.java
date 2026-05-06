package www.ontologyutils.repair;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

public class OntologyRepairWithPowerIndexesSelectionTest {
    @Test
    public void selectsHighestScoringBadAxiomForLeftPoliciesExactPreset() throws Exception {
        var resourceName = "/inconsistent/leftpolicies-ok.owl";
        var path = Objects.requireNonNull(OntologyRepairTest.class.getResource(resourceName),
                () -> "Missing test resource: " + resourceName).getFile();
        try (var ontology = Ontology.loadOntology(path)) {
            var repair = new OntologyRepairWithPowerIndexes(
                    Ontology::isConsistent,
                    OntologyRepairWithPowerIndexes.RefOntologyStrategy.ONE_MCS,
                    OntologyRepairWithPowerIndexes.BadAxiomStrategy.SHAPLEY_EXACT,
                    OntologyRepairWithPowerIndexes.WeakerAxiomStrategy.SHAPLEY_EXACT,
                    AxiomWeakener.FLAG_SROIQ_STRICT | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                            | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE | AxiomWeakener.FLAG_NNF_STRICT
                            | AxiomWeakener.FLAG_ALC_STRICT | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
                            | AxiomWeakener.FLAG_OWL2_SET_OPERANDS,
                    false);

            var currentAxioms = ontology.refutableAxioms().collect(Collectors.toSet());
            Method computeBadAxiomScores = OntologyRepairWithPowerIndexes.class
                    .getDeclaredMethod("computeBadAxiomScores", Set.class);
            computeBadAxiomScores.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<OWLAxiom, Double> badAxiomScores = (Map<OWLAxiom, Double>) computeBadAxiomScores.invoke(repair,
                    currentAxioms);

            Method selectBadAxiom = OntologyRepairWithPowerIndexes.class.getDeclaredMethod("selectBadAxiom",
                    Map.class);
            selectBadAxiom.setAccessible(true);
            OWLAxiom selectedBadAxiom = (OWLAxiom) selectBadAxiom.invoke(repair, badAxiomScores);

            assertEquals("RaiseWages ⊑ ¬ RaiseWelfare", Utils.prettyPrintAxiomDL(selectedBadAxiom));
        }
    }
}






