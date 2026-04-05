package com.aayush.url_shortener.service;

import java.security.SecureRandom;

/**
 * Generates random short codes for URL shortening.
 *
 * - Uses SecureRandom (not Random) — SecureRandom is cryptographically strong,
 *   meaning it's much harder to predict the next code. This prevents users from
 *   guessing short codes to access private URLs.
 *
 * - 7 characters from 62-character alphabet → 62^7 ≈ 3.5 trillion combinations.
 *   At 1,000 new URLs/day, collision probability stays negligible for centuries.
 *
 * - This is a static utility class — no state, no Spring beans, easy to unit test.
 */
public final class ShortCodeGenerator {

    // 62 characters: a-z, A-Z, 0-9
    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int CODE_LENGTH = 7;

    // SecureRandom is thread-safe and expensive to initialise — create once and reuse
    private static final SecureRandom RANDOM = new SecureRandom();

    // Private constructor — this class should never be instantiated
    private ShortCodeGenerator() {}

    /**
     * Generates a random 7-character alphanumeric short code.
     * Example output: "aB3xK7q"
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}