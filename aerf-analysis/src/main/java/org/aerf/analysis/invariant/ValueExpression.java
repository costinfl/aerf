package org.aerf.analysis.invariant;

import java.util.Objects;

/**
 * A value used inside a {@link Predicate}: either a reference to a
 * canonical property ({@link Property}) or a fixed value declared by the
 * invariant itself ({@link Constant}).
 */
public sealed interface ValueExpression {

    record Property(PropertyKey key, String metricName) implements ValueExpression {
        public Property {
            Objects.requireNonNull(key, "key");
            if (key == PropertyKey.METRIC) {
                Objects.requireNonNull(metricName, "metricName is required for PropertyKey.METRIC");
                if (metricName.isBlank()) {
                    throw new IllegalArgumentException("metricName must not be blank");
                }
            } else if (metricName != null) {
                throw new IllegalArgumentException("metricName is only meaningful for PropertyKey.METRIC");
            }
        }

        public static Property of(PropertyKey key) {
            return new Property(key, null);
        }

        public static Property metric(String metricName) {
            return new Property(PropertyKey.METRIC, metricName);
        }
    }

    record Constant(Object value) implements ValueExpression {
        public Constant {
            Objects.requireNonNull(value, "value");
        }
    }
}
