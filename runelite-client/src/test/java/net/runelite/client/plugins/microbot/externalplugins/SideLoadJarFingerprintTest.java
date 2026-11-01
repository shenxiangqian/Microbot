package net.runelite.client.plugins.microbot.externalplugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SideLoadJarFingerprintTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void unchangedFileIsNotDifferent() throws IOException {
        File jar = temporaryFolder.newFile("script.jar");
        Files.write(jar.toPath(), "first build".getBytes(StandardCharsets.UTF_8));

        SideLoadJarFingerprint fingerprint = SideLoadJarFingerprint.of(jar);

        assertNotNull(fingerprint);
        assertFalse(fingerprint.differsFrom(jar));
    }

    @Test
    public void replacedFileIsDifferent() throws IOException {
        File jar = temporaryFolder.newFile("script.jar");
        Files.write(jar.toPath(), "first build".getBytes(StandardCharsets.UTF_8));
        SideLoadJarFingerprint fingerprint = SideLoadJarFingerprint.of(jar);

        Files.write(jar.toPath(), "second build with a different length".getBytes(StandardCharsets.UTF_8));

        assertTrue(fingerprint.differsFrom(jar));
    }

    @Test
    public void missingFileIsDifferent() throws IOException {
        File jar = temporaryFolder.newFile("script.jar");
        Files.write(jar.toPath(), "first build".getBytes(StandardCharsets.UTF_8));
        SideLoadJarFingerprint fingerprint = SideLoadJarFingerprint.of(jar);

        assertTrue(fingerprint.differsFrom(new File(temporaryFolder.getRoot(), "missing.jar")));
        assertTrue(fingerprint.differsFrom(null));
    }

    @Test
    public void missingFileHasNoFingerprint() {
        assertNull(SideLoadJarFingerprint.of(new File(temporaryFolder.getRoot(), "missing.jar")));
        assertNull(SideLoadJarFingerprint.of(null));
    }

    @Test
    public void unknownFingerprintCountsAsChanged() {
        assertTrue(MicrobotPluginManager.hasJarChanged(null, new File(temporaryFolder.getRoot(), "missing.jar")));
    }

    @Test
    public void knownFingerprintCountsAsUnchanged() throws IOException {
        File jar = temporaryFolder.newFile("script.jar");
        Files.write(jar.toPath(), "first build".getBytes(StandardCharsets.UTF_8));
        SideLoadJarFingerprint fingerprint = SideLoadJarFingerprint.of(jar);

        assertFalse(MicrobotPluginManager.hasJarChanged(fingerprint, jar));

        Files.write(jar.toPath(), "second build with a different length".getBytes(StandardCharsets.UTF_8));

        assertTrue(MicrobotPluginManager.hasJarChanged(fingerprint, jar));
    }
}
