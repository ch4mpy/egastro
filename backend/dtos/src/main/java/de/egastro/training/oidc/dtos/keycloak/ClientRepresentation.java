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
		Map<String, String> authenticationFlowBindingOverrides,
		List<ProtocolMapperRepresentation> protocolMappers) {

	public ClientRepresentation(
			String clientId,
			String secret,
			List<String> redirectUris,
			List<String> webOrigins,
			List<String> postLogoutRedirectUris,
			String loginTheme,
			String magicLinkFlowId,
			Map<String, Object> grantsMapperConf) {
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
				Map.of("browser", magicLinkFlowId),
				List.of(new ProtocolMapperRepresentation("User grants per restaurant", "openid-connect", "egastro.de", false, grantsMapperConf)));
	}

	record Attributes(@JsonProperty("login_theme") String loginTheme, @JsonProperty("post.logout.redirect.uris") String postLogoutRedirectUris) {
		public Attributes(String loginTheme, List<String> postLogoutRedirectUris) {
			this(loginTheme, postLogoutRedirectUris.stream().collect(Collectors.joining("##")));
		}
	}
}
