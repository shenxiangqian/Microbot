package net.runelite.client.plugins.microbot.util.security.login;

import lombok.Value;
import net.runelite.api.GameState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable snapshot of all state used to classify the current login result. */
@Value
public class LoginResponseSnapshot
{
	Rs2LoginStatus status;
	GameState gameState;
	int loginIndex;
	List<String> responseLines;
	Rs2LoginStatusSource source;
	Instant observedAt;
	boolean responseTextAvailable;
	String reflectionMappingVersion;

	public LoginResponseSnapshot(
		Rs2LoginStatus status,
		GameState gameState,
		int loginIndex,
		List<String> responseLines,
		Rs2LoginStatusSource source,
		Instant observedAt,
		boolean responseTextAvailable,
		String reflectionMappingVersion)
	{
		this.status = Objects.requireNonNull(status, "status");
		this.gameState = Objects.requireNonNull(gameState, "gameState");
		this.loginIndex = loginIndex;
		this.responseLines = Collections.unmodifiableList(new ArrayList<>(responseLines));
		this.source = Objects.requireNonNull(source, "source");
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
		this.responseTextAvailable = responseTextAvailable;
		this.reflectionMappingVersion = reflectionMappingVersion == null ? "unknown" : reflectionMappingVersion;
	}

	public boolean hasResponseText()
	{
		return responseLines.stream().anyMatch(line -> line != null && !line.isBlank());
	}

	public String getResponseText()
	{
		return responseLines.stream()
			.filter(line -> line != null && !line.isBlank())
			.collect(Collectors.joining(" "));
	}
}
