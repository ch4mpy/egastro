package de.egastro.training.oidc;

import java.util.Optional;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.reactive.client.SpringAddonsServerOAuth2AuthorizationRequestResolver;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConf {

	/**
	 * (Server)OAuth2AuthorizationRequestResolver is in charge of building the request to initiate the authorization_code flow on the authorization server. The
	 * override we define here optionally provide Keycloak with an IDentity Provider "hint": if the frontend sets a X-KC-IDP-HINT header containing the name of
	 * an identity provider to use, it will be added as a kc_idp_hint request param to authorization request. This can be useful for an Android device to
	 * trigger "Login with Google" or an iOS one to use "Login with Apple".
	 */
	@Component
	static class KcIdpHintAwareOAuth2AuthorizationRequestResolver extends SpringAddonsServerOAuth2AuthorizationRequestResolver {
		public static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
		static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

		public KcIdpHintAwareOAuth2AuthorizationRequestResolver(
				JpaReactiveClientRegistrationRepository clientRegistrationRepository,
				SpringAddonsOidcProperties addonsProperties) {
			super(clientRegistrationRepository, addonsProperties.getClient());
		}

		@Override
		public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
			return super.resolve(exchange).map(request -> postProcess(request, exchange.getRequest().getHeaders()));
		}

		@Override
		public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
			return super.resolve(exchange, clientRegistrationId).map(request -> postProcess(request, exchange.getRequest().getHeaders()));
		}

		private OAuth2AuthorizationRequest postProcess(OAuth2AuthorizationRequest request, HttpHeaders headers) {
			final var modified = OAuth2AuthorizationRequest.from(request);

			// Forward Keycloak IDentity Provider hint if provided
			Optional.ofNullable(headers.get(KC_IDP_HINT_HEADER)).ifPresent(hint -> {
				modified.additionalParameters(params -> params.put(KC_IDP_HINT_PARAM, hint));
			});

			return modified.build();
		}
	}

	@Bean
	JpaReactiveClientRegistrationRepository clientRegistrationRepository(
			ClientRegistrationEntityRepository clientRegistrationEntityRepo,
			OAuth2ClientProperties oauth2ClientProperties) {
		return new JpaReactiveClientRegistrationRepository(clientRegistrationEntityRepo, oauth2ClientProperties);
	}
}
