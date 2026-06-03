package com.keeply.agent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Sha256HasherTest {

    @TempDir
    Path tempDir;

    @Test
    void hashBytesProducesKnownSha256ForEmptyInput() {
        // SHA-256 of empty byte array is a well-known constant
        String hash = Sha256Hasher.hashBytes(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void hashBytesProducesKnownSha256ForKnownInput() {
        byte[] input = "keeply".getBytes(StandardCharsets.UTF_8);
        String hash = Sha256Hasher.hashBytes(input);
        // Precomputed: echo -n "keeply" | sha256sum
        assertEquals("8951b393b149ba2d20586036a47f06a798f131d7b9d5b4f4c2d714988064944c", hash);
    }

    @Test
    void hashFileMatchesHashBytes() throws Exception {
        byte[] content = "keeply file content for hashing".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(tempDir.resolve("test.dat"), content);

        String fromBytes = Sha256Hasher.hashBytes(content);
        String fromFile = Sha256Hasher.hashFile(file);

        assertEquals(fromBytes, fromFile);
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        String hashA = Sha256Hasher.hashBytes("aaa".getBytes(StandardCharsets.UTF_8));
        String hashB = Sha256Hasher.hashBytes("bbb".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(hashA, hashB);
    }

    @Test
    void hashIsConsistentAcrossMultipleCalls() {
        byte[] data = "idempotente".getBytes(StandardCharsets.UTF_8);
        assertEquals(Sha256Hasher.hashBytes(data), Sha256Hasher.hashBytes(data));
    }
}
