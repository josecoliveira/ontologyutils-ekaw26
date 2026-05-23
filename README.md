# Ontology Repair with Power Indexes

This repository contains the source code for the ontology repair prototype used in the paper.

## Requirements

- Java 17
- Maven

The build expects the Fact++ dependency at `lib/factplusplus-1.7.0.3.jar`. The jar is committed in this checkout, but you can also install it into your local Maven repository:

```bash
mvn install:install-file -Dfile=lib/factplusplus-1.7.0.3.jar -DgroupId=ontologyutils -DartifactId=factplusplus -Dversion=1.7.0.3 -Dpackaging=jar
```

## Build

Build the project with Maven from the repository root:

```bash
mvn clean package
```

The shaded executable jar is written to `target/shaded-ontologyutils-0.1.0.jar`.

If you want to skip the test suite:

```bash
mvn clean package -DskipTests
```

## Run Repair With Power Indexes

Run the repair application directly with the shaded jar on the classpath:

```bash
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWithPowerIndexes src/test/resources/ekaw26/inconsistent/bctt.owl --preset troquard2018 --power-index-shapley-exact --verbose --normalize
```

The approximate variant is also available:

```bash
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWithPowerIndexes src/test/resources/ekaw26/inconsistent/elig.owl --preset troquard2018 --power-index-shapley-approximate --verbose --normalize
```

You can direct the repaired ontology output to a file using the `-o` or `--output` option.

Example: write the repaired ontology to `results/repair.owl`:

```bash
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWithPowerIndexes src/test/resources/ekaw26/inconsistent/elig.owl --preset troquard2018 --power-index-shapley-approximate --normalize --output results/repair.owl
```

The inconsistent ontologies used in the paper are in `src/test/resources/ekaw26/inconsistent/`.

## Original Project

This repository is a fork of the original Ontology Utils project. The preserved upstream README is available in [README-original.md](README-original.md).

Original authors: Nicolas Troquard, Roberto Confalonieri, Pietro Galliani, and Roland Bernard.
