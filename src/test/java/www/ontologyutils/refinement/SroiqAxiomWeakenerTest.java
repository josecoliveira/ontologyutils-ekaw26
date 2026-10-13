package www.ontologyutils.refinement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.semanticweb.owlapi.model.*;

import www.ontologyutils.toolbox.*;

@Execution(ExecutionMode.CONCURRENT)
public class SroiqAxiomWeakenerTest {
    private static final String ONTOLOGY_IRI = "http://www.semanticweb.org/roland/ontologies/2023/3/untitled/";
    /**
     * Ontologies for which Openllet currently returns a wrong (false) entailment
     * answer. HermiT is used for those (see README "Reasoner Support and
     * Limitations").
     */
    private static final Set<String> HERMIT_ONTOLOGIES = Set.of("/alcrq/BuildingStructure.owl");

    private Ontology ontology;
    private AxiomWeakener axiomWeakener;

    @BeforeEach
    public void setup() {
        var path = RoleCoverTest.class.getResource("/alcri/sroiq-tests.owl").getFile();
        ontology = Ontology.loadOntology(path);
        axiomWeakener = new AxiomWeakener(ontology, ontology, AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT);
    }

    @AfterEach
    public void teardown() {
        ontology.close();
    }

    private static Stream<Arguments> expectedWeakening() {
        var df = Ontology.getDefaultDataFactory();
        return Stream.of(
                Arguments.of(
                        Set.of(
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Mother").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Father").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person")).getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLThing()),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(df.getOWLClass(ONTOLOGY_IRI, "Grandparent"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                                df.getOWLSubClassOfAxiom(df.getOWLThing(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf())),
                        df.getOWLSubClassOfAxiom(df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf())),
                Arguments.of(
                        Set.of(
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLObjectIntersectionOf(
                                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test")),
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test")),
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test")),
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLObjectIntersectionOf(
                                                df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test")),
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLObjectIntersectionOf(
                                                df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLThing()),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test")),
                                df.getOWLClassAssertionAxiom(
                                        df.getOWLObjectIntersectionOf(
                                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test"))),
                        df.getOWLClassAssertionAxiom(
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "Test"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLObjectPropertyAssertionAxiom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "B"))),
                Arguments.of(
                        Set.of(
                                df.getOWLNegativeObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLNegativeObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLNegativeObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLNegativeObjectPropertyAssertionAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLNegativeObjectPropertyAssertionAxiom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "B"))),
                Arguments.of(
                        Set.of(
                                df.getOWLSameIndividualAxiom(
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLSameIndividualAxiom(
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "B"))),
                Arguments.of(
                        Set.of(
                                df.getOWLDifferentIndividualsAxiom(
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                        df.getOWLNamedIndividual(ONTOLOGY_IRI, "B")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLDifferentIndividualsAxiom(
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "A"),
                                df.getOWLNamedIndividual(ONTOLOGY_IRI, "B"))),
                Arguments.of(
                        Set.of(
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLSubObjectPropertyOfAxiom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf"))),
                Arguments.of(
                        Set.of(
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLSubObjectPropertyOfAxiom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "grandparentOf"),
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"))),
                Arguments.of(
                        Set.of(
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty()),
                                df.getOWLSubObjectPropertyOfAxiom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLSubObjectPropertyOfAxiom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"))),
                Arguments.of(
                        Set.of(
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubPropertyChainOfAxiom(
                                        List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty()),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf")),
                                df.getOWLSubClassOfAxiom(
                                        df.getOWLNothing(), df.getOWLThing())),
                        df.getOWLSubPropertyChainOfAxiom(
                                List.of(df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"))));
    }

    @ParameterizedTest
    @MethodSource("expectedWeakening")
    public void weakenAxiom(Set<OWLAxiom> expected, OWLAxiom axiom) {
        assertEquals(expected, Utils.toSet(axiomWeakener.weakerAxioms(axiom)));
    }

    @Test
    public void allWeakerAxiomsAreEntailed() {
        try (var copy = ontology.clone()) {
            ontology.logicalAxioms().forEach(strongAxiom -> {
                axiomWeakener.weakerAxioms(strongAxiom).forEach(weakAxiom -> {
                    assertTrue(copy.isEntailed(weakAxiom));
                });
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/alch/catsandnumbers.owl", "/el/bodysystem.owl", "/alc/bfo.owl", "/el/aeo.owl",
            "/el/duo.owl", "/el/a-and-b.owl", "/el/Empty.owl", "/alc/Alignment.owl", "/alcroiq/owl-tests.owl",
            "/alcri/sroiq-tests.owl", "/el/Disalignment.owl", "/alc/Fish.owl", "/alc/InitialOntology.owl",
            "/alc/InitialOntologyAlignment.owl", "/alc/InitialOntologyInsta.owl", "/alcri/shapes.owl",
            "/alc/InitialOntologyInstantiationAlignment.owl", "/alc/Test_hybrid.owl", "/el/apo.owl",
            "/alc/Vehicle.owl", "/alc/C50_R10_0.001_0.001_0.001_62888.owl", "/shi/SceneOntology.owl",
            "/alcriq/adolena.owl", "/alcrq/BuildingStructure.owl", "/shoin/pizza.owl", "/shiq/stuff.owl",
    })
    public void allWeakerAxiomsAreEntailedFromFile(String resourceName) throws OWLOntologyCreationException {
        var path = SroiqAxiomWeakenerTest.class.getResource(resourceName).getFile();
        var loaded = Ontology.loadOntology(path);
        try (var ontology = HERMIT_ONTOLOGIES.contains(resourceName) ? loaded.withHermit() : loaded) {
            var axiomWeakener = new AxiomWeakener(ontology);
            ontology.logicalAxioms().forEach(strongAxiom -> {
                axiomWeakener.weakerAxioms(strongAxiom).forEach(weakAxiom -> {
                    assertTrue(ontology.isEntailed(weakAxiom));
                });
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/alch/catsandnumbers.owl", "/el/bodysystem.owl", "/alc/bfo.owl", "/el/aeo.owl",
            "/el/duo.owl", "/el/a-and-b.owl", "/el/Empty.owl", "/alc/Alignment.owl", "/alcroiq/owl-tests.owl",
            "/alcri/sroiq-tests.owl", "/el/Disalignment.owl", "/alc/Fish.owl", "/alc/InitialOntology.owl",
            "/alc/InitialOntologyAlignment.owl", "/alc/InitialOntologyInsta.owl", "/alcri/shapes.owl",
            "/alc/InitialOntologyInstantiationAlignment.owl", "/alc/Test_hybrid.owl", "/el/apo.owl",
            "/alc/Vehicle.owl", "/alc/C50_R10_0.001_0.001_0.001_62888.owl", "/shi/SceneOntology.owl",
            "/alcriq/adolena.owl", "/alcrq/BuildingStructure.owl", "/shoin/pizza.owl", "/shiq/stuff.owl",
    })
    public void weakeningMaintainsConstraints(String resourceName) throws OWLOntologyCreationException {
        var path = SroiqAxiomWeakenerTest.class.getResource(resourceName).getFile();
        try (var ontology = Ontology.loadOntology(path)) {
            ontology.generateDeclarationAxioms();
            assertTrue(ontology.isOwl2Dl());
            var originalSimpleRoles = Utils.toSet(ontology.simpleRoles());
            var axiomWeakener = new AxiomWeakener(ontology, AxiomWeakener.FLAG_OWL2_SET_OPERANDS);
            ontology.logicalAxioms().forEach(strongAxiom -> {
                axiomWeakener.weakerAxioms(strongAxiom).forEach(weakAxiom -> {
                    try (var copy = ontology.clone()) {
                        copy.removeAxioms(strongAxiom);
                        copy.addAxioms(weakAxiom);
                        // Replacing an axiom with a weakening does not violate constraints.
                        assertTrue(copy.isOwl2Dl());
                        // All roles that are simple in the original ontology are simple in the modified
                        // ontology.
                        var newSimpleRoles = Utils.toSet(copy.simpleRoles());
                        assertTrue(newSimpleRoles.containsAll(originalSimpleRoles));
                    }
                });
            });
        }
    }
}
