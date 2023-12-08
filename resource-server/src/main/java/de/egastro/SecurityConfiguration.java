package de.egastro;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionRoot;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
	@Bean
	SecurityFilterChain resourceServerFilterChain(HttpSecurity http, AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver)
			throws Exception {
		http.authorizeHttpRequests(authz -> {
		// @formatter:off
				authz
					.requestMatchers("/me").permitAll()
					.anyRequest().authenticated();
				// @formatter:on
		})
				.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver))
				.exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
					response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
					response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
				}));
		return http.build();
	}

	@Bean
	IAuthenticationConverter authenticationConverter() {
		return EGastroAuthentication::new;
	}

	static interface IAuthenticationConverter extends Converter<Jwt, AbstractAuthenticationToken> {
	}

	static class EGastroAuthentication extends AbstractAuthenticationToken {
		private static final long serialVersionUID = -6421797824331073601L;

		private final Jwt jwt;
		private final String realm;

		public EGastroAuthentication(Jwt jwt) {
			super(extractAuthorities(jwt));
			setAuthenticated(jwt != null);
			this.jwt = jwt;
			setDetails(jwt);
			final var splits = jwt.getClaimAsString(JwtClaimNames.ISS).split("/");
			this.realm = splits.length > 0 ? splits[splits.length - 1] : null;

		}

		public String getRealm() {
			return realm;
		}

		@SuppressWarnings("unchecked")
		public List<Long> getManages() {
			return (List<Long>) jwt.getClaims().getOrDefault("manages", List.of());
		}

		@SuppressWarnings("unchecked")
		public List<Long> getWorksAt() {
			return (List<Long>) jwt.getClaims().getOrDefault("worksAt", List.of());
		}

		@Override
		public String getName() {
			return jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME);
		}

		@Override
		public Object getCredentials() {
			return jwt.getTokenValue();
		}

		@Override
		public Jwt getPrincipal() {
			return jwt;
		}

		@Override
		public Jwt getDetails() {
			return jwt;
		}

		@SuppressWarnings("unchecked")
		static List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
			final var realmAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("realm_access", Map.of());
			final var realmRoles = (List<String>) realmAccess.getOrDefault("roles", List.of());
			return realmRoles.stream().map(SimpleGrantedAuthority::new).toList();
		}
	}

	@Bean
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new C4MethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
	}

	static final class EGastroMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

		public boolean is(String username) {
			return Objects.equals(username, getAuthentication().getName());
		}

		public boolean worksFor(Restaurant restaurant) {
			return restaurant.getEmployees().contains(getAuthentication().getName());
		}

		public boolean hasOrdered(Meal meal) {
			return Objects.equals(meal.getOrderedBy(), getAuthentication().getName());
		}
	}

	static class IssuerStartsWithAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

		private final String keycloakHost;
		private final Converter<Jwt, AbstractAuthenticationToken> authenticationConverter;
		private final Map<String, AuthenticationManager> jwtManagers = new ConcurrentHashMap<>();

		public IssuerStartsWithAuthenticationManagerResolver(String keycloakHost, Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {
			super();
			this.keycloakHost = keycloakHost.toString();
			this.authenticationConverter = authenticationConverter;
		}

		@Override
		public AuthenticationManager resolve(String issuer) {
			if (!jwtManagers.containsKey(issuer)) {
				if (!issuer.startsWith(keycloakHost)) {
					throw new UnknownIssuerException(issuer);
				}
				final var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
				var provider = new JwtAuthenticationProvider(decoder);
				provider.setJwtAuthenticationConverter(authenticationConverter);
				jwtManagers.put(issuer, provider::authenticate);
			}
			return jwtManagers.get(issuer);

		}

		@ResponseStatus(HttpStatus.UNAUTHORIZED)
		static class UnknownIssuerException extends RuntimeException {
			private static final long serialVersionUID = 4177339081914400888L;

			public UnknownIssuerException(String issuer) {
				super("Unknown issuer: %s".formatted(issuer));
			}
		}
	}

	@Bean
	AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
			@Value("${keycloak-host}") URI keycloakHost,
			Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {

		return new JwtIssuerAuthenticationManagerResolver(new IssuerStartsWithAuthenticationManagerResolver(keycloakHost.toString(), authenticationConverter));
	}
}
