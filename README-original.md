Ontology Utils
==============

A suite of utility functions and applications for engineering OWL ontologies.

This is a heavily modified (nearly all the original code has been removed or replaced) fork of the code written by Nicolas Troquard. The original code can be found [here](https://bitbucket.org/troquard/ontologyutils/). The code has been extended to handle weakening of ontologies using $\mathcal{SROIQ}$, including weakening of some role inclusion axioms.

## Getting Started

### How to Build

The prototype is implemented in Java, and will require at least Java version 17. The Maven tool has been used for dependency management. Both Maven and a Java 17 JDK must be installed for building this project. To download and build the project, the following code can be executed. After building, the packaged JAR files will be located in `target/`. There will be two different packages, one containing only the code in the project named `ontologyutils-X.X.X.jar`, and one containing also all required dependencies named `shaded-ontologyutils-X.X.X.jar`. If you want to skip running the tests, you can use `mvn package -DskipTests`.

```
git clone https://github.com/rolandbernard/ontologyutils
cd ontologyutils
mvn clean compile package
```
If you would rather use the pre-packaged jar files, those are also available in the GitHub repository at https://github.com/rolandbernard/ontologyutils/releases. Note that the releases may be out of date with the master branch of the repository.

### Use as a Library

Since the project is implemented as a Maven project it is possible to use it as a dependency for another Maven project. To add the library as a dependency, if you have build the project yourself and run `mvn install`, it is sufficient to add the following to your `pom.xml` file. 

```
<dependency>
  <groupId>ontologyutils</groupId>
  <artifactId>ontologyutils</artifactId>
  <version>0.1.0</version>
</dependency>
```

There is also the possibility to let Maven handle the installation from the repository by adding the following configuration to the `pom.xml` file. This will add the GitHub Maven repository and download the dependency from the ones available at https://github.com/rolandbernard?tab=packages&repo_name=ontologyutils.

```
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/rolandbernard/*</url>
    </repository>
</repositories>
```

Javadoc comments are included for most classes and methods, so it should be rather easy to understand the structure of the project.

### How to Run Applications

To simply use the applications that were also used for the evaluation in this thesis, use the JAR file produced during building. The packaged output at `target/shaded-ontologyutils-X.X.X.jar` will include all the necessary dependencies. From there it is enough to run the JAR file using a command if the form `java -cp <jar-file> www.ontologyutils.apps.<app-class> [argument ...]`, where `<jar-file>` should be replaced with the path to the JAR package and `<app-class>` should be replaced with one of the following.

* `BenchCache` Is a utility application used for the evaluation of the cache effectiveness. It is not intended to be used by an end user.
* `Benchmark` Is another utility for benchmarking the performance of the axiom weakening implementation.
* `CheckConsistency` Is an application to test the consistency of an ontology, possibly using multiple different reasoner implementations.
* `ClassifyOntology` Can be used to determine whether an ontology fits into a certain OWL 2 profile. Further, it also shows how expressive the Description Logic features used are.
* `CleanupOntology` This application was used for the evaluation to apply normalization and remove axioms violating the OWL 2 DL profile.
* `EvaluateRepairs` Can be used to compute the size of the inferred class hierarchy and the IIC between different ontologies.
* `MakeInconsistent` Can be used to make a consistent ontology inconsistent by adding stronger versions of the axioms until they cause inconsistency.
* `RepairMcs` Can be used to repair an ontology using a randomly sampled maximal consistent subset.
* `RepairRemoval` Enables the repair of an ontology using the repair algorithm using removal.
* `RepairWeakening` Enables the repair of an ontology using the repair algorithm using axiom weakening.
* `ShowOntology` Is a simple application that prints all the axioms in the ontology.

To get specific information about how to use an application, simply use the `--help` argument. For example the following is the help output for the `RepairWeakening` application.

```
Usage: www.ontologyutils.apps.RepairWeakening [options] <file>
Options:
  -h --help                print this help information and quit
  -o --output=<file>       the file to write the result to
  -n --normalize           normalize the ontology before repair
  --normalize-nnf          normalize the ontology to NNF before repair
  -R --no-repair           no not perform repair
  -v --verbose             print more information
  -V --extra-verbose       print even more information
  --limit=<integer>        number of repairs to generate
  --no-limit=<integer>     only stop once all repairs have been generated
  --reasoner={fact++|hermit|jfact|openllet}
                           the reasoner to use
  --coherence              make the ontology coherent
  --fast                   use fast methods for selection
  --ref-ontology={any|intersect|intersect-of-some|largest|random|random-of-some}
                           method for reference ontology selection
  --bad-axiom={largest-mcs|least-mcs|most-mus|one-mcs|one-mus|random|some-mcs|some-mus}
                           method for bad axiom selection
  --strict-nnf             accept and produce only NNF axioms
  --strict-alc             accept and produce only ALC axioms
  --strict-sroiq           accept and produce only SROIQ axioms
  --strict-simple-roles    use only simple roles in upward and downward covers
  --uncached               do not use any caches for the covers
  --basic-cache            use only a basic cache
  --strict-owl2            do not produce intersection and union with a single operand
  --simple-ria-weakening   do not use the more advanced RIA weakening
  --no-role-refinement     do not refine roles in any context
  --enhance-ref            keep the reference ontology as static axioms in the output
  --preset={bernard2023|confalonieri2020|troquard2018}
                           configuration approximating description in papers
  <file>                   the file containing the original ontology
```

To repair the ontology in the file `inconsistent.owl` using the axiom weakening based repair algorithm, and write the repaired ontology to `consistent.owl`, for example, using the configuration used for the evaluation in this thesis, one would execute the following command.

```
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWeakening inconsistent.owl -o consistent.owl
```

### How to Test

To run all the tests for this software, execute the Maven command `mvn test`. This will automatically run all the defined test using JUnit. Note that by default, all tests will be run also as part of the `package` target before the jar is generated.

### Test Ontologies

The directory `src/test/resources/www/ontologyutils/` contains test OWL ontologies.

## Some Functions

### Computing Maximally Consistent Sets

The method `MaximalConsistentSubsets#maximalConsistentSubsets` takes a set of axioms in parameter and returns the set of maximally consistent subsets.
It is an implementation of the algorithm presented in Robert Malouf's "Maximal Consistent Subsets", Computational Linguistics, vol 33(2), p.153-160, 2007.`

Additionally, the methods `MinimalSubsets#getMinimalSubset` and `MinimalSubsets#getMinimalSubsets` can be used to compute the minimal subsets satisfying some monotone predicate. This can be used to find a minimal correction subset, the complement of which is a maximally consistent subset.
`MinimalSubsets#getMinimalSubset` is based on the algorithms presented in Marques-Silva, Joao, Mikoláš Janota, and Anton Belov. "Minimal sets over monotone predicates in boolean formulae." Computer Aided Verification: 25th International Conference, CAV 2013, Saint Petersburg, Russia, July 13-19, 2013. Proceedings 25. Springer Berlin Heidelberg, 2013.
`MinimalSubsets#getMinimalSubsets` is a modified version of the MergeXplain algorithm proposed in Shchekotykhin, Kostyantyn, Dietmar Jannach, and Thomas Schmitz. "MergeXplain: Fast computation of multiple conflicts for diagnosis." Twenty-Fourth International Joint Conference on Artificial Intelligence. 2015.

### Refinement

The class `RefinementOperator` contains an implementation of the generic refinement operator presented in Nicolas Troquard, Roberto Confalonieri, Pietro Galliani, Rafael Peñaloza, Daniele Porello, Oliver Kutz: "Repairing Ontologies via Axiom Weakening", in Thirty-Second AAAI Conference on Artificial Intelligence (AAAI 2018). It can be instantiated as generalisation operator (with a reference ontology, `way` as UpCover, `back` as DownCover) or as a specialisation operator (with a reference ontology, `way` as DownCover, `back` as UpCover). The method `RefinementOperator#refine` return the concepts that immediately refine a concept via the refinement operator.

Further, the implementation has been extended to the refinement operator described in Confalonieri, R., Galliani, P., Kutz, O., Porello, D., Righetti, G., & Toquard, N. (2020). Towards even more irresistible axiom weakening. This enables refinement of roles and $\mathcal{SROIQ}$ concepts.

### Axiom Weakening

The class `AxiomWeakener` implements the axiom weakening operations presented in Troquard et al. "Repairing Ontologies via Axiom Weakening" (AAAI 2018). `AxiomWeakener:weakerAxioms` is used for getting the weaker axioms.

The axiom weakener has also been extended to operate on following the procedure in Confalonieri, R., Galliani, P., Kutz, O., Porello, D., Righetti, G., & Toquard, N. (2020). Towards even more irresistible axiom weakening. This enables refinement of roles and $\mathcal{SROIQ}$ concepts. Also, further weakening has been implemented for disjoint role assertions and role inclusion axioms.


## Some Apps

### App Example: Show Ontology

The app `AppShowOntology` allows one to display a "human readable" form of an OWL ontology.

`AppShowOntology src/test/resources/www/ontologyutils/bodysystem.owl` prints:

```
Ontology loaded.
Declaration(Class(<http://who.int/bodysystem.owl#AutonomicNervousSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#HaematopoieticSystem>))
[Truncated for README: 20 more Declaration lines]
Declaration(Class(<http://who.int/bodysystem.owl#AuditorySystem>))
Declaration(Class(<http://who.int/bodysystem.owl#VisualSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#GenitourinarySystem>))
Declaration(Class(<http://who.int/bodysystem.owl#FemaleGenitalSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#MaleGenitalSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#SceletalSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#MuscularSystem>))
Declaration(Class(<http://who.int/bodysystem.owl#NervousSystem>))
SubClassOf(<http://who.int/bodysystem.owl#AuditorySystem> <http://who.int/bodysystem.owl#BodySystem>)
[Truncated for README: 20 more SubClassOf lines]
SubClassOf(<http://who.int/bodysystem.owl#MentalSystem> <http://who.int/bodysystem.owl#BodySystem>)
SubClassOf(<http://who.int/bodysystem.owl#NutritionalSystem> <http://who.int/bodysystem.owl#BodySystem>)
SubClassOf(<http://who.int/bodysystem.owl#HaemolymphoidSystem> <http://who.int/bodysystem.owl#BodySystem>)
SubClassOf(<http://who.int/bodysystem.owl#CirculatorySystem> <http://who.int/bodysystem.owl#BodySystem>)
SubClassOf(<http://who.int/bodysystem.owl#AutonomicNervousSystem> <http://who.int/bodysystem.owl#NervousSystem>)
SubClassOf(<http://who.int/bodysystem.owl#DigestiveSystem> <http://who.int/bodysystem.owl#BodySystem>)
SubClassOf(<http://who.int/bodysystem.owl#MuscularSystem> <http://who.int/bodysystem.owl#MusculoskeletalSystem>)
SubClassOf(<http://who.int/bodysystem.owl#RespiratorySystem> <http://who.int/bodysystem.owl#BodySystem>)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#MotoricSystem> "Motoric System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#BodySystem> "Body System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#ParasympatheticNervousSystem> "Parasympathetic Nervous System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#MaleGenitalSystem> "Male Genital System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#SceletalSystem> "Sceletal System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#PeripheralNervousSystem> "Peripheral Nervous System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#MuscularSystem> "Muscular System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#AuditorySystem> "Auditory System"^^xsd:string)
AnnotationAssertion(rdfs:label <http://who.int/bodysystem.owl#HaematopoieticSystem> "Haematopoietic System"^^xsd:string)
[Truncated for README: 20 more AnnotationAssertion lines]
```

### App Example: AppAutomatedRepairWeakening

This is an app showcasing `OntologyRepairWeakening` which is an implementation of `OntologyRepair` following closely (although not strictly) the axiom weakening approach described in Nicolas Troquard, Roberto Confalonieri, Pietro Galliani, Rafael Peñaloza, Daniele Porello, Oliver Kutz: "Repairing Ontologies via Axiom Weakening", AAAI 2018. See also `AppAutomatedRepairRandomMCS` and `AppInteractiveRepair`.

Ran with the parameter `src/test/resources/www/ontologyutils/inconsistent-leftpolicies-small.owl`, it gives:

```
Loaded... Ontology(OntologyID(OntologyIRI(<agenda:eu>) VersionIRI(<null>))) [Axioms: 10 Logical Axioms: 6] First 20 axioms: {SubClassOf(<agenda:eu#RaiseWelfare> <agenda:eu#LeftPolicy>) SubClassOf(<agenda:eu#RaiseWages> <agenda:eu#LeftPolicy>) Declaration(Class(<agenda:eu#RaiseWages>)) Declaration(NamedIndividual(<agenda:eu#Sweden>)) Declaration(Class(<agenda:eu#RaiseWelfare>)) Declaration(Class(<agenda:eu#LeftPolicy>)) ClassAssertion(<agenda:eu#RaiseWelfare> <agenda:eu#Sweden>) ClassAssertion(<agenda:eu#LeftPolicy> <agenda:eu#Sweden>) ClassAssertion(<agenda:eu#RaiseWages> <agenda:eu#Sweden>) DisjointUnion(<agenda:eu#LeftPolicy> <agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare> ) `
Converted ontology: 9 logical axioms:
ClassAssertion(<agenda:eu#RaiseWages> <agenda:eu#Sweden>)
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) <agenda:eu#RaiseWelfare> ObjectComplementOf(<agenda:eu#RaiseWages>))
SubClassOf(Annotation(<origin> <SubClassOf(agenda:eu#RaiseWages agenda:eu#LeftPolicy)>) <agenda:eu#RaiseWages> <agenda:eu#LeftPolicy>)
ClassAssertion(<agenda:eu#RaiseWelfare> <agenda:eu#Sweden>)
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) <agenda:eu#RaiseWages> ObjectComplementOf(<agenda:eu#RaiseWelfare>))
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) ObjectUnionOf(<agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare>) <agenda:eu#LeftPolicy>)
SubClassOf(Annotation(<origin> <SubClassOf(agenda:eu#RaiseWelfare agenda:eu#LeftPolicy)>) <agenda:eu#RaiseWelfare> <agenda:eu#LeftPolicy>)
ClassAssertion(<agenda:eu#LeftPolicy> <agenda:eu#Sweden>)
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) <agenda:eu#LeftPolicy> ObjectUnionOf(<agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare>))
Repairing... 
Repaired ontology.
SubClassOf(<agenda:eu#RaiseWelfare> ObjectComplementOf(<agenda:eu#RaiseWages>))
SubClassOf(Annotation(<origin> <SubClassOf(agenda:eu#RaiseWages agenda:eu#LeftPolicy)>) <agenda:eu#RaiseWages> <agenda:eu#LeftPolicy>)
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) ObjectUnionOf(<agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare>) <agenda:eu#LeftPolicy>)
SubClassOf(Annotation(<origin> <SubClassOf(agenda:eu#RaiseWelfare agenda:eu#LeftPolicy)>) <agenda:eu#RaiseWelfare> <agenda:eu#LeftPolicy>)
SubClassOf(Annotation(<origin> <DisjointUnion(agenda:eu#LeftPolicy agenda:eu#RaiseWages agenda:eu#RaiseWelfare )>) <agenda:eu#LeftPolicy> ObjectUnionOf(<agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare>))
SubClassOf(<agenda:eu#RaiseWages> ObjectComplementOf(<agenda:eu#RaiseWelfare>))
ClassAssertion(<agenda:eu#RaiseWages> <agenda:eu#Sweden>)
ClassAssertion(ObjectUnionOf(<agenda:eu#RaiseWages> <agenda:eu#RaiseWelfare>) <agenda:eu#Sweden>)
ClassAssertion(<agenda:eu#LeftPolicy> <agenda:eu#Sweden>)
Declaration(Class(<agenda:eu#LeftPolicy>))
Declaration(Class(<agenda:eu#RaiseWelfare>))
Declaration(Class(<agenda:eu#RaiseWages>))
Declaration(NamedIndividual(<agenda:eu#Sweden>))
Done.
```

### App Example: AppMakeInconsistent

This app uses axiom strengthening (`AxiomStrengthener`) to obtain an inconsistent ontology from a consistent one. The main intended application is to build inconsistent ontologies from real ones, used for testing repairing methods.

A first argument must be given, corresponding to an OWL ontology file path. A second argument can be given, to indicate the minimal number of strengthening iterations must be done. A third argument can be given, to indicate the minimal number of iterations needed that must be done after reaching inconsistency.

The resulting ontology is saved in a file `<filename>-made-inconsistent.owl`, when the filename of the original ontology is `<filename>.owl`.


## Acknowledgments

Have contributed to the original project: Nicolas Troquard (UNIBZ), Roberto Confalonieri (UNIBZ), Pietro Galliani (UNIBZ).

