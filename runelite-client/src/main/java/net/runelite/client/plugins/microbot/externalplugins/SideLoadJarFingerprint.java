package net.runelite.client.plugins.microbot.externalplugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fingerprint of a side-loaded script JAR as it exists on disk.
 *
 * <p>Used to tell whether the file was replaced since the JAR was loaded. The SHA-256 is the
 * authoritative signal; length and last-modified are kept as cheap pre-checks and as a fallback
 * for the case where the file cannot be hashed.</p>
 */
final class SideLoadJarFingerprint {
    private static final int BUFFER_SIZE = 8192;

    private final long length;
    private final long lastModified;
    private final String sha256;

    private SideLoadJarFingerprint(long length, long lastModified, String sha256) {
        this.length = length;
        this.lastModified = lastModified;
        this.sha256 = sha256;
    }

    /**
     * Captures the current state of {@code jar}.
     *
     * @param jar the JAR file on disk
     * @return the fingerprint, or {@code null} when the file is missing or unreadable
     */
    static SideLoadJarFingerprint of(File jar) {
        if (jar == null || !jar.isFile()) {
            return null;
        }
        return new SideLoadJarFingerprint(jar.length(), jar.lastModified(), sha256Of(jar));
    }

    /**
     * @param jar the JAR file on disk
     * @return {@code true} when the file no longer matches the state this fingerprint was taken from
     */
    boolean differsFrom(File jar) {
        if (jar == null || !jar.isFile()) {
            return true;
        }
        if (jar.length() != length || jar.lastModified() != lastModified) {
            return true;
        }
        if (sha256 == null) {
            return false;
        }
        String current = sha256Of(jar);
        return current != null && !current.equals(sha256);
    }

    private static String sha256Of(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = Files.newInputStream(file.toPath())) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
