# Ontology Repair with Power Indexes

This repository contains the source code for the ontology repair prototype used in the paper.

## Requirements

### Architecture Requirements

> **Note:** the custom `factplusplus` dependency has been **removed**. The
> prototype now uses a standard, pure-Java OWL reasoner by default, and the
> `--reasoner` option selects between `hermit`, `jfact` and `openllet`. For what
> each reasoner can and cannot do see
> [Reasoner Support and Limitations](#reasoner-support-and-limitations).
>The dev container is still built for `linux/amd64`, so the Rosetta/QEMU notes
> below continue to apply to that emulation; the parts that describe the
> `factplusplus` native library are kept for reference.

| Host Platform | Dev Container Support | Required Setup |
|---|---|---|
| **Linux x86_64 / amd64** | ✅ Native | No special setup |
| **macOS Apple Silicon (M1–M4)** | ✅ Via Rosetta 2 emulation | Docker Desktop ≥ 4.19 with Rosetta enabled |
| **Linux ARM64 (e.g., Raspberry Pi)** | ⚠️ Via QEMU binfmt | Install `qemu-user-static` + `binfmt-support` |

#### Enabling Rosetta 2 on Apple Silicon

1. Install [Docker Desktop for Mac](https://docs.docker.com/desktop/setup/install/mac-install/) (version 4.19 or later).
2. Open Docker Desktop → **Settings** (gear icon) → **General**.
3. Check **"Use Rosetta for x86/amd64 emulation on Apple Silicon"**.
4. Apply & restart Docker Desktop.

> **Verify Rosetta is working:**
> ```bash
> docker run --rm --platform linux/amd64 alpine uname -m
> # Expected output: x86_64
> ```

#### Setting up the cross-architecture buildx driver

The [`Dockerfile`](.devcontainer/Dockerfile) uses `FROM --platform=linux/amd64` to build an amd64 container image. On Apple Silicon (ARM64), this requires a **`docker-container`** buildx driver that supports cross-platform image loading.

On **Apple Silicon** and **Linux ARM64**, set up the driver once:

```bash
docker buildx create --name crossarch --driver docker-container --use --bootstrap
```

This creates a BuildKit container that can build amd64 images via QEMU emulation and load them into the local Docker image store.

> **Note:** On native `linux/amd64` hosts, the default `docker` buildx driver works fine — no special setup needed.

#### Verifying Cross-Architecture Setup

The dev container's [`post-create.sh`](.devcontainer/post-create.sh) script includes a native library verification step that runs during container creation. You can also manually check:

```bash
# Check container platform
uname -m
# Should show: x86_64 (even on Apple Silicon, because of Rosetta emulation)

# Check JVM can load native libraries
java -XX:+PrintNativeLibrary -version 2>&1 | head -5
```

### GitHub Authentication

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

## Troubleshooting

### `UnsatisfiedLinkError`: Native library `libFaCTPlusPlus.so` cannot be loaded

This error occurs when the `factplusplus` native library cannot be loaded by the JVM. The JAR bundles native code compiled for **`linux/amd64`** only.

**On Apple Silicon (M1/M2/M3/M4):**
1. Ensure Docker Desktop ≥ 4.19 is installed and **Rosetta 2** is enabled.
2. Verify that the dev container is running under `--platform=linux/amd64` (check [`devcontainer.json`](.devcontainer/devcontainer.json)).
3. Run `uname -m` inside the container — it should print `x86_64`. If it prints `aarch64`, the platform override is not being applied.
4. Confirm Rosetta is working at the Docker level:
   ```bash
   docker run --rm --platform linux/amd64 alpine uname -m
   # Expected: x86_64
   ```

**On Linux ARM64 (e.g., Raspberry Pi):**
1. Install QEMU binfmt support:
   ```bash
   docker run --privileged --rm tonistiigi/binfmt --install all
   ```
2. Rebuild the dev container.

### Maven dependency resolution failures

```
Could not resolve dependency: ... factplusplus:jar:1.7.0.3 was not found
```

The `factplusplus` dependency is hosted on **GitHub Packages** and requires authentication. Ensure:

1. You have a [GitHub Personal Access Token (classic)](https://github.com/settings/tokens) with at least `read:packages` scope.
2. The environment variables `GITHUB_USER` and `GITHUB_TOKEN` are set **before** launching VS Code:
   ```bash
   export GITHUB_USER=<your-username>
   export GITHUB_TOKEN=<your-token>
   code .
   ```
3. Alternatively, create a `.devcontainer/devcontainer.env` file:
   ```bash
   cp .devcontainer/devcontainer.env.example .devcontainer/devcontainer.env
   # Edit with your credentials
   ```

### Dev container fails to start on Apple Silicon

If the container exits immediately with an error like `exec format error` or `platform mismatch`:

1. Check that **Rosetta 2** is enabled in Docker Desktop settings.
2. Verify a **`docker-container`** buildx driver is active and set as default:
   ```bash
   docker buildx ls
   # The active driver (marked with *) should be 'docker-container', not 'docker'
   ```
   If not, create and switch to it:
   ```bash
   docker buildx create --name crossarch --driver docker-container --use --bootstrap
   ```
3. Verify that Docker Desktop is using the **Rosetta** backend (not VZ or QEMU fallback) for amd64 emulation.
4. Run the architecture detection script manually:
   ```bash
   bash .devcontainer/check-arch.sh
   ```
5. As a last resort, reset Docker Desktop to factory defaults and re-enable Rosetta.

## Original Project

This repository is a fork of the original Ontology Utils project. The preserved upstream README is available in [README-original.md](README-original.md).

Original authors: Nicolas Troquard, Roberto Confalonieri, Pietro Galliani, and Roland Bernard.
