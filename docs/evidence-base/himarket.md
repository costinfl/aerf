# himarket — inheritance/refinement-rich

**Acquired for:** characteristic 3. **Unblocks findings V and W.**

| | |
|---|---|
| Repository | `https://github.com/higress-group/himarket` |
| Pinned commit | `a352157ba406eecc93ebac7a52babe9500c26950` |
| Build | Maven, **multi-module**; scanned module `himarket-dal`, 179 source files |
| Scan fidelity | **99.9 % `L2_SYMBOL_RESOLVED`** (1 536 of 1 537 evidence items) |

## Acceptance test — PASSED

`roleRefinementPasses ≥ 1`.

```
nodes 443   edges 1092
*** roleRefinementPasses: 1 ***
roles       UNKNOWN 413   PERSISTENCE 30
```

**This is the first non-zero refinement pass in the project's history.** Every
prior committed sample from both petclinics reported 0 — the fact finding V
recorded and the OQ-05 gate check quantified.

## Why it fires here and nowhere else

Finding W established the reason refinement had never run: both petclinics put
`@Repository` and `@Service` on the **implementation**, so every
`EXTENDS`/`IMPLEMENTS` target was either unresolved or role-`UNKNOWN`, and
`InheritRoleFromSupertype` only produces a candidate when the *supertype already
carries a role*. The role flowed the opposite way from the rule's direction.

himarket inverts that convention:

```java
@NoRepositoryBean
public interface BaseRepository<D, I> extends JpaRepository<D, I>, JpaSpecificationExecutor<D>
```

`BaseRepository` is **project-local** and extends `JpaRepository`, so increment
20's Spring Data marker rule gives it `PERSISTENCE` as a **seed**. Twenty-eight
repository interfaces then extend `BaseRepository`, and each inherits the role
by **refinement**.

Verified by separating the two mechanisms in the scan output — possible only
because the finding-R fix now serializes evidence attributes:

| How the role was assigned | Count |
|---|---|
| Seed — carries `springDataMarkerInterface=…JpaRepository` evidence | **2** (`BaseRepository` and its member method) |
| **Refinement — no marker evidence at all** | **28** |

and confirmed structurally: **28 `IMPLEMENTS` edges target the project-local
`BaseRepository`**.

### A detail worth recording

Java source `interface X extends Y` is emitted as **`IMPLEMENTS`**, not
`EXTENDS`. OpenRewrite models an interface's extension clause as the class
declaration's *implements* clause, and `JavaSourceExtractor` maps it faithfully.
`InheritRoleFromSupertype` accepts both relations, so refinement fires either
way — but any future rule that matches only `EXTENDS` would silently miss every
interface hierarchy.

## What this repository does not provide

No SCCs, so cycle entropy is 0.0. All 413 non-repository nodes are `UNKNOWN`
because `himarket-dal` is the persistence module — the controllers and services
live in `himarket-server`, which this scan does not cover. Layer and persistence
entropy are therefore undefined and `totalEntropy` with them.

Being multi-module, this repository is also the first candidate the evidence
base has for **finding G** (cross-subsystem layer semantics, untested by real
code). Scanning `himarket-server` alongside `himarket-dal` would exercise it —
not claimed here, since `Main` takes a single source root.

## Reproduce

```sh
git clone https://github.com/higress-group/himarket.git /tmp/himarket
cd /tmp/himarket && git checkout a352157ba406eecc93ebac7a52babe9500c26950
mvn -q -DskipTests -pl himarket-dal -am dependency:build-classpath \
  -Dmdep.outputFile=/tmp/himarket-cp.txt

CP="/path/to/aerf/aerf-pipeline/target/classes:$(cat /tmp/cp.txt)"
java -cp "$CP" org.aerf.pipeline.Main /tmp/himarket/himarket-dal/src/main/java \
  $(tr ':' ' ' < /tmp/himarket-cp.txt)
```
