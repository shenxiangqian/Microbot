package net.runelite.client.plugins.microbot.walkerfst;

import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportExecutionRegistryFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.TransportPlanningPolicyFst;

/** Microbot executor capabilities projected into the local planner's catalog-admission seam. */
public final class Rs2TransportPlanningPolicyFst implements TransportPlanningPolicyFst
{
	public static final Rs2TransportPlanningPolicyFst INSTANCE = new Rs2TransportPlanningPolicyFst();

	private Rs2TransportPlanningPolicyFst()
	{
	}

	@Override
	public boolean isAdmitted(TransportFst transport)
	{
		return TransportExecutionRegistryFst.canExecute(transport);
	}

	@Override
	public boolean isZeroRuneSpell(TransportFst transport)
	{
		return transport != null
			&& TransportExecutionRegistryFst.homeTeleportFor(transport.getDisplayInfo()).isPresent();
	}
}
