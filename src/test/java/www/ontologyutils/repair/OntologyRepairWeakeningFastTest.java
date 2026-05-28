package www.ontologyutils.repair;

import org.junit.jupiter.api.parallel.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairBuilder;
import www.ontologyutils.toolbox.Ontology;

@Execution(ExecutionMode.CONCURRENT)
public class OntologyRepairWeakeningFastTest extends OntologyRepairTest {
    @Override
    protected OntologyRepair getRepairForConsistency() {
        return OntologyRepairBuilder.forConsistency().withRefStrategy(RefOntologyStrategy.ONE_MCS)
                .withBadStrategy(BadAxiomStrategy.IN_ONE_MUS).withWeakeningFlags(AxiomWeakener.FLAG_DEFAULT)
                .withEnhanceRef(true).build();
    }

    @Override
    protected OntologyRepair getRepairForCoherence() {
        return OntologyRepairBuilder.forCoherence().withRefStrategy(RefOntologyStrategy.ONE_MCS)
                .withBadStrategy(BadAxiomStrategy.IN_ONE_MUS).withWeakeningFlags(AxiomWeakener.FLAG_DEFAULT)
                .withEnhanceRef(true).build();
    }
}
