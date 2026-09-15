# Increment 31 unified governance view demonstration

Composes OQ-16's view over spring-petclinic and prints all five
commissioned answers, with a second run showing what the view says when it
cannot answer two of them.

It exists because the view is deliberately **not** a `Main.toJson` key — two
of its five answers need a baseline the pipeline does not have, the same reason
`R` has none — so a committed scan cannot show it. It also needs a governance
declaration richer than `Main`'s illustrative one (subsystems, invariant
weights, drift sensitivity and an approved exception) to exercise every section
at once, and the committed default-path scans stay deliberately spare so they
remain comparable across increments.

`ViewDemo` sits in package `org.aerf.pipeline` because `Main.illustrativeConfig`
is package-private; widening production API for a demonstration would be the
wrong trade. Build and run it exactly as `../exception-demo/README.md`
describes, substituting `ViewDemo` and the modern petclinic clone
(`https://github.com/spring-projects/spring-petclinic.git`).

Observed:

```
=== WITH A BASELINE ===
1. what architectural condition was observed
   layer        OptionalDouble[0.6666666666666666] 14/21  confidence=OptionalDouble[0.3770491803278688]
   cycle        OptionalDouble[0.0]          0/118  confidence=OptionalDouble[0.3770491803278688]
   persistence  OptionalDouble[0.0]          0/10  confidence=OptionalDouble[0.38636363636363635]
   security     OptionalDouble.empty         0/0  confidence=OptionalDouble.empty
   totalEntropy=OptionalDouble[0.2222222222222222]  maturity=OptionalDouble[0.7777777777777778] L3_CONTROLLED
2. what changed relative to baseline
   layer        0.4000 -> 0.6667  delta=+0.2667
   cycle        0.0000 -> 0.0000  delta=+0.0000
   persistence  0.1000 -> 0.0000  delta=-0.1000
3. what governance constraints were violated
   no_presentation_to_persistence   I_k=1 lambda=OptionalDouble[1.0] severity=critical  unexcused=13/14
   entropy_budget                   I_k=0 lambda=OptionalDouble[4.0] severity=critical  unexcused=0/0
   E_inv=OptionalDouble[1.0]  (unbounded, as section 6.1 states)
4. what risk interpretation follows
   R=OptionalDouble[0.7555555555555554]  entropyTerm=OptionalDouble[0.2222222222222222]  driftTerm=OptionalDouble[0.5333333333333332]
      layer        gamma=1.0 delta=+0.2667 penalty=0.2667
      cycle        gamma=0.5 delta=+0.0000 penalty=0.0000
      persistence  gamma=1.0 delta=-0.1000 penalty=0.0000
5. what evidence supports each conclusion
   14 finding(s), 13 unexcused
      layer        EXCUSED judged-by=owner    owner.OwnerController -> owner.OwnerRepository
      layer        open   judged-by=owner    owner.OwnerController#findOwner(java.lang.Integer) -> owner.OwnerRepository#findById(java.lang.Integer)
      ...
   unanswered:
      (none - all five questions answered)

=== WITHOUT A BASELINE ===
   ... sections 1, 3 and 5 identical ...
2. what changed relative to baseline
   (not answered)
4. what risk interpretation follows
   (not answered)
   unanswered:
      - what changed relative to baseline: no baseline was supplied, so nothing was compared - this is not a statement that nothing changed
      - what risk interpretation follows: R is drift-aware and needs a baseline
```

Six things worth reading off that output:

- **Every number is one an earlier increment already produced.** `E_inv` = 1.0
  is increment 29's demo exactly; `R` = 0.7556 with the same three penalties is
  increment 30's exactly; `totalEntropy` = 0.2222 is unchanged since increment
  24. The view **composes**, it does not compute — which is why none of these
  could have drifted.
- **Nothing is collapsed.** The entropy term, the drift term, each per-dimension
  penalty, `E_inv`, each λ_k·I_k, and each finding are all still separately
  readable in one document. There is no overall score and no verdict.
- **Finding E is resolved:** `judged-by=owner` names the subsystem whose matrix
  produced each verdict. `vet` is declared too but with **no** matrix of its
  own, so nodes it claims are judged by the default — and the view says the
  default judged them rather than crediting `vet`.
- **Excusal marks, it does not remove.** One finding reads `EXCUSED`; the count
  stays 14, `layer` stays 14/21, and the invariant still reads `I_k=1` with
  13 of 14 violations unexcused. Accepting a finding is not denying it, and it
  certainly does not make a constraint hold.
- **The same edge is both a layer finding and an invariant violation**, counted
  in both places. That is not double-counting: §4.1's ratio and §6.3's rule are
  two different judgements about one edge, and merging them is precisely what
  the commission forbids.
- **An improved dimension contributes nothing.** Persistence went 0.10 → 0.00
  and its penalty is `0.0000`, not `-0.1000`.

The second run is the honest half: the same code, no baseline, and the view
**names the two questions it cannot answer** instead of showing two empty
sections a reader could mistake for "nothing changed".

No sample report is committed for this run: the view reaches no report key, so
a default-path scan has nothing for it to change — confirmed independently by
this increment's regression check, in which a fresh petclinic scan is identical
to increment 30's.
