# Ontology Repair with Power Indexes

This repository contains the source code for the ontology repair prototype used in the paper.

## Requirements

- Java 17
- Maven
- Python 3.10+
- `pip`

The dev container installs all of these; the only host-specific requirement is
that your Docker environment can build the base image
(`maven:3.9-eclipse-temurin-17`) natively. No cross-architecture setup, native
library or registry credentials are needed.

## Reasoner Support and Limitations

The repair algorithms are reasoner-agnostic. Applications choose a reasoner with
`--reasoner=<name>`; in code you can switch with `Ontology#withHermit()`,
`Ontology#withJFact()` and `Ontology#withOpenllet()`.

No single pure-Java reasoner currently reproduces the reference behaviour of
FaCT++ across the whole test suite; the differences are real and are summarised
here so you can pick the right tool for a given ontology. See
[`plans/reasoner-migration-report.md`](plans/reasoner-migration-report.md) for
the measurements.

| Reasoner | Origin | Strengths | Known limitations |
|---|---|---|---|
| `openllet` | Maven Central, pure Java (LGPL), v2.6.5 | Fast; the default; supports every query the repair algorithms use (`isConsistent`, `isCoherent`, `isSubClass`, `isSubRole`) | `Ontology#isEntailed` (and `isEntailed(Ontology)`) **throws `UnsupportedOperationException` for property-chain (`SubPropertyChainOf`, i.e. RIA) axioms**; datatype reasoning needs **JAXB** (`javax.xml.bind`, removed from the JDK in 11+) — add `javax.xml.bind:jaxb-api` plus a runtime; one internal `ArrayIndexOutOfBoundsException` observed on a large ontology; returns a **wrong `false` for `ObjectHasSelf` entailments that hold through a sub-property** (see below) |
| `hermit` | Maven Central, pure Java, v1.4.5.519 | Supports property chains; correct on the `ObjectHasSelf` and object-property-range cases where Openllet and JFact are wrong; good general coverage | **Cannot even be constructed** for ontologies containing a `⊤ ⊑ ⊥`-style GCI (its normalizer builds an empty `owl:ObjectUnionOf` and throws `NullPointerException: operands cannot be null or empty`); **does not terminate** on ontologies containing a GCI whose left-hand side has a qualified exact/at-least cardinality when the query asks for `≥n` (e.g. `ObjectExactCardinality(15, r, C) ⊑ D`); returns **wrong** `isSubClass`/`isEntailed` results for `owl:topObjectProperty` (and its inverse) |
| `jfact` | Maven Central, pure Java, v5.0.3 | Supports property chains; algorithms similar to FaCT++ | Returns **wrong `isEntailed`** results for several cases, e.g. object-property-range axioms strengthened to `ObjectSomeValuesFrom` on the `InitialOntology*`/`Vehicle` ontologies, `SubObjectPropertyOf(p, owl:bottomObjectProperty)`, some property chains, and `/el/aeo.owl` and `/alc/Fish.owl`, even for a freshly created reasoner |
| `fact++` (removed) | Custom native package | The reference behaviour the paper's results were produced with; passes the full suite on x86-64 | Native `libFaCTPlusPlusJNI.so` is **`linux/amd64` only** and executes instructions unavailable under Rosetta emulation → `SIGILL`; not published for OWLAPI 5 |

### Wrong `ObjectHasSelf` entailments in Openllet

The clearest reasoner bug found in this code base is that Openllet does not
propagate `ObjectHasSelf` through the property hierarchy. For an ontology that
contains `SubObjectPropertyOf(ancestorOf, knows)`, the entailment
`⊤ ⊑ ∃ancestorOf.Self` logically entails `⊤ ⊑ ∃knows.Self`, but Openllet answers
*not entailed*. HermiT and JFact both answer correctly. This is visible on
`/alcri/sroiq-tests.owl` and `/alcroiq/owl-tests.owl`.

### Automatic reasoner fallback

`Ontology` does not rely on a single reasoner for every query. Each reasoner
definition is paired with a *secondary* reasoner of a different implementation
(HermiT ↔ Openllet). A query is replayed on the secondary reasoner when the
primary one

* **cannot be constructed** for the ontology, or
* throws `UnsupportedOperationException` (unsupported axiom/query, e.g. property
  chains with Openllet), `IndexOutOfBoundsException`, `NullPointerException` or
  `ReasonerInternalException` (reasoner-internal failures).

This is what keeps the property-chain queries answerable under the Openllet
default, and what lets the few tests that need HermiT still work on ontologies
that HermiT cannot classify at all (the `⊤ ⊑ ⊥` case above: Openllet answers
those, and it is correct there).

The fallback cannot repair a reasoner that silently returns a **wrong answer** —
no exception is raised in that case, so the query is not retried. Where that
matters for a specific entailment, the test selects the reasoner that is known
to be correct explicitly (for example `Ontology#withHermit()`), with a comment
pointing back to this section.

### Guidance

* Ontologies **without** property chains and without `owl:topObjectProperty` →
  Openllet (most reliable and fastest).
* Ontologies **with** property chains (RIAs) → HermiT or JFact: Openllet cannot
  answer entailment queries about chains (the fallback handles this for you).
* Ontologies with **qualified cardinality GCIs** queried with `≥n` → Openllet or
  JFact: HermiT may not terminate.
* Ontologies where `ObjectHasSelf` entailments must be decided → HermiT or JFact.
* Avoid asking HermiT about entailment involving `owl:topObjectProperty`.
* Avoid JFact for entailments over object-property ranges.

Supporting a *single* default reasoner that passes everything requires either the
legacy FaCT++ (x86-64 only) or the fallback layer described above; see the report.

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
