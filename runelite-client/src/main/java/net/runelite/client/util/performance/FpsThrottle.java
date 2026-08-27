/*
 * Copyright (c) 2017, Levi <me@levischuck.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.util.performance;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;

/**
 * Independent FPS throttle that runs after each frame is painted via
 * {@link DrawManager#registerEveryFrameListener(Runnable)}.
 * <p>
 * The RS client operates at 50 cycles per second with higher priority than draws.
 * For high-powered machines, drawing is bounded by client cycles (max ~50 FPS);
 * for low-powered machines, the client catches up by running multiple cycles
 * between draws. Enforcing FPS here does not impact the engine's ability to run,
 * including audio, even at 1 FPS.
 * <p>
 * Decoupled from FpsPlugin so the {@code --fps} command-line argument works
 * regardless of whether the user has enabled the FPS control plugin.
 */
@Slf4j
public class FpsThrottle implements Runnable
{
	private static final int SAMPLE_SIZE = 4;

	private final int targetFps;
	private final long targetDelay;

	private long lastMillis;
	private final long[] lastDelays = new long[SAMPLE_SIZE];
	private int lastDelayIndex;
	private long sleepDelay;

	public FpsThrottle(int targetFps)
	{
		this.targetFps = Math.max(1, targetFps);
		this.targetDelay = 1000 / this.targetFps;
		this.sleepDelay = this.targetDelay;
		this.lastMillis = System.currentTimeMillis();
		for (int i = 0; i < SAMPLE_SIZE; i++)
		{
			this.lastDelays[i] = this.targetDelay;
		}
		log.debug("FPS throttle initialized at {} FPS ({}ms per frame)", this.targetFps, this.targetDelay);
	}

	@Override
	public void run()
	{
		// currentTimeMillis is occasionally cached by the JVM, but unlike nanotime
		// its caching will not cause oscillation here because it's granular enough
		final long before = lastMillis;
		final long now = System.currentTimeMillis();

		lastMillis = now;
		lastDelayIndex = (lastDelayIndex + 1) % SAMPLE_SIZE;
		lastDelays[lastDelayIndex] = now - before;

		// Sampling smooths over the case where the engine sometimes repaints after
		// one cycle and sometimes after many cycles.
		long averageDelay = 0;
		for (int i = 0; i < SAMPLE_SIZE; i++)
		{
			averageDelay += lastDelays[i];
		}
		averageDelay /= lastDelays.length;

		if (averageDelay > targetDelay)
		{
			sleepDelay--;
		}
		else if (averageDelay < targetDelay)
		{
			sleepDelay++;
		}

		if (sleepDelay > 0)
		{
			try
			{
				Thread.sleep(sleepDelay);
			}
			catch (InterruptedException e)
			{
				// Can happen on shutdown
			}
		}
	}
}