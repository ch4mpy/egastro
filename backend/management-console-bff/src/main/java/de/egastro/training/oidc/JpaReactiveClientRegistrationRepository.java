package de.egastro.training.oidc;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import jakarta.persistence.AttributeConverter;
import reactor.core.publisher.Mono;

/**
 * <p>
 * An OAuth2 clients registrations repository which initiates from application properties and maintains in a relational database the registrations dynamically
 * added.
 * </p>
 * <p>
 * The providers used by dynamically added registrations are "static": it must be declared in initial application properties.
 * </p>
 *
 * @see {@link ClientRegistrationEntity} the registration properties saved in database
 * @See {@link ClientRegistrationEntityRepository} the JPA repository used to access the registrations stored in database
 */
public class JpaReactiveClientRegistrationRepository implements ReactiveClientRegistrationRepository {
	private final ClientRegistrationEntityRepository clientRegistrationEntityRepo;
	private final OAuth2ClientProperties oauth2ClientProperties;
	private final Map<String, ClientRegistration> registrationsCache = new ConcurrentHashMap<>();

	public JpaReactiveClientRegistrationRepository(
			ClientRegistrationEntityRepository clientRegistrationEntityRepo,
			OAuth2ClientProperties oauth2ClientProperties) {
		super();
		this.clientRegistrationEntityRepo = clientRegistrationEntityRepo;
		this.oauth2ClientProperties = oauth2ClientProperties;

		// Initialize the registrationsCache with the values from the configuration properties
		final var registrations = new OAuth2ClientPropertiesMapper(oauth2ClientProperties).asClientRegistrations();
		this.registrationsCache.putAll(registrations);
	}

	public Mono<Collection<RelyingPartyKeys>> findAllKeys() {
		final var registrations = registrationsCache.values();
		final var entities = clientRegistrationEntityRepo.findAll();
		return Mono.just(registrations.stream().map(reg -> {
			final var entity = entities.stream().filter(e -> Objects.equals(e.getRegistrationId(), reg.getRegistrationId())).findAny();
			return new RelyingPartyKeys(entity.map(ClientRegistrationEntity::getKeycloakId).orElse(null), reg.getRegistrationId(), reg.getClientId());
		}).toList());
	}

	public Mono<Collection<ClientRegistration>> findAllByAuthorizationGrantType(AuthorizationGrantType grantType) {
		return Mono.just(registrationsCache.values().stream().filter(reg -> Objects.equals(grantType, reg.getAuthorizationGrantType())).toList());
	}

	@Override
	public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
		if (registrationsCache.containsKey(registrationId)) {
			return Mono.just(registrationsCache.get(registrationId));
		}
		return Mono.justOrEmpty(clientRegistrationEntityRepo.findById(registrationId).map(this::toClientRegistration));
	}

	public Mono<ClientRegistration> addRegistration(
			String keycloakId,
			String provider,
			String registrationId,
			String clientId,
			String clientSecret,
			Set<String> scopes) {
		if (!oauth2ClientProperties.getProvider().containsKey(provider)) {
			throw new UnknownProviderException(provider);
		}
		if (registrationsCache.containsKey(registrationId) || clientRegistrationEntityRepo.findById(registrationId).isPresent()) {
			throw new DuplicateRegistrationException(registrationId);
		}
		final var entity = new ClientRegistrationEntity();
		entity.setKeycloakId(keycloakId);
		entity.setProvider(provider);
		entity.setRegistrationId(registrationId);
		entity.setClientId(clientId);
		entity.setClientSecret(clientSecret);
		entity.setScopes(scopes);
		return Mono.fromSupplier(() -> clientRegistrationEntityRepo.save(entity)).map(this::toClientRegistration);
	}

	public Mono<Void> removeRegistrations(String keycloakId) {
		final var toRemove = clientRegistrationEntityRepo.findAllByKeycloakId(keycloakId);
		for (final var entity : toRemove) {
			registrationsCache.remove(entity.getRegistrationId());
		}
		return Mono.fromRunnable(() -> clientRegistrationEntityRepo.deleteAll(toRemove));
	}

	private ClientRegistration toClientRegistration(ClientRegistrationEntity entity) {
		final var registrationProperties = new OAuth2ClientProperties.Registration();
		registrationProperties.setAuthorizationGrantType(entity.getAuthorizationGrantType().getValue());
		registrationProperties.setClientAuthenticationMethod(entity.getClientAuthenticationMethod().getValue());
		registrationProperties.setClientId(entity.getClientId());
		registrationProperties.setClientName(entity.getClientName());
		registrationProperties.setClientSecret(entity.getClientSecret());
		registrationProperties.setProvider(entity.getProvider());
		registrationProperties.setRedirectUri(entity.getRedirectUri());
		registrationProperties.setScope(entity.getScopes());

		final var clientProperties = new OAuth2ClientProperties();
		clientProperties.getProvider().put(entity.getProvider(), oauth2ClientProperties.getProvider().get(entity.getProvider()));
		clientProperties.getRegistration().put(entity.getRegistrationId(), registrationProperties);

		final var registrations = new OAuth2ClientPropertiesMapper(clientProperties).asClientRegistrations();
		this.registrationsCache.putAll(registrations);

		return this.registrationsCache.get(entity.getRegistrationId());
	}

	static class StringSetConverter implements AttributeConverter<Set<String>, String> {
		private static final String SPLIT_CHAR = ";";

		@Override
		public String convertToDatabaseColumn(Set<String> stringList) {
			return stringList != null ? String.join(SPLIT_CHAR, stringList) : "";
		}

		@Override
		public Set<String> convertToEntityAttribute(String string) {
			return string != null ? Stream.of(string.split(SPLIT_CHAR)).collect(Collectors.toSet()) : Set.of();
		}
	}

	static class StringClientAuthenticationMethodConverter implements AttributeConverter<ClientAuthenticationMethod, String> {

		@Override
		public String convertToDatabaseColumn(ClientAuthenticationMethod authorizationGrantType) {
			return authorizationGrantType == null ? null : authorizationGrantType.getValue();
		}

		@Override
		public ClientAuthenticationMethod convertToEntityAttribute(String string) {
			return string == null ? null : new ClientAuthenticationMethod(string);
		}
	}

	static class StringAuthorizationGrantTypeConverter implements AttributeConverter<AuthorizationGrantType, String> {

		@Override
		public String convertToDatabaseColumn(AuthorizationGrantType authorizationGrantType) {
			return authorizationGrantType == null ? null : authorizationGrantType.getValue();
		}

		@Override
		public AuthorizationGrantType convertToEntityAttribute(String string) {
			return string == null ? null : new AuthorizationGrantType(string);
		}
	}

	static class UnknownProviderException extends RuntimeException {
		private static final long serialVersionUID = -3061850618915631962L;

		public UnknownProviderException(String provider) {
			super("OIDC Provider %s is missing from application properties".formatted(provider));
		}
	}

	static class DuplicateRegistrationException extends RuntimeException {
		private static final long serialVersionUID = -3061850618915631962L;

		public DuplicateRegistrationException(String registrationId) {
			super("An OAuth2 client registration is already registered with ID %s".formatted(registrationId));
		}
	}

	public static record RelyingPartyKeys(String keycloakId, String registrationId, String clientId) {
	}
}
