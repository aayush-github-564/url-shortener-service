package com.aayush.url_shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeGeneratorTest {

    @Test
    void generatedCode_shouldBeSevenCharactersLong() {
        String code = ShortCodeGenerator.generate();
        assertEquals(7, code.length());
    }

    @Test
    void generatedCode_shouldOnlyContainAlphanumericCharacters() {
        String code = ShortCodeGenerator.generate();
        assertTrue(code.matches("^[a-zA-Z0-9]+$"),
                "Code should only contain a-z, A-Z, 0-9 but was: " + code);
    }

    @RepeatedTest(5)
    void generatedCode_shouldNotBeNull() {
        assertNotNull(ShortCodeGenerator.generate());
    }

    @Test
    void generate_shouldProduceUniqueCodesInBulk() {
        // Generate 1000 codes and confirm no duplicates
        // 62^7 = ~3.5 trillion combinations, so collisions here would be a bug
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(ShortCodeGenerator.generate());
        }
        assertEquals(1000, codes.size(), "Expected 1000 unique codes but found duplicates");
    }
}