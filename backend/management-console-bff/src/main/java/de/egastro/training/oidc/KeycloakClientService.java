package de.egastro.training.oidc;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import de.egastro.training.oidc.dtos.keycloak.ClientRepresentation;
import de.egastro.training.oidc.dtos.keycloak.CredentialRepresentation;
import reactor.core.publisher.Mono;

/**
 * Uses a REST client to call Keycloak admin API
 */
@Service
public class KeycloakClientService {
	private final WebClient client;
	private final EgastroOAuth2ClientConfigurationProperties conf;
	private final URI oauth2ClientsAdminUri;

	public KeycloakClientService(
			EgastroOAuth2ClientConfigurationProperties conf,
			ReactiveClientRegistrationRepository clientRegistrations,
			ReactiveOAuth2AuthorizedClientService authorizedClientService) {
		super();
		AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
				clientRegistrations,
				authorizedClientService);
		ServerOAuth2AuthorizedClientExchangeFilterFunction oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
		oauth.setDefaultClientRegistrationId(conf.getWebClientRegistration());
		this.client = WebClient.builder().filter(oauth).build();
		this.conf = conf;
		this.oauth2ClientsAdminUri = UriComponentsBuilder.fromUri(conf.getAdminApiUri()).path("/realms/%s/clients".formatted(conf.getRealm())).build().toUri();
	}

	/**
	 * @param  clientId
	 * @param  secret
	 * @param  loginTheme
	 * @return            the client internal ID to use when building URIs
	 */
	public Mono<String> createClient(String clientId, String secret, String loginTheme) {
		final var dto = new ClientRepresentation(
				clientId,
				secret,
				conf.getRedirectUris(),
				conf.getWebOrigins(),
				conf.getPostLogoutRedirectUris(),
				loginTheme,
				conf.getMagicLinkFlowId(),
				// @formatter:off
				Map.of(
						"restaurants-employees-client.client-id", conf.getUserGrantsMapperConf().getClientId(),
						"restaurants-employees-client.token-endpoint-uri",  conf.getUserGrantsMapperConf().getTokenEndpoint(),
						"restaurants-employees-api.base-uri",  conf.getUserGrantsMapperConf().getApiBaseUri(),
						"restaurants-employees-client.client-secret",  conf.getUserGrantsMapperConf().getClientSecret())
				// @formatter:on
		);

		return client
				.post()
				.uri(oauth2ClientsAdminUri)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(dto)
				.retrieve()
				.toEntity(Void.class)
				.map(
						response -> Optional
								.ofNullable(response.getHeaders().getLocation())
								.map(URI::toString)
								.map(uri -> uri.split("/"))
								.map(fragments -> fragments.length > 0 ? fragments[fragments.length - 1] : null)
								.orElse(null));
	}

	public Mono<String> getClientSecret(String id) {
		final var clientSecretUri = UriComponentsBuilder.fromUri(oauth2ClientsAdminUri).path("/%s/client-secret".formatted(id)).build().toUri();
		return client.get().uri(clientSecretUri).retrieve().bodyToMono(CredentialRepresentation.class).map(CredentialRepresentation::value);
	}

	public Mono<Void> deleteClient(String id) {
		final var clientUri = UriComponentsBuilder.fromUri(oauth2ClientsAdminUri).path("/%s".formatted(id)).build().toUri();
		return client.delete().uri(clientUri).retrieve().bodyToMono(Void.class);
	}
}
