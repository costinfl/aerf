# OQ-05 — Non-Monotonic Role Refinement: Gate Check

Post-v0.4.1 implementation phase. OQ-05 is **GATED**, and this document is
the check, not the work.

The backlog is explicit about what a gate means:

> **Gate:** do not implement revising inference merely because it is on the
> backlog.
>
> If a real scan produces a demonstrably wrong role that revision would
> correct: capture the concrete case; define revision semantics; establish a
> termination/convergence bound; define rule conflict resolution; add
> adversarial oscillation tests; implement only then.
>
> **If no motivating real case appears, leave OQ-05 open.**

So the only question this document answers is: **has a real scan produced a
demonstrably wrong role that revision would correct?**

## Evidence examined

Every scan this project has on record — both repositories in the evidence
base, across every increment that produced a committed sample:

| Report | Refinement passes | Roles assigned |
|---|---|---|
| `spring-petclinic.json` (increment 19) | **0** | 43 PRESENTATION, 75 UNKNOWN |
| `spring-petclinic-rescan-post-increment-20.json` | **0** | 43 PRESENTATION, 8 PERSISTENCE, 67 UNKNOWN |
| `…post-increment-24.json` | **0** | identical |
| `…post-increment-25.json` | **0** | identical |
| `…subsystems-post-increment-26.json` | **0** | identical |
| `spring-framework-petclinic.json` (legacy) | **0** | 35 PRESENTATION, 40 PERSISTENCE, 11 APPLICATION, 130 UNKNOWN |
| fresh rescan, this increment | **0** | identical to increment 20 |

## Finding 1 — no role is wrong

Every one of the **137** non-UNKNOWN roles across both repositories traces
to exactly one declared, resolved fact:

- `PRESENTATION` ← a `@Controller` annotation whose type resolved to
  `org.springframework.stereotype.Controller`;
- `APPLICATION` ← a resolved `@Service`;
- `PERSISTENCE` ← a resolved `@Repository`, or `extends` a Spring Data
  marker interface (increment 20).

None comes from a naming heuristic. Spot-checking the cases that look
suspicious at a glance confirms them: `OwnerController#<constructor>(OwnerRepository)`
is `PRESENTATION` — correct, it is a member of a `@Controller` class, and the
repository is its *parameter*, not its owner — and
`VisitController#setAllowedFields(WebDataBinder)` is `PRESENTATION` for the
same reason.

**Precedence conflicts: 0.** No node in either repository carried competing
role signals at all, so `RolePrecedence` never had to choose between two
candidates. The one known weakness in precedence — that a naming heuristic
would outrank a governance declaration, pinned by
`roleConflictsAreStillResolvedByRolePrecedenceAloneWithNoNotionOfAuthorship` —
has therefore never been exercised on real code either.

## Finding 2 — UNKNOWN is not a wrong role

205 nodes across the two repositories are `UNKNOWN`. That is not 205 errors.
§3.5 requires unclassified information to stay visibly separate from a
measured result rather than being folded into it, and `Role.UNKNOWN` is that
separation working as designed. Revising inference does not fill UNKNOWNs —
**monotonic** refinement does, and it already exists.

## Finding 3 — refinement has never made a single pass, and there is a concrete reason

This is the decisive one. Revision means *changing a role a rule already
assigned*. For that to be necessary, a node must be assigned a role and then
later evidence must contradict it. On real code no node has ever been
assigned a role twice, because graph refinement has never run a pass at all.

The reason is not that the rule is broken. `InheritRoleFromSupertype` produces
a candidate only when an `EXTENDS`/`IMPLEMENTS` target is a resolved node that
already carries a non-UNKNOWN role. Across both repositories, **every such
target is either unresolved or role-UNKNOWN** — checked edge by edge:

- modern petclinic: unresolved targets are framework types outside the scanned
  source (`JpaRepository`, `Serializable`, `WebMvcConfigurer`, `Validator`);
  every resolved target is a domain base class (`BaseEntity`, `NamedEntity`,
  `Person`) which carries no stereotype and is correctly `UNKNOWN`;
- legacy petclinic: the resolved targets are the repository *interfaces*
  (`OwnerRepository`, `PetRepository`, `VetRepository`, `VisitRepository`) and
  `ClinicService` — all `UNKNOWN`, because in that codebase the `@Repository`
  and `@Service` annotations sit on the **implementations**, not the
  interfaces.

So in the one place a supertype-inheritance rule could plausibly have fired,
the role flows the *opposite* way from the one it reads: the concrete class
knows its role and the interface does not.

## Conclusion — the gate has not opened

| Gate condition | Status |
|---|---|
| A real scan produced a role | Yes — 137 across two repositories |
| Any of them demonstrably wrong | **No.** Each traces to one resolved declaration; 0 precedence conflicts. |
| Any of them wrong *in a way revision would correct* | **No.** Revision changes an assigned role; nothing has ever been assigned twice. |
| A motivating real case | **None.** |

**OQ-05 stays GATED.** Nothing here is a reason to implement revising
inference, and per the backlog's own instruction the item is left open rather
than closed — the absence of a motivating case is not evidence that none can
exist, only that none has appeared in this evidence base.

This also confirms V04-ROLE-02 (monotonic refinement) holds on real code by
the strongest available margin: not merely "no revision was observed" but
"no second assignment was ever attempted".

Increment 24 added the complementary determinism evidence for V04-ROLE-01: the
same repository re-scanned from a different directory four increments later
produced a byte-identical graph with every role assignment in the same order.

## Findings recorded

- **V — Graph refinement is unexercised on real code.** Zero passes in every
  committed sample from both repositories, for the concrete reason above. The
  monotonic refinement contract is therefore verified by unit tests and by
  fixtures, but has never been demonstrated on a real repository — the same
  class of gap as findings G and I. A repository whose stereotypes sit on
  interfaces, or a multi-module one, would exercise it.
- **W — The evidence base's inheritance edges run against the one refinement
  rule's direction.** Both petclinics annotate implementations rather than
  interfaces, so a role could propagate implementation → interface but never
  supertype → subtype. Propagating the other way would be a **monotonic**
  change filling `UNKNOWN`s, explicitly **not** OQ-05's revising inference,
  and it is recorded here as an observation rather than proposed: it would
  change role assignment, hence every downstream metric, so it needs its own
  decision record and §10.3 treatment.
- **X — `RolePrecedence`'s authorship weakness remains untested by real
  code.** Zero precedence conflicts in either repository, so the case where a
  naming heuristic outranks a declaration has never occurred outside a unit
  test. It stays pinned by `GovernanceBoundaryTest` and remains OQ-02's
  still-open half.
