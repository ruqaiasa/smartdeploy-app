package com.smartdeploy.deployment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionOrderTest {

    @Test
    void comparesVersionNumbersIgnoringSuffixes() {
        assertTrue(VersionOrder.isOlder("1.1.0", "1.2.0"));
        assertTrue(VersionOrder.isOlder("1.1.2-compilation", "1.2.0"));
        assertTrue(VersionOrder.isOlder("1.9", "1.10"), "comparaison numérique, pas alphabétique");
        assertFalse(VersionOrder.isOlder("1.2.0", "1.1.0"));
        assertFalse(VersionOrder.isOlder("1.1.0", "1.1"), "1.1.0 et 1.1 sont égales");
        assertTrue(VersionOrder.isOlder("v1.0.0", "v2.0.0"), "préfixe v toléré");
        assertFalse(VersionOrder.isOlder("release-candidate", "1.0.0"), "incomparable : pas d'avertissement");
    }
}
