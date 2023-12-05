package de.egastro.training.oidc.dtos.keycloak;

public record RealmRepresentation(
		String id,
		String realm,
		Integer accessTokenLifespan,
		Boolean enabled,
		String sslRequired,
		Boolean bruteForceProtected,
		String loginTheme,
		Boolean eventsEnabled,
		Boolean adminEventsEnabled,
		Boolean duplicateEmailsAllowed,
		Boolean editUsernameAllowed,
		Boolean loginWithEmailAllowed,
		Boolean passwordCredentialGrantAllowed,
		Boolean resetPasswordAllowed,
		Boolean verifyEmail) {

}
