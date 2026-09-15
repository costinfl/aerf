# jsoup — cycle-rich

**Acquired for:** characteristic 1, cycle-rich. **Unblocks findings H and I.**

| | |
|---|---|
| Repository | `https://github.com/jhy/jsoup` |
| Pinned commit | `922ee0113abc7c450819b07c3330b865d7961b84` |
| Build | Maven, single module, 93 source files |
| Scan fidelity | **99.2 % `L2_SYMBOL_RESOLVED`** (12 671 of 12 776 evidence items) |

## Acceptance test — PASSED

`cycleEntropy.relevantSccs` must be non-empty with `includeSelfCycles = false`,
i.e. genuine multi-node strongly connected components rather than direct
recursion.

```
nodes 2436   edges 10340
SCCs 37      cycleEntropy 0.04556650246305419   (111 of 2436 nodes participating)
SCC sizes    {2: 26, 3: 4, 4: 3, 5: 1, 8: 1, 10: 1, 12: 1}
```

**This is the first non-zero cycle entropy in the project's history.** Every
prior committed sample — both petclinics, every increment — reported
`cycleEntropy: 0.0` with zero SCCs, which is exactly what findings H and I
recorded as blocking.

## The cycles are real

Spot-checked rather than assumed:

- **12 nodes — `select.QueryParser`**: `parse → parseNested → has/is/not →
  parse`. A hand-written recursive-descent CSS selector parser: textbook
  mutual recursion.
- **10 nodes — `nodes.Element`**: `text() ↔ html() ↔ data() ↔ ownText()`
  delegation.
- **8 nodes — `nodes.Element` + `nodes.Node`**: `appendChild ↔ addChildren ↔
  insertChildren`, genuinely **across two classes**.
- **5 nodes — `Attribute`, `Attributes`, `Element`, `Node`, `Range`**:
  `sourceRange()` resolution spanning five types.

## Why a parser and not a JPA application

The obvious cycle-rich candidate is a bidirectional JPA association
(`Owner.pets ⇄ Pet.owner`). **It would not have worked.** Finding Y: `DEPENDS`
edges drop generic type arguments, so `List<Pet>` yields an edge to `List` and
`Pet` never becomes a target. The "one" side of every one-to-many is invisible,
so no entity graph can close a cycle today.

Cycle entropy measures over `CALL` and `DEPENDS`, so mutual recursion between
**methods** is the shape AERF can actually see — which is why acquisition
retargeted to a parser.

## What this repository does not provide

Every node is `UNKNOWN`: jsoup is not a Spring application, so no stereotype or
marker-interface evidence exists. Layer and persistence entropy are therefore
undefined, and `totalEntropy` is undefined with them — `AggregatedEntropy`
poisoning the aggregate exactly as documented, here demonstrated on real code
rather than a fixture.

`roleRefinementPasses` is 0. Cycle-richness is the only characteristic claimed.

Confidence is notably high — 0.737 layer/cycle against petclinic's 0.377 —
because jsoup has almost no external dependencies, so most types resolve. That
is indirect support for finding Z.

## Reproduce

```sh
git clone https://github.com/jhy/jsoup.git /tmp/jsoup
cd /tmp/jsoup && git checkout 922ee0113abc7c450819b07c3330b865d7961b84
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=/tmp/jsoup-cp.txt

cd /path/to/aerf && mvn -q install -DskipTests
mvn -q -pl aerf-pipeline dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="/path/to/aerf/aerf-pipeline/target/classes:$(cat /tmp/cp.txt)"
java -cp "$CP" org.aerf.pipeline.Main /tmp/jsoup/src/main/java $(tr ':' ' ' < /tmp/jsoup-cp.txt)
```
