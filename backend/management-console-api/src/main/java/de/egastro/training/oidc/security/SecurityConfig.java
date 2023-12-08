package de.egastro.training.oidc.security;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oidc.starter.ConfigurableClaimSetAuthoritiesConverter;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.IssuerStartsWithAuthenticationManagerResolver;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.JwtAbstractAuthenticationTokenConverter;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	ConfigurableClaimSetAuthoritiesConverter authoritiesConverter(@Value("${keycloak-host}") URI keycloakHost, SpringAddonsOidcProperties addonsProperties) {
		final var opProperties = addonsProperties.getOpProperties(keycloakHost.toString());
		return new ConfigurableClaimSetAuthoritiesConverter(claims -> opProperties.getAuthorities());
	}

	@Bean
	JwtAbstractAuthenticationTokenConverter authenticationFactory(
			@Value("${keycloak-host}") URI keycloakHost,
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsOidcProperties addonsProperties) {
		return jwt -> {
			final var opProperties = addonsProperties.getOpProperties(keycloakHost.toString());
			final var claims = new OpenidClaimSet(jwt.getClaims(), opProperties.getUsernameClaim());
			return new EGastroAuthentication(claims, authoritiesConverter.convert(claims), jwt.getTokenValue());
		};
	}

	@Bean
	AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
			@Value("${keycloak-host}") URI keycloakHost,
			Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {
		return new JwtIssuerAuthenticationManagerResolver(new IssuerStartsWithAuthenticationManagerResolver(keycloakHost.toString(), authenticationConverter));
	}

	@Bean
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new C4MethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
	}
}
