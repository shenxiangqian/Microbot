package net.runelite.client.plugins.microbot.util.security.login;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the three obfuscated static fields rendered as the login response.
 * Field names are isolated in a versioned resource so client updates do not
 * require changing the classifier or its public API.
 */
@Slf4j
public final class LoginResponseReflectionReader
{
	private static final String RESOURCE_NAME = "login-response-fields.properties";

	private final Mapping mapping;
	private final AtomicBoolean failureLogged = new AtomicBoolean();
	private volatile ResolvedFields resolvedFields;

	public LoginResponseReflectionReader()
	{
		this(Mapping.load());
	}

	LoginResponseReflectionReader(Mapping mapping)
	{
		this.mapping = mapping;
	}

	ReadResult readOnClientThread(Client client)
	{
		if (client == null || mapping == null)
		{
			return ReadResult.unavailable(getMappingVersion());
		}
		return readFromClassLoaderOnClientThread(client.getClass().getClassLoader());
	}

	ReadResult readFromClassLoaderOnClientThread(ClassLoader classLoader)
	{
		if (classLoader == null || mapping == null)
		{
			return ReadResult.unavailable(getMappingVersion());
		}
		ResolvedFields fields = resolve(classLoader);
		if (!fields.available)
		{
			return ReadResult.unavailable(getMappingVersion());
		}

		try
		{
			return new ReadResult(
				Arrays.asList(
					(String) fields.line1.get(null),
					(String) fields.line2.get(null),
					(String) fields.line3.get(null)),
				true,
				getMappingVersion());
		}
		catch (IllegalAccessException | RuntimeException e)
		{
			logFailureOnce("mapped fields could not be read", e);
			return ReadResult.unavailable(getMappingVersion());
		}
	}

	String getMappingVersion()
	{
		return mapping == null ? "unavailable" : mapping.injectedClientVersion;
	}

	private ResolvedFields resolve(ClassLoader classLoader)
	{
		ResolvedFields cached = resolvedFields;
		if (cached != null && cached.classLoader == classLoader)
		{
			return cached;
		}

		synchronized (this)
		{
			cached = resolvedFields;
			if (cached != null && cached.classLoader == classLoader)
			{
				return cached;
			}

			try
			{
				Class<?> owner = Class.forName(mapping.owner, false, classLoader);
				Field line1 = resolveStringField(owner, mapping.line1);
				Field line2 = resolveStringField(owner, mapping.line2);
				Field line3 = resolveStringField(owner, mapping.line3);
				cached = new ResolvedFields(classLoader, line1, line2, line3, true);
			}
			catch (ClassNotFoundException | NoSuchFieldException | RuntimeException e)
			{
				logFailureOnce("mapping no longer matches the injected client", e);
				cached = ResolvedFields.unavailable(classLoader);
			}
			resolvedFields = cached;
			return cached;
		}
	}

	private static Field resolveStringField(Class<?> owner, String name) throws NoSuchFieldException
	{
		Field field = owner.getDeclaredField(name);
		if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers()))
		{
			throw new IllegalStateException(owner.getName() + "#" + name + " is not a static String");
		}
		field.setAccessible(true);
		return field;
	}

	private void logFailureOnce(String reason, Exception e)
	{
		if (failureLogged.compareAndSet(false, true))
		{
			log.warn("Login response reflection disabled for mapping {}: {} ({})",
				getMappingVersion(), reason, e.getClass().getSimpleName());
		}
	}

	@Value
	static class ReadResult
	{
		List<String> lines;
		boolean available;
		String mappingVersion;

		static ReadResult unavailable(String mappingVersion)
		{
			return new ReadResult(Collections.emptyList(), false, mappingVersion);
		}
	}

	@Value
	static class Mapping
	{
		String injectedClientVersion;
		String owner;
		String line1;
		String line2;
		String line3;

		static Mapping load()
		{
			try (InputStream in = LoginResponseReflectionReader.class.getResourceAsStream(RESOURCE_NAME))
			{
				if (in == null)
				{
					return null;
				}
				Properties properties = new Properties();
				properties.load(in);
				return new Mapping(
					require(properties, "injectedClientVersion"),
					require(properties, "owner"),
					require(properties, "line1"),
					require(properties, "line2"),
					require(properties, "line3"));
			}
			catch (IOException | IllegalArgumentException e)
			{
				return null;
			}
		}

		private static String require(Properties properties, String key)
		{
			String value = properties.getProperty(key);
			if (value == null || value.isBlank())
			{
				throw new IllegalArgumentException("Missing property " + key);
			}
			return value.trim();
		}
	}

	private static final class ResolvedFields
	{
		private final ClassLoader classLoader;
		private final Field line1;
		private final Field line2;
		private final Field line3;
		private final boolean available;

		private ResolvedFields(ClassLoader classLoader, Field line1, Field line2, Field line3, boolean available)
		{
			this.classLoader = classLoader;
			this.line1 = line1;
			this.line2 = line2;
			this.line3 = line3;
			this.available = available;
		}

		private static ResolvedFields unavailable(ClassLoader classLoader)
		{
			return new ResolvedFields(classLoader, null, null, null, false);
		}
	}
}
