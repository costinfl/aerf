# Increment 11 — Evidence Structured Attributes

## Objective

First step of the ExtractionAdapter Plan (`docs/extraction-adapter-plan.md`):
give `Evidence` a structured, adapter-defined attribute map, so a real
extraction adapter can report a structured fact (an annotation's
fully-qualified name, a resolved encoding function) rather than having
to compose prose for the illustrative rule catalogs to re-parse.

## Scope

- `aerf-model`: `Evidence` gains `attributes()` — an open, order-stable
  `Map<String, String>` — plus a new `Evidence.Builder` for constructing
  evidence with attributes attached. The four existing `of(...)`
  factories are unchanged and continue to produce evidence with empty
  attributes.
- `aerf-analysis`: `DefaultSeedRules`'s two annotation-matching rules
  (`PresentationBySpringControllerAnnotation`,
  `ApplicationBySpringServiceAnnotation`) and `DefaultSecurityRules`'s
  `UnencodedViewOutputRule` updated to check a structured attribute
  first, falling back to the original prose substring match.
- 10 new tests (6 in `EvidenceTest`, 2 in `DefaultSeedRulesTest`, 2 in
  `DefaultSecurityRulesTest`), plus every pre-existing test unchanged
  and still green.
- **Amendment 5** recorded in `aerf-v0.4.1-patch.md`.

## Why a builder, not more `of(...)` overloads

`Evidence` already has four static factories covering every combination
of optional `location` and `executionContext`. A sixth field would
double that to eight. A builder (`Evidence.builder(sourceAdapter,
description, fidelity)` → `.location(...)`, `.executionContext(...)`,
`.attribute(key, value)`, `.attributes(map)`, `.build()`) scales to
future optional fields without overload explosion, and reads naturally
at adapter call sites, which will attach several attributes at once.

## Why this required no migration

Verified before writing any code: **all 65 existing `Evidence.of(...)`
call sites are in test sources.** No production code in `aerf-model`,
`aerf-analysis`, or `aerf-report` constructs `Evidence` — every increment
so far analysed hand-built or fixture graphs, never emitted evidence
itself. So adding `attributes()` (defaulting to empty via the unchanged
factories) is purely additive: nothing needed updating just to keep
compiling, and nothing changed behavior for any evidence built before
this increment.

## Implementation decisions not dictated by v0.4

1. **No attribute key vocabulary is prescribed as canonical.** Same
   reasoning as `SecurityOpportunityRule.Finding`'s `concern` field
   (Increment 7): v0.4 doesn't define one, so none is invented as "the"
   AERF vocabulary. The two conventions the illustrative rules now use
   (`annotation`, `outputEncoding`) are documented as the conventions
   *those specific rules* look for, not a schema.
2. **Attribute-based matching always falls back to prose.** A rule
   checks the structured attribute first; if absent, it falls back to
   the original substring match on `description`. This is why every
   pre-existing test (including the entire `CanonicalSampleGraphs`
   fixture, unmodified) still passes unchanged — the fallback exists
   specifically so this increment doesn't force a migration of evidence
   that predates it.
3. **`attribute(key, value)` rejects a blank key** but not a blank
   value — mirroring `Node.attributes()`'s existing lack of value
   validation, extended with the one check that seemed clearly
   warranted (a nameless attribute is meaningless; an empty-string value
   for a real key is a legitimate thing an adapter might observe, e.g.
   an annotation attribute with no arguments).

## Evidence — what this increment proves or exposes about the AERF model

- The dual-matching design (attribute-first, prose-fallback) is directly
  testable and was tested: `controllerRuleFiresFromTheStructuredAnnotationAttributeAlone`
  and its two siblings construct evidence with *no* matching substring
  anywhere in the description, proving the attribute path is genuinely
  independent of the prose path, not just a second way to trigger the
  same string search.
- Finding that zero production call sites needed to change is itself
  evidence about how the project has built each increment: every prior
  increment's "illustrative rules" were designed and tested against
  evidence *the tests themselves* constructed, never against evidence a
  production code path emitted. That was always the plan (no adapter
  existed yet), but it's a concrete confirmation that the boundary
  between "the model" and "what feeds the model" has stayed clean enough
  for this kind of change to land without touching a single line of
  production evidence-construction code.

## Open questions for the architecture

No new entries in `open-questions-register.md` — the attribute
vocabulary question is the same shape as register item #11 (security
concern vocabulary), not a new one, and is noted as such in Amendment 5.

## Scope check

No new node/edge/relation concepts, no changes to any entropy
calculator, no changes to role inference's engine (only its illustrative
rule catalog), no `aerf-extraction` module yet, and no OpenRewrite
dependency anywhere. This increment is exactly the model change the
ExtractionAdapter Plan calls for before Increment 12's extraction bridge.
