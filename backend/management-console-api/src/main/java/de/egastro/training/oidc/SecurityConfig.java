package de.egastro.training.oidc;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionRoot;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.JwtAbstractAuthenticationTokenConverter;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Component
	static class EGastroAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

		private final String keycloakHost;
		private final JwtAbstractAuthenticationTokenConverter authenticationConverter;
		private final Map<String, AuthenticationManager> jwtManagers = new ConcurrentHashMap<>();
		private final JwtIssuerAuthenticationManagerResolver delegate = new JwtIssuerAuthenticationManagerResolver(
				(AuthenticationManagerResolver<String>) this::getOrCreate);

		public EGastroAuthenticationManagerResolver(
				@Value("${keycloak-host}") URI keycloakHost,
				JwtAbstractAuthenticationTokenConverter authenticationConverter) {
			super();
			this.keycloakHost = keycloakHost.toString();
			this.authenticationConverter = authenticationConverter;
		}

		@Override
		public AuthenticationManager resolve(HttpServletRequest context) {
			return delegate.resolve(context);
		}

		private AuthenticationManager getOrCreate(String issuer) {
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
				super("Unkown issuer: %s".formatted(issuer));
			}
		}
	}

	@Bean
	JwtAbstractAuthenticationTokenConverter authenticationFactory(
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsOidcProperties addonsProperties) {
		return jwt -> {
			final var opProperties = addonsProperties.getOpProperties("egastro");
			final var claims = new OpenidClaimSet(jwt.getClaims(), opProperties.getUsernameClaim());
			return new EGastroAuthentication(claims, authoritiesConverter.convert(claims), jwt.getTokenValue());
		};
	}

	@Bean
	static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new C4MethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
	}

	static final class EGastroMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

		public boolean is(String preferredUsername) {
			return Objects.equals(preferredUsername, getAuthentication().getName());
		}

		public boolean isManager(String realm, String restaurant) {
			return hasAnyAuthority("NICE", "SUPER_COOL");
		}
	}
}
