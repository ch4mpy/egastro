package de.egastro.training.oidc;

import java.net.URI;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConf {

	@Component
	static class KcIdpHintAwareOAuth2AuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {
		static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
		static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

		private final DefaultServerOAuth2AuthorizationRequestResolver delegate;
		private final URI gatewayUri;

		public KcIdpHintAwareOAuth2AuthorizationRequestResolver(
				ReactiveClientRegistrationRepository clientRegistrationRepository,
				@Value("${gateway-uri}") URI gatewayUri) {
			this.delegate = new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
			this.gatewayUri = gatewayUri;
		}

		@Override
		public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
			return delegate.resolve(exchange).map(request -> {
				Optional
						.ofNullable(exchange.getRequest().getHeaders().get(KC_IDP_HINT_HEADER))
						.ifPresent(hint -> request.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));

				final var uri = UriComponentsBuilder.fromUriString(request.getAuthorizationRequestUri());
				uri.scheme(gatewayUri.getScheme());
				uri.host(gatewayUri.getHost());
				uri.port(gatewayUri.getPort());
				final var builder = OAuth2AuthorizationRequest.from(request);
				builder.authorizationRequestUri(uri.build().toUriString());
				return builder.build();
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
