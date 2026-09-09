package com.example;

// Deliberately carries no stereotype annotation of its own: the middle
// link of the multi-pass role-refinement chain. Its role can only come
// from graph-relationship refinement (EXTENDS/IMPLEMENTS inheritance,
// AERF v0.4 section 3.2's R^(n+1)) inheriting PERSISTENCE from
// OrderRepository - resolvable in the engine's first refinement pass,
// since OrderRepository's own role is already known from its seed
// evidence before any pass runs.
public interface JpaOrderRepository extends OrderRepository {
}
