package de.egastro.restaurants_employees.keycloak_mapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import de.egastro.training.oidc.dtos.users.UserGrantsDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestaurantsEmployeesClient {

	private static final Map<RestaurantsEmployeesClientConfig, RestaurantsEmployeesClient> instances = new HashMap<>();

	private final RestaurantsEmployeesClientConfig config;
	private final RestClient tokenClient;
	private final RestClient usersClient;
	private long expiresAt = 0L;
	private Optional<TokenResponseDto> token = Optional.empty();

	private RestaurantsEmployeesClient(RestaurantsEmployeesClientConfig config) {
		this.config = config;
		this.tokenClient = RestClient.builder().baseUrl(config.tokenEndpointUri()).build();
		this.usersClient = RestClient.builder().baseUrl(config.usersApiBaseUri()).build();
	}

	public Optional<UserGrantsDto> getUserGrants(String realm, String username) {
		try {
			return Optional
					.ofNullable(
							usersClient.get().uri("/{realm}/{username}/grants", realm, username).headers(this::setBearer).retrieve().body(UserGrantsDto.class));
		} catch (final Exception e) {
			log.error("Failed to get UserEmployersDto: {}", e);
			return Optional.empty();
		}
	}

	private HttpHeaders setBearer(HttpHeaders headers) {
		getClientAccessToken().ifPresent(str -> {
			headers.setBearerAuth(str);
		});
		return headers;
	}

	private Optional<String> getClientAccessToken() {
		final var now = Instant.now().getEpochSecond();
		if (expiresAt < now) {
			final var formData = Map.of("scope", "openid profile", "grant_type", "client_credentials");
			try {
				token = Optional.ofNullable(tokenClient.post().headers(headers -> {
					headers.setBasicAuth(config.clientId(), config.clientSecret());
				}).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formData).retrieve().body(TokenResponseDto.class));
				expiresAt = now + token.map(TokenResponseDto::getExpiresIn).orElse(0L);
			} catch (final Exception e) {
				log.error("Failed to get client authorization-token: {}", e);
				token = Optional.empty();
			}
		}
		return token.map(TokenResponseDto::getAccessToken);
	}

	public static RestaurantsEmployeesClient getInstance(RestaurantsEmployeesClientConfig config) {
		return instances.computeIfAbsent(config, c -> {
			return new RestaurantsEmployeesClient(c);
		});
	}
}
