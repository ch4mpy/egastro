package de.egastro.training.oidc;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import de.egastro.training.oidc.JpaReactiveClientRegistrationRepository.StringAuthorizationGrantTypeConverter;
import de.egastro.training.oidc.JpaReactiveClientRegistrationRepository.StringClientAuthenticationMethodConverter;
import de.egastro.training.oidc.JpaReactiveClientRegistrationRepository.StringSetConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Entity
@Data
@RequiredArgsConstructor
public class ClientRegistrationEntity {

	private String keycloakId;

	@Id
	private String registrationId;

	@Column(nullable = false)
	private String clientId;

	@Column(nullable = false)
	private String clientSecret;

	@Convert(converter = StringClientAuthenticationMethodConverter.class)
	@Column(nullable = false)
	private ClientAuthenticationMethod clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

	@Convert(converter = StringAuthorizationGrantTypeConverter.class)
	@Column(nullable = false)
	private AuthorizationGrantType authorizationGrantType = AuthorizationGrantType.AUTHORIZATION_CODE;

	private String redirectUri;

	@Convert(converter = StringSetConverter.class)
	@Column(nullable = false)
	private Set<String> scopes = new HashSet<>();

	@Column(nullable = false)
	private String provider;

	private String clientName;

}