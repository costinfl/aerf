package org.springframework.stereotype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// A minimal local stub of Spring's real annotation - same package and
// simple name, so its fully-qualified name matches exactly what
// DefaultSeedRules and a real Spring dependency would both produce -
// deliberately not the real spring-context dependency, so the sample
// project stays parseable with zero external classpath (see the
// ExtractionAdapter Plan's Increment 13 owner decision: "no network at
// analysis time"). Meta-annotation semantics (Spring's real @Controller
// is itself meta-annotated with @Component) are not modeled here.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
}
