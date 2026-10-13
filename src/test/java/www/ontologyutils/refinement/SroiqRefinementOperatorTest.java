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
public class SroiqRefinementOperatorTest {
    private static final String ONTOLOGY_IRI = "http://www.semanticweb.org/roland/ontologies/2023/3/untitled/";
    /**
     * Ontologies for which Openllet currently returns a wrong (false) entailment
     * answer. HermiT is used for those (see README "Reasoner Support and
     * Limitations").
     */
    private static final Set<String> HERMIT_ONTOLOGIES = Set.of("/alcri/sroiq-tests.owl");

    private Ontology ontology;
    private Covers covers;
    private RefinementOperator generalization;
    private RefinementOperator specialization;

    @BeforeEach
    public void setup() {
        var path = RoleCoverTest.class.getResource("/alcri/sroiq-tests.owl").getFile();
        ontology = Ontology.loadOntology(path);
        var subConcepts = Utils.toSet(ontology.subConcepts());
        var subRoles = Utils.toSet(ontology.subRoles());
        var simpleRoles = Utils.toSet(ontology.simpleRoles());
        covers = new Covers(ontology, subConcepts, subRoles, simpleRoles, 0);
        var upCover = covers.upCover().cached();
        var downCover = covers.downCover().cached();
        generalization = new RefinementOperator(upCover, downCover);
        specialization = new RefinementOperator(downCover, upCover);
    }

    @AfterEach
    public void teardown() {
        ontology.close();
    }

    private static Stream<Arguments> expectedGeneralization() {
        var df = Ontology.getDefaultDataFactory();
        return Stream.of(
                Arguments.of(
                        Set.of(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                Arguments.of(
                        Set.of(
                                df.getOWLClass(ONTOLOGY_IRI, "Mother").getObjectComplementOf(),
                                df.getOWLClass(ONTOLOGY_IRI, "Father").getObjectComplementOf(),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf(),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")).getObjectComplementOf(),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Person"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLThing()),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLObjectUnionOf(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Person"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLThing()),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                        df.getOWLObjectIntersectionOf(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLObjectAllValuesFrom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"))),
                        df.getOWLObjectSomeValuesFrom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(3,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLThing()),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLObjectMinCardinality(4,
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(5,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLObjectMaxCardinality(4,
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLThing()),
                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))));
    }

    @ParameterizedTest
    @MethodSource("expectedGeneralization")
    public void generalize(Set<OWLClassExpression> expected, OWLClassExpression concept) {
        assertEquals(expected, Utils.toSet(generalization.refine(concept)));
    }

    private static Stream<Arguments> expectedSpecialization() {
        var df = Ontology.getDefaultDataFactory();
        return Stream.of(
                Arguments.of(
                        Set.of(
                                df.getOWLClass(ONTOLOGY_IRI, "Mother"),
                                df.getOWLClass(ONTOLOGY_IRI, "Father"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                Arguments.of(
                        Set.of(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf(),
                                df.getOWLClass(ONTOLOGY_IRI, "Person").getObjectComplementOf(),
                                df.getOWLNothing()),
                        df.getOWLClass(ONTOLOGY_IRI, "Parent").getObjectComplementOf()),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Mother"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Father"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectUnionOf(
                                        df.getOWLObjectSomeValuesFrom(
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectUnionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                        df.getOWLObjectUnionOf(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Mother"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Father"), df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLObjectSomeValuesFrom(
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectIntersectionOf(
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLClass(ONTOLOGY_IRI, "Mother"),
                                df.getOWLClass(ONTOLOGY_IRI, "Father"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                        df.getOWLObjectIntersectionOf(
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"), df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Mother")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Father")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectAllValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectSomeValuesFrom(
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                                df.getOWLNothing()),
                        df.getOWLObjectAllValuesFrom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Mother")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Father")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLObjectSomeValuesFrom(
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                        df.getOWLObjectSomeValuesFrom(
                                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                                df.getOWLNothing()),
                        df.getOWLObjectSomeValuesFrom(
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Parent"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(5,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "fatherOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "motherOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMinCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Parent")),
                                df.getOWLNothing()),
                        df.getOWLObjectMinCardinality(4,
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(3,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "parentOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf").getInverseProperty(),
                                        df.getOWLClass(ONTOLOGY_IRI, "Person")),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))),
                                df.getOWLObjectMaxCardinality(4,
                                        df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                        df.getOWLThing()),
                                df.getOWLNothing()),
                        df.getOWLObjectMaxCardinality(4,
                                df.getOWLObjectProperty(ONTOLOGY_IRI, "childOf"),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"))),
                Arguments.of(
                        Set.of(
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows")),
                                df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "ancestorOf")),
                                df.getOWLClass(ONTOLOGY_IRI, "Person"),
                                df.getOWLClass(ONTOLOGY_IRI, "Grandparent"),
                                df.getOWLThing()),
                        df.getOWLObjectHasSelf(df.getOWLObjectProperty(ONTOLOGY_IRI, "knows"))));
    }

    @ParameterizedTest
    @MethodSource("expectedSpecialization")
    public void specialize(Set<OWLClassExpression> expected, OWLClassExpression concept) {
        assertEquals(expected, Utils.toSet(specialization.refine(concept)));
    }

    @Test
    public void allGeneralize() {
        ontology.subConcepts().map(generalization::refine).forEach(Stream::count);
    }

    @Test
    public void allSpecialize() {
        ontology.subConcepts().map(specialization::refine).forEach(Stream::count);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/alch/catsandnumbers.owl", "/el/bodysystem.owl", "/alc/bfo.owl", "/el/apo.owl", "/el/aeo.owl",
            "/el/duo.owl", "/el/a-and-b.owl", "/el/Empty.owl", "/alc/Alignment.owl", "/alcroiq/owl-tests.owl",
            "/alcri/sroiq-tests.owl", "/el/Disalignment.owl", "/alc/Fish.owl", "/alc/InitialOntology.owl",
            "/alc/InitialOntologyAlignment.owl", "/alc/InitialOntologyInsta.owl",
            "/alc/InitialOntologyInstantiationAlignment.owl", "/alc/Test_hybrid.owl",
            "/alc/Vehicle.owl", "/alc/C50_R10_0.001_0.001_0.001_62888.owl", "/shoin/pizza.owl"
    })
    public void generalizationFromFile(String resourceName) throws OWLOntologyCreationException {
        var path = SroiqAxiomWeakenerTest.class.getResource(resourceName).getFile();
        var loaded = Ontology.loadOntology(path);
        try (var ontology = HERMIT_ONTOLOGIES.contains(resourceName) ? loaded.withHermit() : loaded) {
            var subConcepts = Utils.toSet(ontology.subConcepts());
            var subRoles = Utils.toSet(ontology.subRoles());
            var simpleRoles = Utils.toSet(ontology.simpleRoles());
            covers = new Covers(ontology, subConcepts, subRoles, simpleRoles, 0);
            var upCover = covers.upCover().cached();
            var downCover = covers.downCover().cached();
            generalization = new RefinementOperator(upCover, downCover);
            ontology.subConcepts().forEach(special -> {
                for (var general : Utils.toList(generalization.refine(special))) {
                    assertTrue(ontology.isSubClass(special, general));
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/alch/catsandnumbers.owl", "/el/bodysystem.owl", "/alc/bfo.owl", "/el/apo.owl", "/el/aeo.owl",
            "/el/duo.owl", "/el/a-and-b.owl", "/el/Empty.owl", "/alc/Alignment.owl", "/alcroiq/owl-tests.owl",
            "/alcri/sroiq-tests.owl", "/el/Disalignment.owl", "/alc/Fish.owl", "/alc/InitialOntology.owl",
            "/alc/InitialOntologyAlignment.owl", "/alc/InitialOntologyInsta.owl",
            "/alc/InitialOntologyInstantiationAlignment.owl", "/alc/Test_hybrid.owl",
            "/alc/Vehicle.owl", "/alc/C50_R10_0.001_0.001_0.001_62888.owl", "/shoin/pizza.owl"
    })
    public void specializationFromFile(String resourceName) throws OWLOntologyCreationException {
        var path = SroiqAxiomWeakenerTest.class.getResource(resourceName).getFile();
        try (var ontology = Ontology.loadOntology(path)) {
            var subConcepts = Utils.toSet(ontology.subConcepts());
            var subRoles = Utils.toSet(ontology.subRoles());
            var simpleRoles = Utils.toSet(ontology.simpleRoles());
            covers = new Covers(ontology, subConcepts, subRoles, simpleRoles, 0);
            var upCover = covers.upCover().cached();
            var downCover = covers.downCover().cached();
            specialization = new RefinementOperator(downCover, upCover);
            ontology.subConcepts().forEach(general -> {
                for (var special : Utils.toList(specialization.refine(general))) {
                    assertTrue(ontology.isSubClass(special, general));
                }
            });
        }
    }
}
