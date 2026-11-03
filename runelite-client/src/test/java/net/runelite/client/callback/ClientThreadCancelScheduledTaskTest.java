package net.runelite.client.callback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the cancellation of the pending client thread task. A script that stops while a path
 * calculation is still queued has to end that calculation, otherwise the character keeps moving
 * long after the script was stopped.
 */
public class ClientThreadCancelScheduledTaskTest
{
	private ClientThread clientThread;

	@Before
	public void setUp()
	{
		Client client = mock(Client.class);
		clientThread = new ClientThread();
		try
		{
			java.lang.reflect.Field field = ClientThread.class.getDeclaredField("client");
			field.setAccessible(true);
			field.set(clientThread, client);
		}
		catch (ReflectiveOperationException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	@Test
	public void unownedTaskIsCancelledByAnyScript() throws Exception
	{
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean interrupted = new AtomicBoolean();
		clientThread.runOnSeperateThread(() ->
		{
			started.countDown();
			try
			{
				Thread.sleep(60_000L);
			}
			catch (InterruptedException ex)
			{
				interrupted.set(true);
				Thread.currentThread().interrupt();
			}
			return null;
		});

		assertTrue(started.await(5, TimeUnit.SECONDS));
		assertTrue("the pending client thread task has to be cancelled",
				clientThread.cancelScheduledTask(new Object()));
		assertTrue("the cancelled task has to be interrupted", await(interrupted));
	}

	@Test
	public void taskOfAnotherScriptIsNotCancelled() throws Exception
	{
		Object owner = new Object();
		clientThread.runOnSeperateThread(() ->
		{
			Thread.sleep(60_000L);
			return null;
		}, owner);

		assertFalse("a task that belongs to another script must not be cancelled",
				clientThread.cancelScheduledTask(new Object()));
		assertTrue("the owner of the task has to be able to cancel it",
				clientThread.cancelScheduledTask(owner));
	}

	private static boolean await(AtomicBoolean flag) throws InterruptedException
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!flag.get() && System.nanoTime() < deadline)
		{
			Thread.sleep(10);
		}
		return flag.get();
	}
}
