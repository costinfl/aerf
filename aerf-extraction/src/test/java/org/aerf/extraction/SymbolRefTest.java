package org.aerf.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SymbolRefTest {

    @Test
    void ofSingleArgumentUsesTheKeyAsItsOwnDescription() {
        SymbolRef ref = SymbolRef.of("com.example.Foo");

        assertEquals("com.example.Foo", ref.key());
        assertEquals("com.example.Foo", ref.description());
    }

    @Test
    void ofTwoArgumentsKeepsKeyAndDescriptionSeparate() {
        SymbolRef ref = SymbolRef.of("com.example.Foo", "reference to Foo in method body");

        assertEquals("com.example.Foo", ref.key());
        assertEquals("reference to Foo in method body", ref.description());
    }

    @Test
    void blankKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SymbolRef.of("  "));
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SymbolRef.of("key", "  "));
    }
}
