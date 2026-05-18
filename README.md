# Ontology Repair with Power Indexes

## How to Build

The prototype is implemented in Java, and will require at least Java version 17. The Maven tool has been used for dependency management. Both Maven and a Java 17 JDK must be installed for building this project. To download and build the project, the following code can be executed. After building, the packaged JAR files will be located in `target/`. There will be two different packages, one containing only the code in the project named `ontologyutils-X.X.X.jar`, and one containing also all required dependencies named `shaded-ontologyutils-X.X.X.jar`. If you want to skip running the tests, you can use `mvn package -DskipTests`.

```bash
git clone https://github.com/josecoliveira/ontologyutils
cp ontologyutils
mvn clean compile package
```

## How to Run

This will run the `RepairWithPowerIndexes` application with the exact Shapley value strategy for selecting the bad axiom and the weaker axiom on the `leftpolicies-ok.owl` test file:

```bash
java -jar target/shaded-ontologyutils-X.X.X.jar  src/test/resources/inconsistent/leftpolicies-ok.owl --preset troquard2018-shapley-exact --verbose --normalize
```

## Original README

The original README file is located at `README-original.md` made by the original authors of the code. It contains instructions on how to run the other applications.
