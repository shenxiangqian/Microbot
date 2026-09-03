package net.runelite.client.plugins.microbot.util.security.login;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LoginResponseReflectionReaderTest
{
	@Test
	public void readsAndCachesMappedStaticStringFields()
	{
		LoginResponseReflectionReader reader = new LoginResponseReflectionReader(mapping(
			"first", "second", "third"));
		FakeLoginFields.first = "one";
		FakeLoginFields.second = "two";
		FakeLoginFields.third = "three";

		LoginResponseReflectionReader.ReadResult firstRead =
			reader.readFromClassLoaderOnClientThread(getClass().getClassLoader());
		assertTrue(firstRead.isAvailable());
		assertEquals(Arrays.asList("one", "two", "three"), firstRead.getLines());

		FakeLoginFields.second = "updated";
		LoginResponseReflectionReader.ReadResult secondRead =
			reader.readFromClassLoaderOnClientThread(getClass().getClassLoader());
		assertEquals(Arrays.asList("one", "updated", "three"), secondRead.getLines());
	}

	@Test
	public void unavailableWhenMappingDoesNotResolve()
	{
		LoginResponseReflectionReader reader = new LoginResponseReflectionReader(mapping(
			"first", "missing", "third"));

		LoginResponseReflectionReader.ReadResult result =
			reader.readFromClassLoaderOnClientThread(getClass().getClassLoader());

		assertFalse(result.isAvailable());
		assertTrue(result.getLines().isEmpty());
	}

	@Test
	public void bundledMappingTargetsCurrentInjectedClientVersion()
	{
		LoginResponseReflectionReader.Mapping mapping = LoginResponseReflectionReader.Mapping.load();

		assertNotNull(mapping);
		assertEquals(
			System.getProperty("runelite.injected-client.version"),
			mapping.getInjectedClientVersion());
		assertEquals("bf", mapping.getOwner());
		assertEquals("ci", mapping.getLine1());
		assertEquals("cx", mapping.getLine2());
		assertEquals("ce", mapping.getLine3());
	}

	@Test
	public void bundledMappingResolvesAgainstInjectedClientJar() throws ReflectiveOperationException
	{
		LoginResponseReflectionReader.Mapping mapping = LoginResponseReflectionReader.Mapping.load();
		assertNotNull(mapping);

		Class<?> owner = Class.forName(mapping.getOwner(), false, getClass().getClassLoader());
		for (String fieldName : Arrays.asList(mapping.getLine1(), mapping.getLine2(), mapping.getLine3()))
		{
			Field field = owner.getDeclaredField(fieldName);
			assertEquals(String.class, field.getType());
			assertTrue(Modifier.isStatic(field.getModifiers()));
		}
	}

	private static LoginResponseReflectionReader.Mapping mapping(
		String line1,
		String line2,
		String line3)
	{
		return new LoginResponseReflectionReader.Mapping(
			"test",
			FakeLoginFields.class.getName(),
			line1,
			line2,
			line3);
	}

	private static final class FakeLoginFields
	{
		private static String first;
		private static String second;
		private static String third;
	}
}
