package com.example;

// The bottom of the multi-pass role-refinement chain: also carries no
// stereotype annotation of its own. Its role can only come from
// inheriting JpaOrderRepository's role - but JpaOrderRepository's own
// role does not resolve until the engine's *first* refinement pass
// completes (IterativeRoleInferenceEngine takes a fixed snapshot of
// roles at the start of each pass), so this class cannot inherit
// PERSISTENCE until the *second* pass. This is the real analogue of the
// synthetic inheritance-chain example IterativeRoleInferenceEngineTest
// has used since Increment 2 - see docs/increment-16-*.md.
public class OrderRepositoryImpl implements JpaOrderRepository {

    @Override
    public Order findById(Long id) {
        return new Order(id);
    }
}
