# OQ-10 — CSRF and Mass-Assignment: Evidence-Availability Assessment

Post-v0.4.1 implementation phase. This is the **first** of OQ-10's four
commissioned steps, and deliberately not the fourth.

The backlog commissions, in order:

1. examine evidence available from the current extraction model;
2. decide whether the current rule contract can express the required evidence;
3. if not, define the smallest evidence/rule extension;
4. **implement only after that contract is established.**

Steps 1–3 are below. **Step 4 is not taken here**, and the reason is
recorded rather than assumed: step 1's answer turns out to block it.

**Constraints:** do not assume a technology is insecure merely because it is
present; preserve the opportunity-vs-weakness distinction; retain traceable
evidence.

## Step 1 — What the extraction model actually produces

Measured on a real scan of modern spring-petclinic (118 nodes, 353 edges),
not inferred from reading the extractor:

| Fact | Observed |
|---|---|
| Node types emitted | `FUNCTION` 93, `COMPONENT` 25. **No `VIEW` node at all.** |
| Relations emitted | `CALL` 220, `MEMBER_OF` 93, `DEPENDS` 24, `IMPLEMENTS` 8, `EXTENDS` 8 |
| Node `attributes` keys | **none — the map is never populated by any adapter** |
| Evidence `sourceAdapter` values | `openrewrite-java` 118, `spring` 43, `spring-data` 8 |
| Distinct evidence descriptions | class/interface/method declaration observed; `@Controller annotation observed`; `extends Spring Data marker interface …` |
| Annotations that become evidence | exactly **three** FQNs: `@Controller`, `@Service`, `@Repository` |
| Where annotations are read | `classDecl.getLeadingAnnotations()` **only** — `visitMethodDeclaration` never reads annotations, so **no method-level annotation exists anywhere in the model** |
| Method parameter types | produce **no node, no edge and no evidence** — the extractor documents this explicitly: parameters "are deliberately excluded from this increment's `DEPENDS` scope" |

### The surface OQ-10 targets is genuinely present in the evidence base

This is not a hypothetical. Modern petclinic contains a real
mass-assignment surface, and it is **protected**:

```java
// VisitController
@InitBinder
public void setAllowedFields(WebDataBinder dataBinder) {
    dataBinder.setDisallowedFields("id", "*.id");
}

@PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
public String processNewVisitForm(@ModelAttribute Owner owner, @PathVariable int petId,
                                  @Valid Visit visit, BindingResult result, …)
```

`OwnerController` and `PetController` carry the same pattern. So the evidence
base contains applicable mass-assignment **opportunities where the control is
present** — precisely the case §4.4's opportunity-vs-weakness distinction
exists for, and a far better calibration case than a synthetic vulnerable
fixture would be.

CSRF is the opposite: **petclinic has no Spring Security dependency at all**,
so there is no CSRF configuration site, no protected endpoint, and no
unprotected one. There is nothing to calibrate a CSRF rule against.

### What survives extraction, and what does not

Taking that one real method through the pipeline:

| Ingredient a rule would need | Present? |
|---|---|
| The endpoint is request-bound and state-changing (`@PostMapping`) | **No.** Method annotations are never read. |
| An entity is bound from request data (`@ModelAttribute Owner owner`) | **No.** Neither the annotation nor the parameter type produces evidence. The types appear *only* inside the node id string. |
| A sibling method configures the binder (`@InitBinder`) | **Partly.** `setAllowedFields(org.springframework.web.bind.WebDataBinder)` **is** a `FUNCTION` node, reachable from the controller by `MEMBER_OF` — but its `@InitBinder` annotation is absent, so it is distinguishable only by its parameter type. |
| The binder is actually restricted (`setDisallowedFields(…)`) | **Weakly.** It survives as an **unresolved** `CALL` edge carrying only the name `setDisallowedFields` — `L1_SYNTAX`, with no confirmation that the receiver is `WebDataBinder`. Matching it is a name heuristic. |
| CSRF configuration state | **No**, and no repository in the evidence base contains any. |

**The asymmetry is the finding.** The *control* side is weakly observable; the
*opportunity* side is not observable at all. That ordering matters, because
§4.4 requires the opportunity denominator first: a rule must return empty when
there is nothing to assess, and one cannot report "protected" without first
establishing there was something to protect. Reporting petclinic's binder
allowlist as a satisfied control today would mean counting a control for an
opportunity the model cannot see — inventing the denominator from the
numerator.

### An adjacent defect found while measuring

`GraphJson.evidence` serializes `sourceAdapter`, `description`, `location`,
`fidelity` and `executionContext` — and **silently drops
`Evidence.attributes()`**.

That map is not decorative. `DefaultSeedRules` matches on
`evidence.attributes().get("annotation")`, and on this scan that attribute is
what assigns **43 of 118 nodes** their `PRESENTATION` role. So every report
this project has ever produced shows a role whose deciding evidence is
invisible in the report. It is also the exact channel a node-evidence
convention for OQ-10 would use, so OQ-10 cannot satisfy its own "retain
traceable evidence" constraint until it is fixed. Recorded as finding **R**.

## Step 2 — Can the current rule contract express this?

**No, and not marginally.**

`SecurityOpportunityRule.evaluate(Node node)` receives one node. It has no
graph, no edges, and no view of any other node.

- **Mass assignment is irreducibly a two-node question.** The bound entity is
  a property of *this* method; the binder allowlist is a property of a
  *sibling* method, reachable only by traversing `MEMBER_OF` to the declaring
  class and then an outgoing `CALL`. A node-local rule cannot see the sibling.
- **CSRF is a graph-wide configuration question.** Whether protection is
  enabled is a property of a security-configuration site somewhere else
  entirely, and it applies to every state-changing endpoint at once — a
  one-to-many relationship a per-node rule has no shape for.
- **The one apparent shortcut is closed.** The parameter types *are* present
  inside node ids (`…#processNewVisitForm(…owner.Owner,int,…owner.Visit,…)`),
  so a rule could in principle parse them out. It must not: increment 26
  established that `NodeId` is opaque to AERF, which "only calls `startsWith`
  and never parses or interprets", and reconstructing a method signature from
  an identifier is exactly the parsing that decision refused. It would also
  silently couple every security rule to the Java adapter's id format.

So the honest answer is that the contract cannot express it — but note
*which* part is contract-limited. Even given a graph-aware rule, the facts
it would need to read (`@PostMapping`, `@ModelAttribute`, `@InitBinder`, the
bound parameter types) **do not exist in the model at all**. The rule contract
is the second obstacle, not the first.

## Step 3 — The smallest extension

The backlog offers two shapes: "an edge-aware rule shape **or** a documented
node-evidence convention". The assessment recommends the **second**, with one
addition, and rejects the first.

### Reject: giving rules the graph

An `evaluate(Node, Graph)` contract would let any security rule measure
anything, reopening the NodeId-parsing temptation and dissolving the boundary
that makes rules reviewable. It also solves the wrong problem: the missing
facts are missing from extraction, not from the rule's reach.

### Recommend: extraction records the facts; the rule keeps its node

Three changes, in dependency order. The first is a prerequisite for the other
two and is worth doing on its own merits.

**(R) Serialize `Evidence.attributes()` in `GraphJson`.** Purely additive,
independent of OQ-10, and required by its "retain traceable evidence"
constraint. It will change every committed sample report by one key per
evidence object, so it needs its own recorded decision — which is why it is
not done here.

**(1) The adapter records the opportunity, as observation only.** On a
`FUNCTION` node, structured evidence for its own leading annotations (by
resolved FQN, exactly as `springStereotypeEvidence` already does for the three
stereotypes — never by simple name) and for each bound parameter's resolved
type. This is the precedent this project already runs on: increment 20 added
Spring Data marker evidence and increment 15 added stereotype evidence, both
so that rules written earlier could read real facts with no rule-side change.

**(2) The adapter summarizes the sibling fact onto the node.** The extractor
already visits the whole class body, so it can record on each method node
whether its declaring class declares an `@InitBinder`-annotated method and
whether that method calls a binder-restricting method — with fidelity
reflecting whether the receiver resolved, so the weak `L1_SYNTAX` case stays
visibly weak.

This is an **observation**, not a judgement, and the distinction is what keeps
the boundary intact: "this class declares an `@InitBinder` method that calls
`setDisallowedFields`" is Java/Spring knowledge the adapter legitimately has;
"a bound entity with no allowlist is a mass-assignment weakness" is concern
knowledge that stays in the rule. **`SecurityOpportunityRule` then needs no
change at all** — `evaluate(Node)` is already sufficient, and the existing XSS
rule already works exactly this way, reading
`evidence.attributes().get("outputEncoding")`.

### CSRF stays out of scope even after that

Extension (1) would make a CSRF *opportunity* visible (a state-changing
mapping annotation). It would **not** make the control visible: whether
protection is enabled lives in a security configuration that petclinic does
not have and that no repository in this project's evidence base contains.

That is the same situation as finding H for cycle tolerance — a decision with
no real case to calibrate against — and §10.4's rule applies: **do not
implement a rule whose behaviour no scanned repository can demonstrate.** A
CSRF rule built now would be untestable against reality and would risk
violating the constraint it is most exposed to, "do not assume a technology is
insecure merely because it is present": absent evidence of a CSRF filter, the
tempting inference is that protection is missing, when the truth is that
nothing was observed.

## Conclusion

| Question | Answer |
|---|---|
| Is the required evidence available today? | **No.** Method annotations and parameter types do not survive extraction at all; the binder control survives only as an unresolved call name. |
| Can the current rule contract express it? | **No** — mass assignment is a two-node question and CSRF a graph-wide one, while `evaluate(Node)` sees one node. But extraction is the first obstacle, not the contract. |
| What is the smallest extension? | Adapter-side evidence for mapping annotations, bound parameter types and the `@InitBinder` sibling fact, plus serializing `Evidence.attributes()`. **`SecurityOpportunityRule` needs no change.** |
| Should it be implemented now? | **Mass assignment: not yet** — it needs the evidence extension first, which is a real adapter increment with its own §10.3 obligations, since it changes the security denominator. **CSRF: no** — no repository in the evidence base contains any CSRF configuration, so there is nothing to calibrate against. |

**OQ-10 therefore stays OPEN, with step 1 complete and steps 2 and 3 answered.**
It is no longer an unexamined item: what is missing, why the contract cannot
reach it, and what the smallest extension would be are now recorded, so
implementing it is a scoped increment rather than an open question.

## Findings recorded

- **R — `Evidence.attributes()` never reaches the report.** `GraphJson` drops
  it; `DefaultSeedRules` depends on it for 43 of 118 role assignments on real
  code. A reader cannot see the attribute that decided a role. Additive to
  fix, but it changes every committed sample report, so it needs its own
  decision record. **Blocks OQ-10's "retain traceable evidence" constraint.**
- **S — No method-level annotation survives extraction.** Only three
  class-level stereotype FQNs become evidence. Every annotation-driven
  concern — mapping methods, validation, binding, transactions, scheduling —
  is invisible for the same reason, so this is broader than OQ-10.
- **T — Method parameter types produce no node, edge or evidence**, by an
  explicit extractor decision. They survive only inside node id strings, which
  increment 26 made off-limits to parse. Any concern about what a method
  *accepts* is unreachable today.
- **U — CSRF has no calibration case in the evidence base**, exactly as
  cycle tolerance does not (finding H). Both petclinics lack Spring Security
  entirely.
