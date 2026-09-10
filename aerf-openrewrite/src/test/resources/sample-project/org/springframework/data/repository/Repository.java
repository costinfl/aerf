package org.springframework.data.repository;

// A minimal local stub of Spring Data's real marker interface - same
// package and simple name, so its fully-qualified name matches exactly
// what JavaSourceExtractor's SPRING_DATA_MARKER_INTERFACES set and a real
// spring-data-commons dependency would both produce (see Controller.java
// in ../../stereotype for the same reasoning applied to an annotation
// instead of an interface). Real Spring Data repositories are recognized
// by extending this interface (or a subinterface of it, e.g.
// CrudRepository/JpaRepository) without needing any annotation at all -
// this is exactly the real-world gap open question #18 (Increment 18's
// spring-petclinic run) found: idiomatic repositories carry no
// @Repository annotation, so JavaSourceExtractor must also recognize this
// marker-interface family by resolved supertype FQN.
public interface Repository<T, ID> {
}
