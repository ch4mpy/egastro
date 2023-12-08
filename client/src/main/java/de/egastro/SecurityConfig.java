package de.egastro;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	SecurityFilterChain clientSecurityFilterChain(
			HttpSecurity http,
			ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizationRequestResolver authorizationRequestResolver)
			throws Exception {
		http.securityMatcher("/bff/**");
		http.oauth2Login(login -> {
			login.authorizationEndpoint(authorizationEndpoint -> {
				authorizationEndpoint.authorizationRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
				authorizationEndpoint.authorizationRequestResolver(authorizationRequestResolver);
			});
			final var successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
			successHandler.setRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
			login.successHandler(successHandler);
		});
		http.csrf(csrf -> {
			csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler());
		});
		http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

		http.logout(logout -> {
			final var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
			logoutSuccessHandler.setRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.ACCEPTED));
		});

		http.authorizeHttpRequests(ex -> ex.anyRequest().permitAll());
		return http.build();
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	@SuppressWarnings("unchecked")
	SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, @Value("${permit-all:[]}") String[] permitAll) throws Exception {
		http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.csrf(csrf -> csrf.disable());
		http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtResourceServer -> {
			final var authenticationConverter = new JwtAuthenticationConverter();
			authenticationConverter.setPrincipalClaimName(StandardClaimNames.PREFERRED_USERNAME);
			authenticationConverter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
				final var resourceAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("resource_access", Map.of());
				final var obsClientAccess = (Map<String, Object>) resourceAccess.getOrDefault("observability", Map.of());
				final var realmRoles = (List<String>) obsClientAccess.getOrDefault("roles", List.of());
				return realmRoles.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
			});
			jwtResourceServer.jwtAuthenticationConverter(authenticationConverter);
		})).exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
			response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
			response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
		}));
		// @formatter:off
		http.authorizeHttpRequests(ex -> ex
				.requestMatchers(permitAll).permitAll()
				.requestMatchers("/actuator/**").hasAuthority("OBSERVABILITY")
				.anyRequest().authenticated());
		// @formatter:on
		return http.build();
	}

	static class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
		private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
			this.delegate.handle(request, response, csrfToken);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
				return super.resolveCsrfTokenValue(request, csrfToken);
			}
			return this.delegate.resolveCsrfTokenValue(request, csrfToken);
		}
	}

	static class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException,
				IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
			csrfToken.getToken();

			filterChain.doFilter(request, response);
		}
	}

	@RequiredArgsConstructor
	static class ConfigurableStatusRedirectStrategy implements RedirectStrategy {
		static final String RESPONSE_STATUS_HEADER = "X-RESPONSE-STATUS";
		static final String RESPONSE_STATUS_LOCATION = "X-RESPONSE-LOCATION";

		private final HttpStatus defaultStatus;

		@Override
		public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
			final var requestedStatus = request.getIntHeader(RESPONSE_STATUS_HEADER);
			response.setStatus(requestedStatus > -1 ? requestedStatus : defaultStatus.value());

			final var location = Optional.ofNullable(request.getHeader(RESPONSE_STATUS_LOCATION)).orElse(url);
			response.setHeader(HttpHeaders.LOCATION, location);
		}
	}

	@Component
	static class KcIdpHintAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
		static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
		static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

		private final DefaultOAuth2AuthorizationRequestResolver delegate;

		public KcIdpHintAwareOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
			this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
					clientRegistrationRepository,
					OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
		}

		@Override
		public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
			final var req = delegate.resolve(request);
			Optional.ofNullable(request.getHeader(KC_IDP_HINT_HEADER)).ifPresent(hint -> req.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
			return req;
		}

		@Override
		public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
			final var req = delegate.resolve(request, clientRegistrationId);
			Optional.ofNullable(request.getHeader(KC_IDP_HINT_HEADER)).ifPresent(hint -> req.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
			return req;
		}
	}
}
