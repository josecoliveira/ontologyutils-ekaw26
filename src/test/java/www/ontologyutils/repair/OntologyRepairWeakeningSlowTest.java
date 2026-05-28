package www.ontologyutils.repair;

import org.junit.jupiter.api.parallel.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairBuilder;
import www.ontologyutils.toolbox.Ontology;

@Execution(ExecutionMode.CONCURRENT)
public class OntologyRepairWeakeningSlowTest extends OntologyRepairTest {
    @Override
    protected OntologyRepair getRepairForConsistency() {
        return OntologyRepairBuilder.forConsistency().withRefStrategy(RefOntologyStrategy.RANDOM_MCS)
                .withBadStrategy(BadAxiomStrategy.IN_MOST_MUS).withWeakeningFlags(AxiomWeakener.FLAG_DEFAULT)
                .withEnhanceRef(false).build();
    }

    @Override
    protected OntologyRepair getRepairForCoherence() {
        return OntologyRepairBuilder.forCoherence().withRefStrategy(RefOntologyStrategy.RANDOM_MCS)
                .withBadStrategy(BadAxiomStrategy.IN_MOST_MUS).withWeakeningFlags(AxiomWeakener.FLAG_DEFAULT)
                .withEnhanceRef(false).build();
    }
}
