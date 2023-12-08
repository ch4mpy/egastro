package de.egastro.training.oidc;

import java.util.Optional;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConf {

	@Component
	static class KcIdpHintAwareOAuth2AuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {
		static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
		static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

		private final DefaultServerOAuth2AuthorizationRequestResolver delegate;

		public KcIdpHintAwareOAuth2AuthorizationRequestResolver(ReactiveClientRegistrationRepository clientRegistrationRepository) {
			this.delegate = new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
		}

		@Override
		public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
			return delegate.resolve(exchange).map(request -> {
				Optional
						.ofNullable(exchange.getRequest().getHeaders().get(KC_IDP_HINT_HEADER))
						.ifPresent(hint -> request.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
				return request;
			});
		}

		@Override
		public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
			return delegate.resolve(exchange, clientRegistrationId).map(request -> {
				Optional
						.ofNullable(exchange.getRequest().getHeaders().get(KC_IDP_HINT_HEADER))
						.ifPresent(hint -> request.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
				return request;
			});
		}
	}

}
