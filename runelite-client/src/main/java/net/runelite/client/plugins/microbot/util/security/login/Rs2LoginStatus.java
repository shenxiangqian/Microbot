package net.runelite.client.plugins.microbot.util.security.login;

import lombok.Getter;

/**
 * Detailed result of a login attempt. The severity values mirror the external
 * login-response model that originally defined these states.
 */
@Getter
public enum Rs2LoginStatus
{
	ENTER_USERNAME(1, true),
	CONNECTION_TIMED_OUT(1, true),
	ERROR_CONNECTING(1, true),
	FAILED_TO_LOGIN(1, true),
	NO_RESPONSE(1, true),
	NOT_LOGGED_OUT(1, true),
	STILL_LOGGED_IN(1, true),
	INVALID_LOGIN(5, true),
	DISABLED(5, true),
	ALREADY_LOGGED_IN(1, true),
	UPDATED(4, true),
	FULL_WORLD(2, true),
	UNABLE_TO_CONNECT(2, true),
	LOGIN_LIMIT_EXCEEDED(2, true),
	BAD_SESSION(4, true),
	PASSWORD_KNOWN(5, true),
	MEMBERS_WORLD(3, true),
	MEMBERS_WORLD_2(3, true),
	FAILED_TO_COMPLETE_LOGIN(2, true),
	SERVER_UPDATED(2, true),
	SERVER_UPDATING(2, true),
	TOO_MANY_ATTEMPTS(2, true),
	TOO_MANY_ATTEMPTS_LAUNCHER(2, true),
	MEMBERS_AREA(3, true),
	ACCOUNT_LOCKED(5, true),
	CLOSED_BETA(5, true),
	INVALID_LOGIN_SERVER(2, true),
	MALFORMED_PACKET(2, true),
	NO_REPLY(2, true),
	ERROR_LOADING_PROFILE(5, true),
	UNEXPECTED_LOGIN_RESPONSE(2, true),
	ADDRESS_BLOCKED(5, true),
	SERVICE_UNAVAILABLE(5, true),
	SET_DISPLAY_NAME(5, true),
	UNSUCCESSFUL_LOGIN(5, true),
	INACCESSIBLE(2, true),
	VOTE(5, true),
	NOT_ELIGIBLE(5, true),
	ENTER_AUTH(5, true),
	BAD_AUTH_CODE(5, true),
	UNEXPECTED_SERVER_RESPONSE(2, true),
	CONNECTING_TO_SERVER(0, false),
	TOTAL_LEVEL(3, true),
	WORLD_LOCKED(2, true),
	LOGIN_SCREEN(0, false),
	LOGGED_IN(0, false),
	SIGNED_OUT(1, true),
	UNKNOWN(0, false);

	private final int severity;
	private final boolean terminal;

	Rs2LoginStatus(int severity, boolean terminal)
	{
		this.severity = severity;
		this.terminal = terminal;
	}
}
