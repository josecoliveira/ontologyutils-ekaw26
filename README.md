# Ontology Repair with Power Indexes

## How to Build

The prototype is implemented in Java, and will require at least Java version 17. The Maven tool has been used for dependency management. Both Maven and a Java 17 JDK must be installed for building this project. To download and build the project, the following code can be executed. After building, the packaged JAR files will be located in `target/`. There will be two different packages, one containing only the code in the project named `ontologyutils-X.X.X.jar`, and one containing also all required dependencies named `shaded-ontologyutils-X.X.X.jar`. If you want to skip running the tests, you can use `mvn package -DskipTests`.

```bash
git clone https://github.com/josecoliveira/ontologyutils
cp ontologyutils
mvn clean compile package
```

This checkout expects the `Fact++` jar to be available locally at `lib/factplusplus-1.7.0.3.jar`. After downloading that jar, place it in `lib/` and run Maven normally. If you prefer to install it into your local Maven repository instead, use:

```bash
mvn install:install-file -Dfile=/path/to/factplusplus-1.7.0.3.jar -DgroupId=ontologyutils -DartifactId=factplusplus -Dversion=1.7.0.3 -Dpackaging=jar
```

## How to Run

This will run the `RepairWithPowerIndexes` application with the exact Shapley value strategy for selecting the bad axiom and the weaker axiom on the `leftpolicies-ok.owl` test file:

```bash
java -jar target/shaded-ontologyutils-X.X.X.jar  src/test/resources/inconsistent/leftpolicies-ok.owl --preset troquard2018-shapley-exact --verbose --normalize
```

## Original README

The original README file is located at `README-original.md` made by the original authors of the code. It contains instructions on how to run the other applications.
 
## Contributing / Developer setup

If you are contributing or developing locally, follow these steps to get the project running on your machine:

- Prerequisites: Java JDK (17+), Maven, Python 3.

The repository now includes the `Fact++` JAR at `lib/factplusplus-1.7.0.3.jar` so contributors do not need to locate this hard-to-find artifact manually. The `lib/` directory is normally ignored, but `lib/factplusplus-1.7.0.3.jar` is intentionally tracked and available in this checkout.

If you prefer to install the artifact into your local Maven repository, you may still do so (optional):

```bash
mvn install:install-file \
	-Dfile=lib/factplusplus-1.7.0.3.jar \
	-DgroupId=ontologyutils \
	-DartifactId=factplusplus \
	-Dversion=1.7.0.3 \
	-Dpackaging=jar
```

After installing the artifact (or using the committed copy in `lib/`), the project `pom.xml` is configured to use the dependency from your local repository. Build and run a single Java trial directly with Maven to verify everything is on the classpath:

```bash
mvn exec:java -Dexec.mainClass=www.ontologyutils.apps.SingleTrialExperiment \
	-Dexec.args="--ontology src/test/resources/ekaw26/cleanup/bctt.owl --seed 28 --out analysis/data/shapley-shapley/iic-temp-28.csv --run-id LOCAL"
```

To run the provided driver script which executes many trials and collects CSVs (recommended):

```bash
# from project root
python3 analysis/run_trials.py src/test/resources/ekaw26/cleanup/bctt.owl
```

Notes:
- If you see warnings about restricted native access (JDK 19+), you can add the JVM flag to enable native access when running Maven or Java:

```bash
MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn exec:java ...
```

- The committed `lib/factplusplus-1.7.0.3.jar` is included to make contributor setup easier; if you prefer not to keep the JAR in your fork, you can remove it and instead run the `mvn install:install-file` command above.

If you want, I can run the full `python3 analysis/run_trials.py ...` now to verify an end-to-end successful run.
