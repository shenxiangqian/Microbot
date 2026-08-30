/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client.rs;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class JagexAccountStorage
{
	private static final String CACHE_DATA_FILE = "main_file_cache.dat2";
	private static final String CACHE_INDEX_PREFIX = "main_file_cache.idx";
	private static final int LAST_STANDARD_CACHE_INDEX = 24;
	private static final int META_CACHE_INDEX = 255;

	private JagexAccountStorage()
	{
	}

	public static Path prepare(
		Path sharedRuneLiteDirectory,
		@Nullable File accountsRoot,
		@Nullable String characterId) throws IOException
	{
		Path normalizedSharedDirectory = sharedRuneLiteDirectory.toAbsolutePath().normalize();
		if (accountsRoot == null)
		{
			Files.createDirectories(normalizedSharedDirectory);
			return normalizedSharedDirectory;
		}

		validateCharacterId(characterId);
		Path normalizedAccountsRoot = accountsRoot.toPath().toAbsolutePath().normalize();
		if (normalizedSharedDirectory.startsWith(normalizedAccountsRoot))
		{
			throw new IllegalArgumentException(
				"--accounts-root cannot contain the shared RuneLite directory; remove the account-specific -Duser.home argument");
		}

		Path accountRuneLiteDirectory = normalizedAccountsRoot
			.resolve(characterId)
			.resolve(".runelite")
			.normalize();
		if (!accountRuneLiteDirectory.startsWith(normalizedAccountsRoot))
		{
			throw new IllegalArgumentException("Invalid character ID for account storage");
		}

		Path sharedCacheDirectory = getLiveCacheDirectory(normalizedSharedDirectory);
		Path accountCacheDirectory = getLiveCacheDirectory(accountRuneLiteDirectory);
		Files.createDirectories(sharedCacheDirectory);
		Files.createDirectories(accountCacheDirectory);

		Path lockPath = normalizedSharedDirectory
			.resolve("jagexcache")
			.resolve(".microbot-cache-links.lock");
		try (FileChannel lockChannel = FileChannel.open(
			lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock ignored = lockChannel.lock())
		{
			int cacheFileCount = synchronizeCacheFiles(sharedCacheDirectory, accountCacheDirectory);
			log.info("Prepared isolated Jagex account storage with {} shared cache files", cacheFileCount);
		}

		return accountRuneLiteDirectory;
	}

	private static void validateCharacterId(@Nullable String characterId)
	{
		if (characterId == null || characterId.isEmpty())
		{
			throw new IllegalArgumentException("--accounts-root requires --character-id");
		}
		for (int i = 0; i < characterId.length(); i++)
		{
			if (!Character.isDigit(characterId.charAt(i)))
			{
				throw new IllegalArgumentException("--character-id must be numeric when --accounts-root is used");
			}
		}
	}

	private static Path getLiveCacheDirectory(Path runeLiteDirectory)
	{
		return runeLiteDirectory.resolve("jagexcache").resolve("oldschool").resolve("LIVE");
	}

	private static int synchronizeCacheFiles(Path sharedCacheDirectory, Path accountCacheDirectory) throws IOException
	{
		Set<String> cacheFileNames = new TreeSet<>();
		cacheFileNames.add(CACHE_DATA_FILE);
		for (int index = 0; index <= LAST_STANDARD_CACHE_INDEX; index++)
		{
			cacheFileNames.add(CACHE_INDEX_PREFIX + index);
		}
		cacheFileNames.add(CACHE_INDEX_PREFIX + META_CACHE_INDEX);
		collectCacheFileNames(sharedCacheDirectory, cacheFileNames);
		collectCacheFileNames(accountCacheDirectory, cacheFileNames);

		for (String cacheFileName : cacheFileNames)
		{
			synchronizeCacheFile(
				sharedCacheDirectory.resolve(cacheFileName),
				accountCacheDirectory.resolve(cacheFileName));
		}
		return cacheFileNames.size();
	}

	private static void collectCacheFileNames(Path cacheDirectory, Set<String> cacheFileNames) throws IOException
	{
		try (Stream<Path> files = Files.list(cacheDirectory))
		{
			files.map(path -> path.getFileName().toString())
				.filter(JagexAccountStorage::isCacheDataFile)
				.forEach(cacheFileNames::add);
		}
	}

	private static boolean isCacheDataFile(String fileName)
	{
		if (CACHE_DATA_FILE.equals(fileName))
		{
			return true;
		}
		if (!fileName.startsWith(CACHE_INDEX_PREFIX))
		{
			return false;
		}

		String index = fileName.substring(CACHE_INDEX_PREFIX.length());
		if (index.isEmpty())
		{
			return false;
		}
		for (int i = 0; i < index.length(); i++)
		{
			if (!Character.isDigit(index.charAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	private static void synchronizeCacheFile(Path sharedCacheFile, Path accountCacheFile) throws IOException
	{
		boolean sharedExists = Files.exists(sharedCacheFile, LinkOption.NOFOLLOW_LINKS);
		boolean accountExists = Files.exists(accountCacheFile, LinkOption.NOFOLLOW_LINKS);
		try
		{
			if (!sharedExists && accountExists)
			{
				validateCacheFile(accountCacheFile);
				Files.createLink(sharedCacheFile, accountCacheFile);
				sharedExists = true;
			}
			else if (!sharedExists)
			{
				Files.createFile(sharedCacheFile);
				sharedExists = true;
			}

			validateCacheFile(sharedCacheFile);
			if (accountExists && Files.isSameFile(sharedCacheFile, accountCacheFile))
			{
				return;
			}

			if (accountExists)
			{
				validateCacheFile(accountCacheFile);
				replaceWithHardLink(accountCacheFile, sharedCacheFile);
			}
			else if (sharedExists)
			{
				Files.createLink(accountCacheFile, sharedCacheFile);
			}
		}
		catch (UnsupportedOperationException | IOException ex)
		{
			throw new IOException(
				"Unable to share the Jagex cache. The shared RuneLite directory and accounts root must support hard links on the same volume.",
				ex);
		}
	}

	private static void validateCacheFile(Path cacheFile) throws IOException
	{
		if (!Files.isRegularFile(cacheFile))
		{
			throw new IOException("Jagex cache entry is not a regular file");
		}
	}

	private static void replaceWithHardLink(Path link, Path target) throws IOException
	{
		Path temporaryLink = link.resolveSibling(
			"." + link.getFileName() + ".microbot-link-" + UUID.randomUUID());
		try
		{
			Files.createLink(temporaryLink, target);
			try
			{
				Files.move(temporaryLink, link,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				Files.move(temporaryLink, link, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(temporaryLink);
		}
	}
}
