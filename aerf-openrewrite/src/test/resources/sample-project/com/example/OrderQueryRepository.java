package com.example;

import org.springframework.data.repository.Repository;

// Idiomatic Spring Data repository: recognized by its marker-interface
// supertype (org.springframework.data.repository.Repository), not by an
// annotation - deliberately carries no @Repository at all. This is the
// real-world shape open question #18 (Increment 18's spring-petclinic
// run) found: OrderRepositoryImpl above is the annotation-based case
// already covered; this interface is the marker-interface-based case
// that was, until this increment, invisible to JavaSourceExtractor.
public interface OrderQueryRepository extends Repository<Order, Long> {

    Order findByStatus(String status);
}
