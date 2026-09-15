# Increment 29 `E_inv` demonstration

Computes AERF v0.4 §6.1's `E_inv = Σ(λ_k · I_k(S))` over spring-petclinic's own
two illustrative invariants, with weights declared.

It exists because `Main`'s illustrative config declares **no** weights — so the
committed default-path scans stay comparable across increments and `E_inv` reads
undefined there — and because the formula deserves showing on real code.

`EinvDemo` sits in package `org.aerf.pipeline` because `Main.illustrativeConfig`
is package-private; widening production API for a demonstration would be the
wrong trade. Build and run it exactly as `../exception-demo/README.md` describes,
substituting `EinvDemo` and the modern petclinic clone.

Observed:

```
DECLARED [no_presentation_to_persistence λ=1.0, entropy_budget λ=4.0]
totalEntropy=0.2222222222222222  (unchanged by weighting)
E_inv=1.0
  term  no_presentation_to_persistence  lambda=1.0  I_k=1  contribution=1.0
  term  entropy_budget                  lambda=4.0  I_k=0  contribution=0.0
unevaluated=[]
skippedInvariants=[]
```

Three things worth reading off that output:

- petclinic violates the layering invariant (`I_k = 1`) and satisfies the
  entropy budget, since 0.222 is under the declared 0.35 (`I_k = 0`);
- the **heavily**-weighted invariant contributes **nothing**, because λ_k
  multiplies an indicator that is zero — weight is importance, not a score;
- `totalEntropy` is unchanged. `E_inv` is reported beside the §5 aggregate and
  never inside it: §6.1 imposes no normalization on λ_k, so `E_inv` is
  unbounded and is not an entropy dimension.
