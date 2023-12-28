package de.egastro.training.oidc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;

import de.egastro.training.oidc.dtos.restaurants.AuthorizedPartyCreationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "BFF")
public class BffController {

	private final EgastroOAuth2ClientConfigurationProperties egastroOAuth2ClientConfigurationProperties;
	private final KeycloakClientService clientService;
	private final JpaReactiveClientRegistrationRepository clientRegistrationRepo;
	private final URI ingressHost;

	public BffController(
			OAuth2ClientProperties clientProps,
			SpringAddonsOidcProperties addonsProperties,
			EgastroOAuth2ClientConfigurationProperties egastroOAuth2ClientConfigurationProperties,
			KeycloakClientService clientService,
			JpaReactiveClientRegistrationRepository clientRegistrationRepository,
			@Value("${ingress-host}") URI ingressHost) {
		this.egastroOAuth2ClientConfigurationProperties = egastroOAuth2ClientConfigurationProperties;
		this.clientService = clientService;
		this.clientRegistrationRepo = clientRegistrationRepository;
		this.ingressHost = ingressHost;
	}

	@GetMapping(path = "/login-options", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(operationId = "getLoginOptions")
	public Mono<List<LoginOptionDto>> getLoginOptions(Authentication auth) throws URISyntaxException {
		final boolean isAuthenticated = auth instanceof OAuth2AuthenticationToken;
		return isAuthenticated
				? Mono.just(List.of())
				: clientRegistrationRepo
						.findAllByAuthorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
						.map(
								registrations -> registrations
										.stream()
										.map(
												reg -> new LoginOptionDto(
														reg.getClientId(),
														"%s/oauth2/authorization/%s".formatted(ingressHost, reg.getRegistrationId())))
										.toList());
	}

	@GetMapping(path = "/client-registrations", produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")
	public Mono<List<RelyingPartyDto>> listAuthorizedParties() {
		return clientRegistrationRepo
				.findAllKeys()
				.map(keys -> keys.stream().map(k -> new RelyingPartyDto(k.keycloakId(), k.registrationId(), k.clientId())).toList());
	}

	@PostMapping(path = "/client-registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = false)
	@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")
	public Mono<ResponseEntity<Void>> addAuthorizedParty(@RequestBody @Valid AuthorizedPartyCreationDto dto, Authentication auth) {
		final var providedSecret = Optional.ofNullable(dto.clientSecret()).orElse(egastroOAuth2ClientConfigurationProperties.getDefaultSecret());
		return clientService.createClient(dto.clientId(), dto.clientSecret(), dto.loginTheme()).flatMap(id -> {
			final var secret = StringUtils.hasText(providedSecret) ? Mono.just(providedSecret) : clientService.getClientSecret(id);
			return secret
					.flatMap(
							s -> clientRegistrationRepo
									.addRegistration(
											id,
											egastroOAuth2ClientConfigurationProperties.getProvider(),
											dto.registrationId(),
											dto.clientId(),
											s,
											egastroOAuth2ClientConfigurationProperties.getScopes()));
		}).map(registration -> {
			return ResponseEntity.created(URI.create("/authorized-parties/%s".formatted(dto.registrationId()))).build();
		});
	}

	@DeleteMapping(path = "/client-registrations/{keycloakId}")
	@Transactional(readOnly = false)
	@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")
	public Mono<ResponseEntity<Void>> deleteAuthorizedParty(@PathVariable(name = "keycloakId") String keycloakId, Authentication auth) {

		return clientService
				.deleteClient(keycloakId)
				.then(clientRegistrationRepo.removeRegistrations(keycloakId))
				.then(Mono.just(ResponseEntity.accepted().build()));
	}

	public static record LoginOptionDto(@NotEmpty String label, @NotEmpty String href) {
	}

	static record RelyingPartyDto(String id, String registration, String name) {
	}
}
