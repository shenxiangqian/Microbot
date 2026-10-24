package com.microbot.hwid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

public final class HwidAgent
{
	private static final Logger log = Logger.getLogger(HwidAgent.class.getName());

	public static final String TOGGLE_PROPERTY = "microbot.hwid.spoof";
	public static final String AGENT_PROPERTY = "microbot.hwid.agent";
	public static final String DEVICE_HOOK_PROPERTY = "microbot.hwid.device.installed";
	public static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";

	private static volatile boolean deviceHookInstalled;
	private static volatile byte[] injectedRandomDat;
	private static volatile byte[] injectedSnapshot;
	private static volatile String injectedFor;
	private static File credentialsFile;
	private static boolean credentialsRead;
	private static String credentialsCharacterId;

	private HwidAgent()
	{
	}

	public static void premain(String args, Instrumentation inst)
	{
		try
		{
			inst.addTransformer(new PlatformInfoTransformer());
			inst.addTransformer(new RandomDatTransformer());
			inst.addTransformer(new RandomDatWriteTransformer());
			System.setProperty(AGENT_PROPERTY, "loaded");
			log.warning("HwidAgent传入参数："+args);
			launcherCharacterId(parseAgentArgCharacterId(args));   // 改这一行,加个解析
		}
		catch (Throwable t)
		{
			log.severe("hwid-agent failed to register transformer: " + t);
		}
	}

	static void markDeviceHookInstalled()
	{
		deviceHookInstalled = true;
		System.setProperty(DEVICE_HOOK_PROPERTY, "true");
	}

	public static boolean isDeviceHookInstalled()
	{
		return deviceHookInstalled;
	}

	public static String deviceId(int osType)
	{
		try
		{
			if (!spoofEnabled())
			{
				return VanillaDeviceId.getDeviceId(osType);
			}
			var deviceId = DeviceIdStore.getOrCreate(currentIdentifier());
			log.warning("设备ID："+deviceId);
			return deviceId;
		}
		catch (Throwable t)
		{
			log.warning("hwid-agent device id fallback: " + t);
			return VanillaDeviceId.getDeviceId(osType);
		}
	}

	public static boolean spoofEnabled()
	{
		return Boolean.parseBoolean(System.getProperty(TOGGLE_PROPERTY, "true"));
	}

	public static Object randomDatFile(Object handle)
	{
		return spoofEnabled() ? null : handle;
	}

	public static synchronized byte[] randomDatField(byte[] current)
	{
		try
		{
			System.setProperty(RANDOM_DAT_CALL_PROPERTY, Long.toString(System.currentTimeMillis()));
			if (current != null && current != injectedRandomDat)
			{
				return current;
			}
			if (current != null && !Arrays.equals(current, injectedSnapshot))
			{
				RandomDatStore.put(injectedFor, current);
				injectedSnapshot = current.clone();
			}
			if (!spoofEnabled())
			{
				injectedRandomDat = null;
				injectedSnapshot = null;
				injectedFor = null;
				return null;
			}
			String identifier = currentIdentifier();
			byte[] data = RandomDatStore.getOrCreate(identifier);
			injectedFor = identifier;
			if (current != null && Arrays.equals(current, data))
			{
				return current;
			}
			byte[] injected = data.clone();
			injectedSnapshot = data.clone();
			injectedRandomDat = injected;
			return injected;
		}
		catch (Throwable t)
		{
			System.clearProperty(RANDOM_DAT_CALL_PROPERTY);
			log.warning("hwid-agent random.dat fallback: " + t);
			return current;
		}
	}

	static String currentIdentifier() {
		String username = vanillaUsername();
		if (username != null && !username.isBlank()) {
			return "user/" + username.trim();
		}

		String jxCharacterId = System.getenv("JX_CHARACTER_ID");
		if (jxCharacterId == null || jxCharacterId.isBlank()) {
			jxCharacterId = launcherCharacterId(null);
		}
		if (jxCharacterId != null && !jxCharacterId.isBlank()) {
			log.warning("调用了currentIdentifier()，值是：" + jxCharacterId);
			return "jx/" + jxCharacterId.trim();
		}

		return "default";
	}

	/**
	 * 获取characterId,在原方法的基础上添加了参数传入，如果传入了参数就直接返回传入的参数
	 * @param characterId 角色ID
	 * @return 角色ID
	 */
	private static synchronized String launcherCharacterId(String characterId) {
		if (!credentialsRead) {
			credentialsRead = true;
			if (characterId != null && !characterId.isBlank()) {
				log.warning("传入了characterId:"+characterId);
				credentialsCharacterId = characterId.trim();
			} else {
				credentialsCharacterId = readCharacterId(credentialsFile != null
						? credentialsFile
						: new File(new File(System.getProperty("user.home"), ".runelite"), System.getProperty("runelite.credentials.path", "credentials.properties")));
			}
		}
		return credentialsCharacterId;
	}

	/**
	 * 从启动参数里提取characterId
	 * @param args 启动参数
	 * @return characterId
	 */
	private static String parseAgentArgCharacterId(String args)
	{
		if (args == null || args.isBlank())
		{
			return null;
		}

		String trimmed = args.trim();

		// 没有 key=value 格式，就把整段当成 characterId 本身
		// 对应 -javaagent:hwid-agent-xxx.jar=3333322222111 这种单值写法
		if (!trimmed.contains("="))
		{
			return trimmed;
		}

		// key=value[,key=value...] 格式
		// 对应 -javaagent:hwid-agent-xxx.jar=characterId=3333322222111 这种写法
		for (String pair : trimmed.split(","))
		{
			int idx = pair.indexOf('=');
			if (idx < 0)
			{
				continue;
			}
			String key = pair.substring(0, idx).trim();
			String value = pair.substring(idx + 1).trim();
			if (key.equalsIgnoreCase("characterId") && !value.isBlank())
			{
				return value;
			}
		}

		return null;
	}

	private static String readCharacterId(File file)
	{
		if (!file.isFile())
		{
			return null;
		}
		Properties credentials = new Properties();
		try (InputStream in = Files.newInputStream(file.toPath()))
		{
			credentials.load(in);
		}
		catch (IOException e)
		{
			log.warning("hwid-agent could not read the launcher credentials: " + e);
			return null;
		}
		return credentials.getProperty("JX_CHARACTER_ID");
	}

	static synchronized void setCredentialsFileForTesting(File file)
	{
		credentialsFile = file;
		credentialsRead = false;
		credentialsCharacterId = null;
	}

	static synchronized void resetRandomDatForTesting()
	{
		injectedRandomDat = null;
		injectedSnapshot = null;
		injectedFor = null;
	}

	private static String vanillaUsername()
	{
		try
		{
			Class<?> api = Class.forName("net.runelite.api.Client", false, HwidAgent.class.getClassLoader());
			Class<?> gameclient = Class.forName("client", false, HwidAgent.class.getClassLoader());

			for (java.lang.reflect.Field field : gameclient.getDeclaredFields())
			{
				int modifiers = field.getModifiers();
				if (!java.lang.reflect.Modifier.isStatic(modifiers))
				{
					continue;
				}
				if (java.lang.reflect.Modifier.isFinal(modifiers) || field.getType().isPrimitive())
				{
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value == null || !api.isInstance(value))
				{
					continue;
				}
				Object username = api.getMethod("getUsername").invoke(value);
				return username instanceof String ? (String) username : null;
			}
		}
		catch (Throwable ignored)
		{
		}
		return null;
	}
}
