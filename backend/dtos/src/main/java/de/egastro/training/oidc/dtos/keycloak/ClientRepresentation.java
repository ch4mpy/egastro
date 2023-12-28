package de.egastro.training.oidc.dtos.keycloak;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientRepresentation(
		String clientId,
		String secret,
		List<String> redirectUris,
		List<String> webOrigins,
		boolean enabled,
		String clientAuthenticatorType,
		boolean standardFlowEnabled,
		boolean implicitFlowEnabled,
		boolean directAccessGrantsEnabled,
		boolean serviceAccountsEnabled,
		boolean publicClient,
		Attributes attributes,
		Map<String, String> authenticationFlowBindingOverrides) {

	public ClientRepresentation(
			String clientId,
			String secret,
			List<String> redirectUris,
			List<String> webOrigins,
			List<String> postLogoutRedirectUris,
			String loginTheme) {
		this(
				clientId,
				secret,
				redirectUris,
				webOrigins,
				true,
				"client-secret",
				true,
				false,
				false,
				false,
				false,
				new Attributes(loginTheme, postLogoutRedirectUris),
				Map.of("browser", "ca22d65c-3e53-4d2f-97db-791ec1a8cbc3"));
	}

	record Attributes(@JsonProperty("login_theme") String loginTheme, @JsonProperty("post.logout.redirect.uris") String postLogoutRedirectUris) {
		public Attributes(String loginTheme, List<String> postLogoutRedirectUris) {
			this(loginTheme, postLogoutRedirectUris.stream().collect(Collectors.joining("##")));
		}
	}
}
