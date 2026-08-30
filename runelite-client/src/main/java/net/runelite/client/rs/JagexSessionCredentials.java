/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client.rs;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import javax.annotation.Nullable;

public final class JagexSessionCredentials implements AutoCloseable
{
	static final String CREDENTIALS_PATH_PROPERTY = "runelite.credentials.path";
	static final String SESSION_ID_PROPERTY = "JX_SESSION_ID";
	static final String CHARACTER_ID_PROPERTY = "JX_CHARACTER_ID";

	private final Path credentialsFile;
	private final String previousCredentialsPath;

	private JagexSessionCredentials(@Nullable Path credentialsFile, @Nullable String previousCredentialsPath)
	{
		this.credentialsFile = credentialsFile;
		this.previousCredentialsPath = previousCredentialsPath;
	}

	public static JagexSessionCredentials install(
		Path credentialsDirectory,
		@Nullable String sessionId,
		@Nullable String characterId) throws IOException
	{
		boolean hasSessionId = !Strings.isNullOrEmpty(sessionId);
		boolean hasCharacterId = !Strings.isNullOrEmpty(characterId);
		if (!hasSessionId && !hasCharacterId)
		{
			return new JagexSessionCredentials(null, null);
		}
		if (!hasSessionId || !hasCharacterId)
		{
			throw new IllegalArgumentException("Session ID and character ID must be provided together");
		}

		Files.createDirectories(credentialsDirectory);
		Path credentialsFile = Files.createTempFile(
			credentialsDirectory, "microbot-jx-credentials-", ".properties");
		credentialsFile.toFile().deleteOnExit();
		try
		{
			setOwnerOnlyPermissions(credentialsFile);

			Properties credentials = new Properties();
			credentials.setProperty(SESSION_ID_PROPERTY, sessionId);
			credentials.setProperty(CHARACTER_ID_PROPERTY, characterId);
			try (OutputStream output = Files.newOutputStream(credentialsFile))
			{
				credentials.store(output, null);
			}

			String previousCredentialsPath = System.setProperty(
				CREDENTIALS_PATH_PROPERTY,
				credentialsFile.getFileName().toString());
			return new JagexSessionCredentials(credentialsFile, previousCredentialsPath);
		}
		catch (IOException | RuntimeException ex)
		{
			Files.deleteIfExists(credentialsFile);
			throw ex;
		}
	}

	private static void setOwnerOnlyPermissions(Path credentialsFile) throws IOException
	{
		try
		{
			Files.setPosixFilePermissions(credentialsFile, EnumSet.of(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE));
		}
		catch (UnsupportedOperationException ignored)
		{
			// Windows temp files inherit the current user's ACL from the temp directory.
		}
	}

	@Nullable
	Path getCredentialsFile()
	{
		return credentialsFile;
	}

	@Override
	public void close() throws IOException
	{
		if (credentialsFile == null)
		{
			return;
		}

		String installedPath = credentialsFile.getFileName().toString();
		if (installedPath.equals(System.getProperty(CREDENTIALS_PATH_PROPERTY)))
		{
			if (previousCredentialsPath == null)
			{
				System.clearProperty(CREDENTIALS_PATH_PROPERTY);
			}
			else
			{
				System.setProperty(CREDENTIALS_PATH_PROPERTY, previousCredentialsPath);
			}
		}

		Files.deleteIfExists(credentialsFile);
	}
}
