# spring-petclinic-rest — Spring Security + method-level web behaviour

**Acquired for:** characteristic 2. **Unblocks finding U** and gives OQ-10 its
first calibration case.

| | |
|---|---|
| Repository | `https://github.com/spring-petclinic/spring-petclinic-rest` |
| Pinned commit | `4cd8e1b0cd42578e882247d8801f6be5d402f118` |
| Build | Maven; needs `generate-sources` (OpenAPI generator) |
| Scan fidelity | **98.1 % `L2_SYMBOL_RESOLVED`** (2 061 of 2 101 evidence items) |

Chosen deliberately as the *same domain* as the existing base, so differences
are attributable to the characteristic rather than to an unfamiliar codebase.

## Acceptance test — PASSED

Spring Security on the compile classpath, **and** method-level security
annotations, **and** state-changing HTTP mappings.

| Criterion | Found |
|---|---|
| `spring-boot-starter-security` | yes, `pom.xml` |
| `@PreAuthorize` in `src/main/java` | **37** |
| State-changing mappings | **21** — 8 POST, 7 PUT, 6 DELETE (plus 16 GET) |

**The mappings live in generated sources**, not `src/main/java`: controllers
`implements PettypesApi` and the `*Api` interfaces are produced by
`openapi-generator-maven-plugin` into
`target/generated-sources/openapi/src/main/java`, carrying
`@RequestMapping(method = RequestMethod.POST)`. That is itself useful for a
future CSRF rule — it says the adapter must be pointed at generated roots too,
or the endpoint set is invisible.

## Stated caveat: the scan cannot show any of this

Findings S and T mean **no method-level annotation and no method parameter type
survives extraction**. This repository is acquired because the surface provably
exists in source; the scan proves only that it is scannable at L2 and records a
baseline. It becomes usable evidence for OQ-10 once S/T are addressed.

## What the scan measured

```
nodes 471    edges 1479
roles        UNKNOWN 325   PERSISTENCE 110   APPLICATION 36   PRESENTATION 0
layer        0.0  (0 violations of 24 relevant edges)
cycle        0.0  (0 SCCs)          persistence  0.125
totalEntropy 0.041666666666666664   maturity 0.9583  L4_OPTIMIZED
roleRefinementPasses 0
```

### The most important number here is `PRESENTATION 0`

This codebase has **10 `@RestController` classes** and AERF classified none of
them. `DefaultSeedRules` matches three exact fully-qualified names —
`@Controller`, `@Service`, `@Repository`. `@RestController` is meta-annotated
with `@Controller` but its own FQN is
`org.springframework.web.bind.annotation.RestController`, which the catalog does
not list, and the extractor deliberately matches a resolved FQN rather than a
simple name.

The consequence is not a neutral gap. Because the controllers are `UNKNOWN`,
every controller→service edge falls outside layer entropy's measurement universe,
so the denominator is 24 instead of what it should be and the numerator is 0.
**The repository scores `L4_OPTIMIZED`, the highest maturity band, while its
entire presentation layer is unmeasured.** A silent, flattering false negative.

Recorded as finding **AA**. Note what it is and is not: `UNKNOWN` is *absence of
a role*, not a *wrong* role, so §3.5 is working as designed and **OQ-05's gate
stays shut**. What it does feed is **OQ-01** — "when does seed-only role
classification actually fail?" — which has stood open since increment 2 with no
case found. This is a case.

## Reproduce

```sh
git clone https://github.com/spring-petclinic/spring-petclinic-rest.git /tmp/petclinic-rest
cd /tmp/petclinic-rest && git checkout 4cd8e1b0cd42578e882247d8801f6be5d402f118
./mvnw -q -DskipTests generate-sources dependency:build-classpath -Dmdep.outputFile=/tmp/rest-cp.txt

CP="/path/to/aerf/aerf-pipeline/target/classes:$(cat /tmp/cp.txt)"
java -cp "$CP" org.aerf.pipeline.Main /tmp/petclinic-rest/src/main/java $(tr ':' ' ' < /tmp/rest-cp.txt)
```
