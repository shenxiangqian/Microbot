package net.runelite.client.plugins.microbot;

/**
 * Thrown when a script thread is interrupted while it waits.
 *
 * <p>The interrupt is the stop signal of the framework: a script that was stopped has to leave its
 * loop. A wait that swallowed the interrupt hid the stop from the script, so the script kept
 * running - and a busy loop over such a wait spun without ever waiting again.</p>
 *
 * <p>The {@link InterruptedException} is kept as the cause, so the code that already recognises an
 * interruption keeps recognising it.</p>
 */
public class ScriptInterruptedException extends RuntimeException
{
    public ScriptInterruptedException(InterruptedException cause)
    {
        super(cause);
    }
}
