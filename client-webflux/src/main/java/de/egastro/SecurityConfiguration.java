package de.egastro;

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	SecurityWebFilterChain clientSecurityFilterChain(
			ServerHttpSecurity http,
			ReactiveClientRegistrationRepository clientRegistrationRepository,
			ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver)
			throws Exception {
		final String[] routes = { "/bff/v1/**", "/login/**", "/oauth2/**", "/logout" };
		final var matchers = Stream.of(routes).map(PathPatternParserServerWebExchangeMatcher::new).map(ServerWebExchangeMatcher.class::cast).toList();
		http.securityMatcher(new OrServerWebExchangeMatcher(matchers));

		http.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(null));

		http.oauth2Login(login -> {
			login.authorizationRequestResolver(authorizationRequestResolver);
		});
		http.logout(logout -> {
			logout.logoutSuccessHandler(new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository));
		});
		http
				.csrf(
						csrf -> csrf
								.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
								.csrfTokenRequestHandler(new SpaServerCsrfTokenRequestHandler()));
		http.authorizeExchange(ex -> ex.anyExchange().authenticated());
		return http.build();
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	SecurityWebFilterChain resourceServerSecurityFilterChain(
			ServerHttpSecurity http,
			ReactiveClientRegistrationRepository clientRegistrationRepository,
			ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver)
			throws Exception {
		http.oauth2ResourceServer(Customizer.withDefaults());
		http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance());
		http.csrf(csrf -> csrf.disable());
		http.authorizeExchange(ex -> ex.anyExchange().permitAll());
		return http.build();
	}

	static final class SpaServerCsrfTokenRequestHandler extends ServerCsrfTokenRequestAttributeHandler {
		private final ServerCsrfTokenRequestAttributeHandler delegate = new XorServerCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(ServerWebExchange exchange, Mono<CsrfToken> csrfToken) {
			/*
			 * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection of the CsrfToken when it is rendered in the response body.
			 */
			this.delegate.handle(exchange, csrfToken);
		}

		@Override
		public Mono<String> resolveCsrfTokenValue(ServerWebExchange exchange, CsrfToken csrfToken) {
			final var hasHeader = exchange.getRequest().getHeaders().get(csrfToken.getHeaderName()).stream().filter(StringUtils::hasText).count() > 0;
			return hasHeader ? super.resolveCsrfTokenValue(exchange, csrfToken) : this.delegate.resolveCsrfTokenValue(exchange, csrfToken);
		}
	}

	@Bean
	WebFilter csrfCookieWebFilter() {
		return (exchange, chain) -> {
			exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty()).subscribe();
			return chain.filter(exchange);
		};
	}

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
