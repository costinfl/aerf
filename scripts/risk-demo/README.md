# Increment 30 risk-model demonstration

Computes AERF v0.4 §5.3's
**`R = Σ(w_d·f_d(E_d)) + β·Σ(γ_d·max(0, Δ_d))`** over two measurements of
spring-petclinic, and then shows `R` **refusing** when the two measurements were
governed by different policies.

It exists because `R` is deliberately **not** a `PipelineReport` component and
has no `Main.toJson` key — the pipeline has no `subjectId` and no prior
measurement, so it genuinely cannot compute `R`. `Risk.compute` is a pure
function a caller invokes, the sibling of `Drift.compute`, and this driver is
what a caller looks like on real code.

`RiskDemo` sits in package `org.aerf.pipeline` because `Main.illustrativeConfig`
is package-private; widening production API for a demonstration would be the
wrong trade. Build and run it exactly as `../exception-demo/README.md`
describes, substituting `RiskDemo` and the modern petclinic clone
(`https://github.com/spring-projects/spring-petclinic.git`).

Observed:

```
=== same policy ===
E_total (entropy term) = OptionalDouble[0.2222222222222222]
drift term (beta=2.0)  = OptionalDouble[0.5333333333333332]
   layer        gamma=1.0 delta=+0.2667 penalty=0.2667
   cycle        gamma=0.5 delta=+0.0000 penalty=0.0000
   persistence  gamma=1.0 delta=-0.1000 penalty=0.0000
R                      = OptionalDouble[0.7555555555555554]

=== policy changed between the two measurements ===
layer entropy OptionalDouble[0.0] -> OptionalDouble[0.6666666666666666]  (same code, different matrix)
R = OptionalDouble.empty
   because: the two measurements were governed by different policies, so their difference is not necessarily code drift
```

Four things worth reading off that output:

- the **two terms stay separately visible**, and each per-dimension penalty
  beside them. `R` is a total you can always read back to its parts — which is
  what makes "do not collapse them into an opaque single score" structural
  rather than argued;
- **only worsening is penalized.** Persistence *improved* by 0.10 and
  contributed penalty `0.0`, not `−0.10`: `max(0, Δ_d)` means an improvement
  cannot buy down a regression elsewhere;
- **γ_d = 0.5 on cycle is not a discount applied to a nonzero delta** — cycle
  did not move at all, so its penalty is zero for that reason, not for its
  weight;
- the second run uses the **same source code**. Only the layering matrix
  differs, and layer entropy moves 0.0 → 0.667 as a result. That is a pure
  governance change, and `R` refuses to interpret it as drift. This is
  finding B, fixed.

No sample report is committed for this run: `R` reaches no report, so there is
nothing in a default-path scan for it to change — which the increment's
regression check confirms independently.
