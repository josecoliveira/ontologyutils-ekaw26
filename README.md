# Ontology Repair with Power Indexes

This repository contains the source code for the ontology repair prototype used in the paper.

## Requirements

- Java 17
- Maven
- **GitHub authentication** — The project depends on Maven artifacts hosted on
  GitHub Packages (`maven.pkg.github.com/rolandbernard/*`). You **must**
  provide credentials with at least `read:packages` scope so that Maven can
  resolve these dependencies.

  The recommended way is to set the following environment variables **before**
  starting VS Code or running the dev container:

  ```bash
  export GITHUB_USER=<your-github-username>
  export GITHUB_TOKEN=<your-github-personal-access-token>
  ```

  > **Windows users**: Environment variables set inside a terminal session may
  > not be visible to VS Code when launched from the Start Menu. If the dev
  > container does not pick up your variables, create a
  > `.devcontainer/devcontainer.env` file from the provided template:
  > ```bash
  > cp .devcontainer/devcontainer.env.example .devcontainer/devcontainer.env
  > ```
  > Then edit the file with your credentials. The file is in `.gitignore` so it
  > will not be committed.

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
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWithPowerIndexes src/test/resources/ekaw26/inconsistent/taxrank.owl --preset troquard2018 --power-index-shapley-approximate --verbose --normalize
```

You can direct the repaired ontology output to a file using the `-o` or `--output` option.

Example: write the repaired ontology to `results/repair.owl`:

```bash
java -cp target/shaded-ontologyutils-0.1.0.jar www.ontologyutils.apps.RepairWithPowerIndexes src/test/resources/ekaw26/inconsistent/taxrank.owl --preset troquard2018 --power-index-shapley-approximate --normalize --output results/repair.owl
```

The inconsistent ontologies used in the paper are in `src/test/resources/ekaw26/inconsistent/`.

## Original Project

This repository is a fork of the original Ontology Utils project. The preserved upstream README is available in [README-original.md](README-original.md).

Original authors: Nicolas Troquard, Roberto Confalonieri, Pietro Galliani, and Roland Bernard.
