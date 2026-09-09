package com.example;

// Deliberately extends a type that is never declared in this sample and is
// not on any classpath supplied to the extractor, so it must stay
// unresolved (JavaType.Unknown) - the L1_SYNTAX fallback path's fixture.
public class LegacyWidget extends com.example.external.UnknownFramework {
}
