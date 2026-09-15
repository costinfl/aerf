# AERF evidence base

Every repository AERF has been scanned against, why it is here, and what it
proves. Created when three repositories were acquired to unblock findings H, I,
U, V and W; the two petclinics are listed too, so the base is inspectable in one
place for the first time.

**Rules.** A repository is acquired for a *named characteristic* with a
*machine-checkable acceptance test*, pinned to an upstream commit, and scanned
at full `L2_SYMBOL_RESOLVED` fidelity — candidates that will not build are
rejected, because a source-only scan leaves types unresolved and is not
comparable with the rest of the base. Source is never vendored; each document
carries the clone-and-scan command.

## Acquired

| Repository | Characteristic | Acceptance test | Result | Unblocks |
|---|---|---|---|---|
| [jsoup](jsoup.md) | cycle-rich | `relevantSccs` non-empty | **37 SCCs**, cycle entropy 0.0456 | H, I |
| [spring-petclinic-rest](spring-petclinic-rest.md) | Spring Security + method-level | security on classpath + `@PreAuthorize` + state-changing mappings | 37 `@PreAuthorize`, 21 state-changing endpoints | U |
| [himarket](himarket.md) | inheritance/refinement-rich | `roleRefinementPasses ≥ 1` | **1 pass**, 28 roles by refinement | V, W |

## Pre-existing

| Repository | What it provides |
|---|---|
| `spring-projects/spring-petclinic` | The primary regression subject. Layer entropy 14/21, persistence 0/10, `totalEntropy` 0.2222. Re-scanned every increment; byte-identical results are how determinism (V04-ROLE-01) is checked. |
| `spring-petclinic/spring-framework-petclinic` | Legacy JDBC/JPA. Source of the only genuine N+1 AERF has found on real code — the evidence that decided OQ-08. |

## What the base now covers, and what it still does not

Three long-standing "no repository contains one" blockers are cleared: a real
cycle, a real Spring Security surface, a real refinement pass. Each was blocked
by absence of evidence, not by missing code, which is why acquisition rather
than implementation was the right next action.

Still uncovered:

- **A multi-module scan.** himarket is multi-module but only `himarket-dal` was
  scanned, because `Main` takes a single source root. Finding **G**
  (cross-subsystem layer semantics) stays open with a candidate now identified.
- **A repository that produces a *wrong* role.** All three acquisitions produce
  correct or absent roles. **OQ-05's gate stays shut** — `UNKNOWN` is absence,
  not error.
- **A VIEW node anywhere.** Security entropy remains undefined on every
  repository in the base, so the XSS rule still has no applicable opportunity on
  real code.

## Findings this acquisition produced

- **AA — `@RestController` is invisible to the seed catalog.** petclinic-rest's
  10 REST controllers are all `UNKNOWN`, so its layer entropy reads 0 violations
  of 24 edges and it scores **`L4_OPTIMIZED`** with its entire presentation layer
  unmeasured. A silent, flattering false negative, and the first concrete case
  for **OQ-01**.
- **Y — `DEPENDS` drops generic type arguments**, which is why the cycle-rich
  characteristic targeted a parser rather than a JPA application: a bidirectional
  entity association cannot close a cycle today.
- **Interface extension is emitted as `IMPLEMENTS`, not `EXTENDS`.** Refinement
  accepts both, but a future rule matching only `EXTENDS` would miss every
  interface hierarchy — including the one that makes himarket work.
