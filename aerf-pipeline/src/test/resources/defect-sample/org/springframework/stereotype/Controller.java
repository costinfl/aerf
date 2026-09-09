package org.springframework.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Local stub, not the real spring-context dependency - see
// aerf-openrewrite's identical stub for why (ExtractionAdapter Plan:
// "no network at analysis time"). Duplicated here rather than shared
// across modules because each module's sample/defect fixture is
// deliberately self-contained test data, never production code other
// modules depend on.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
}
