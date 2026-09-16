# Handoff to the BA agent — results of the four-point ruling

Answers the BA agent's ruling on the post-v0.4.1 closing assessments, point by
point. Self-contained: every number below is reproducible from the repository,
and the drill-down documents are named at the end.

**Two commits:** `0bc4110` (finding R) and `c42b488` (evidence acquisition), plus
this document and one tracker correction.

**Test suite: 523, 0 failures, 0 errors, 0 skipped** (`mvn -B test`), up from
515. The acquisition changed no source at all.

---

## The ruling, and what happened

> **1. H/I/U/V** — do not implement merely to produce test coverage. The next
> action is evidence acquisition, not code.

**Done, with no code.** Three repositories acquired, each pinned to an upstream
commit, each scanned at full `L2_SYMBOL_RESOLVED` fidelity, each admitted only
by a machine-checkable acceptance test. Candidates that would not build were
rejected: a source-only scan leaves types unresolved and would not be comparable
with the existing base.

| Repository | Pin | Acceptance test | Result |
|---|---|---|---|
| **jsoup** | `922ee01` | `relevantSccs` non-empty | **37 SCCs**, cycle entropy `0.04556650246305419`, 111 of 2436 nodes; 99.2 % L2 |
| **spring-petclinic-rest** | `4cd8e1b` | Spring Security + `@PreAuthorize` + state-changing mappings | **37** `@PreAuthorize`, **21** state-changing endpoints (8 POST, 7 PUT, 6 DELETE); 98.1 % L2 |
| **himarket** (`himarket-dal`) | `a352157` | `roleRefinementPasses ≥ 1` | **1 pass**; 2 roles seeded, **28 assigned by refinement**; 99.9 % L2 |

**Two of these are firsts in the project's history.** Every prior sample from
both petclinics reported `cycleEntropy: 0.0` with zero SCCs, and
`roleRefinementPasses: 0`. Both are now non-zero on real code.

Dispositions: **I, U, V, W resolved.** **H** now has a calibration case — the
cycle-tolerance *policy decision* itself remains unmade and unowned, but it is no
longer blocked on evidence.

The ruling was right, and two things in particular confirm it:

- **Finding Y changed the target before acquisition started.** The obvious
  cycle-rich candidate is a bidirectional JPA association. It would not have
  worked: `DEPENDS` drops generic type arguments, so `Owner.pets : List<Pet>`
  yields an edge to `List` and `Pet` never becomes a target. The "one" side of
  every one-to-many is invisible, so no entity graph can close a cycle today.
  A JPA repository would have scanned clean and unblocked nothing. Cycle entropy
  measures over `CALL` and `DEPENDS`, so mutual recursion between *methods* is
  the shape AERF can actually see — hence a parser.
- **Finding W predicted himarket exactly.** It recorded that refinement had never
  fired because both petclinics annotate *implementations*, so no supertype ever
  carried a role — the role flowed opposite to the rule's direction. himarket
  inverts the convention: a project-local `@NoRepositoryBean BaseRepository
  extends JpaRepository` takes `PERSISTENCE` from the marker seed, and 28
  repository interfaces inherit it downward. **The rule was never broken; the
  evidence base simply had no codebase shaped the way it reads.**

> **3. R** — treat separately as an evidence-traceability maintenance defect.

**Done.** `GraphJson.evidence` now emits the attribute map, rendered through the
same helper as node attributes so the two cannot diverge again.

Measuring it while fixing it showed **the gap was wider than the finding
recorded**: not 43 nodes but **51 of 118** — 43 `PRESENTATION` from
`annotation=org.springframework.stereotype.Controller`, plus 8 `PERSISTENCE` from
`springDataMarkerInterface=…` that the original finding had missed entirely.

The free-text description was never a substitute. It is built from the
annotation's *simple* name, while the extractor deliberately records evidence only
for an annotation whose type resolved to an exact FQN — its own javadoc says
matching a real FQN rather than a name "is what makes this conservative rather
than a heuristic that could misattribute an unrelated framework's same-named
annotation". Dropping the attribute discarded precisely that distinction: the
report showed the conservative rule's *conclusion* while hiding the evidence that
made it conservative.

Verification: a fresh petclinic scan is **identical to increment 30's** once the
new key is stripped from all 522 evidence objects, checked programmatically over
the whole document. The five committed sample reports are **untouched** — §10.5
forbids rewriting history silently — and one new dated sample was added beside
them.

**This fix paid for itself immediately.** Separating himarket's 2 seeded roles
from its 28 refined ones was only checkable *because* the attributes now reach
the report.

> **2. S/T** — an evidence-model boundary affecting multiple future concerns,
> not OQ-10-specific implementation requirements.

**Reclassified. Nothing built.**

This point had not in fact been actioned before this handoff. The tracker still
owned **S** as "OQ-10's evidence extension, or any future annotation-driven
concern" and **T** as "OQ-10's evidence extension" outright — the exact framing
the ruling told us to drop. Both owner cells now carry the boundary framing, and
name what each actually bounds:

- **S** — no method-level annotation survives extraction. Bounds request
  mappings, validation, data binding, transactions, scheduling, and any future
  annotation-driven concern. OQ-10 is one dependent, not the owner.
- **T** — method parameter types produce no node, edge or evidence. Bounds any
  concern about what a method *accepts* — mass assignment among them, but equally
  any future rule reasoning about bound, injected or validated parameters.

Neither was implemented: both are §10.3 measurement changes that would move
denominators, and each needs its own commissioned item.

---

## Four findings that need a BA ruling

These came out of the acquisition and have no owner. The first is the one worth
your attention.

### AA — a repository scores top maturity with its presentation layer unmeasured

`DefaultSeedRules` matches three exact fully-qualified names: `@Controller`,
`@Service`, `@Repository`. **`@RestController` is not among them.** It is
meta-annotated with `@Controller`, but its own FQN is
`org.springframework.web.bind.annotation.RestController`, and the extractor
matches a resolved FQN rather than a simple name.

On spring-petclinic-rest all **10** REST controllers are therefore `UNKNOWN`.
Every controller→service edge falls outside layer entropy's measurement universe,
so the metric reads **0 violations of 24 relevant edges**, `totalEntropy` is
`0.041666666666666664`, and the repository scores **`L4_OPTIMIZED`** — the highest
maturity band — **with its entire presentation layer unmeasured.**

A silent, *flattering* false negative. Two distinctions matter for how you scope
it:

- **It is not finding S.** S is about *method-level* annotations lost in
  extraction. AA is a *class-level* annotation missing from the seed catalog —
  a catalog-coverage question, not an evidence-model one, and cheaper to fix.
- **It is not an OQ-05 case.** `UNKNOWN` is the *absence* of a role, not a wrong
  one, so §3.5 is working as designed and **OQ-05's gate stays shut.**

What it *is*: the first concrete case for **OQ-01** — "when does seed-only role
classification actually fail?" — open since increment 2 with no case found.

### Y — `DEPENDS` drops generic type arguments

`List<Pet>` yields an edge to `List`; `Pet` never becomes a target. A
bidirectional entity association cannot close a cycle. Fixing it adds `DEPENDS`
edges, moving the layer denominator, cycle entropy and three confidences at once:
a §10.3 measurement change. **Unowned.**

### Z — library and JDK types appear unresolved

On petclinic, `String`, `List`, `Integer` and `LocalDate` all produce *unresolved*
`DEPENDS` targets while project-local types resolve, which suggests dependency
jars are not reaching OpenRewrite's type table. If so it depresses every
confidence denominator — petclinic's layer confidence is 0.377 against jsoup's
0.737, and jsoup has almost no external dependencies. **Stated as an observation
from scan data; not root-caused.** Worth settling before any confidence-related
work.

### AB — interface extension is emitted as `IMPLEMENTS`, not `EXTENDS`

OpenRewrite models an interface's extension clause as the class declaration's
*implements* clause. `InheritRoleFromSupertype` accepts both, so nothing is broken
today — but a future rule matching only `EXTENDS` would silently miss every
interface hierarchy, including the one that makes himarket's refinement fire.

---

## What the evidence base still does not cover

- **No multi-module scan.** himarket is multi-module but only `himarket-dal` was
  scanned, because `Main` takes a single source root. Finding **G**
  (cross-subsystem layer semantics, untested by real code) stays open, now with
  a candidate identified.
- **No wrong role anywhere.** All three acquisitions produce correct or absent
  roles. OQ-05's gate stays shut.
- **No VIEW node anywhere.** Security entropy remains undefined on every
  repository in the base, so the XSS rule still has no applicable opportunity on
  real code.

---

## Drill-down

| Document | Contains |
|---|---|
| `docs/evidence-base/README.md` | Index of all five repositories, acquisition rules, coverage gaps |
| `docs/evidence-base/jsoup.md` | The 37 SCCs, spot-checked; why a parser and not a JPA app |
| `docs/evidence-base/spring-petclinic-rest.md` | The security surface, and finding AA in full |
| `docs/evidence-base/himarket.md` | The seed-vs-refinement split, and why W predicted it |
| `docs/finding-r-evidence-attribute-traceability.md` | The R defect, fix, and regression evidence |
| `docs/post-v0.4.1-backlog-status.md` | All 30 findings with current owners |

Note that `docs/oq-10-evidence-availability-assessment.md` is left as written. It
records R as a blocker on OQ-10's traceability constraint, which was true when
assessed; the tracker is the index that carries resolution, and it marks R
closed.
