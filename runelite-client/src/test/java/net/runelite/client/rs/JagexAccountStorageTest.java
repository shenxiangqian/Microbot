/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client.rs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JagexAccountStorageTest
{
	private static final String CHARACTER_ID = "344492934";

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testNoAccountsRootUsesSharedRuneLiteDirectory() throws Exception
	{
		Path sharedRuneLiteDirectory = temporaryFolder.getRoot().toPath().resolve("new-shared");

		assertEquals(
			sharedRuneLiteDirectory.toAbsolutePath().normalize(),
			JagexAccountStorage.prepare(sharedRuneLiteDirectory, null, null));
		assertTrue(Files.isDirectory(sharedRuneLiteDirectory));
	}

	@Test
	public void testSharesCacheButKeepsPreferencesIsolated() throws Exception
	{
		Path sharedRuneLiteDirectory = temporaryFolder.newFolder("shared").toPath();
		Path accountsRoot = temporaryFolder.newFolder("accounts").toPath();
		Path sharedLiveCache = liveCache(sharedRuneLiteDirectory);
		Files.createDirectories(sharedLiveCache);
		Path sharedDataFile = sharedLiveCache.resolve("main_file_cache.dat2");
		Files.write(sharedDataFile, new byte[]{1, 2, 3});
		Files.write(sharedLiveCache.resolve("preferences.dat"), new byte[]{9});
		Path existingAccountPreferences = liveCache(accountsRoot.resolve(CHARACTER_ID).resolve(".runelite"))
			.resolve("preferences.dat");
		Files.createDirectories(existingAccountPreferences.getParent());
		byte[] accountPreferences = new byte[]{4, 5, 6};
		Files.write(existingAccountPreferences, accountPreferences);

		Path accountRuneLiteDirectory = JagexAccountStorage.prepare(
			sharedRuneLiteDirectory, accountsRoot.toFile(), CHARACTER_ID);
		Path accountLiveCache = liveCache(accountRuneLiteDirectory);

		assertEquals(
			accountsRoot.resolve(CHARACTER_ID).resolve(".runelite").toAbsolutePath().normalize(),
			accountRuneLiteDirectory);
		assertTrue(Files.isSameFile(sharedDataFile, accountLiveCache.resolve("main_file_cache.dat2")));
		assertArrayEquals(accountPreferences, Files.readAllBytes(accountLiveCache.resolve("preferences.dat")));
		assertFalse(Files.isSameFile(
			sharedLiveCache.resolve("preferences.dat"), accountLiveCache.resolve("preferences.dat")));
		assertFalse(Files.exists(accountLiveCache.resolve("preferences2.dat")));
		assertFalse(Files.exists(accountRuneLiteDirectory.resolve("random.dat")));
	}

	@Test
	public void testPromotesExistingAccountCacheToSharedCache() throws Exception
	{
		Path sharedRuneLiteDirectory = temporaryFolder.newFolder("shared").toPath();
		Path accountsRoot = temporaryFolder.newFolder("accounts").toPath();
		Path accountCacheFile = liveCache(accountsRoot.resolve(CHARACTER_ID).resolve(".runelite"))
			.resolve("main_file_cache.idx25");
		Files.createDirectories(accountCacheFile.getParent());
		byte[] cacheData = "existing-cache".getBytes(StandardCharsets.UTF_8);
		Files.write(accountCacheFile, cacheData);

		JagexAccountStorage.prepare(sharedRuneLiteDirectory, accountsRoot.toFile(), CHARACTER_ID);

		Path sharedCacheFile = liveCache(sharedRuneLiteDirectory).resolve("main_file_cache.idx25");
		assertTrue(Files.isSameFile(sharedCacheFile, accountCacheFile));
		assertArrayEquals(cacheData, Files.readAllBytes(sharedCacheFile));
	}

	@Test
	public void testSharedCacheReplacesDuplicateAccountCache() throws Exception
	{
		Path sharedRuneLiteDirectory = temporaryFolder.newFolder("shared").toPath();
		Path accountsRoot = temporaryFolder.newFolder("accounts").toPath();
		Path sharedCacheFile = liveCache(sharedRuneLiteDirectory).resolve("main_file_cache.idx0");
		Path accountCacheFile = liveCache(accountsRoot.resolve(CHARACTER_ID).resolve(".runelite"))
			.resolve("main_file_cache.idx0");
		Files.createDirectories(sharedCacheFile.getParent());
		Files.createDirectories(accountCacheFile.getParent());
		byte[] sharedCacheData = "shared-cache".getBytes(StandardCharsets.UTF_8);
		Files.write(sharedCacheFile, sharedCacheData);
		Files.write(accountCacheFile, "duplicate-cache".getBytes(StandardCharsets.UTF_8));

		JagexAccountStorage.prepare(sharedRuneLiteDirectory, accountsRoot.toFile(), CHARACTER_ID);

		assertTrue(Files.isSameFile(sharedCacheFile, accountCacheFile));
		assertArrayEquals(sharedCacheData, Files.readAllBytes(accountCacheFile));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsNonNumericCharacterId() throws Exception
	{
		Path sharedRuneLiteDirectory = temporaryFolder.newFolder("shared").toPath();
		Path accountsRoot = temporaryFolder.newFolder("accounts").toPath();

		JagexAccountStorage.prepare(sharedRuneLiteDirectory, accountsRoot.toFile(), "../other-account");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsAccountSpecificUserHome() throws Exception
	{
		Path accountsRoot = temporaryFolder.newFolder("accounts").toPath();
		Path sharedRuneLiteDirectory = accountsRoot.resolve(CHARACTER_ID).resolve(".runelite");

		JagexAccountStorage.prepare(sharedRuneLiteDirectory, accountsRoot.toFile(), CHARACTER_ID);
	}

	private static Path liveCache(Path runeLiteDirectory)
	{
		return runeLiteDirectory.resolve("jagexcache").resolve("oldschool").resolve("LIVE");
	}
}
