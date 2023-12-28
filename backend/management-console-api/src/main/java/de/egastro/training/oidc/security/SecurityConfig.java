package de.egastro.training.oidc.security;

import java.util.Collection;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.JwtAbstractAuthenticationTokenConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	JwtAbstractAuthenticationTokenConverter authenticationFactory(
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsOidcProperties addonsProperties) {
		return jwt -> {
			final var opProperties = addonsProperties.getOpProperties(jwt.getIssuer());
			final var claims = new OpenidClaimSet(jwt.getClaims(), opProperties.getUsernameClaim());
			return new EGastroAuthentication(claims, authoritiesConverter.convert(claims), jwt.getTokenValue());
		};
	}

	@Bean
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new SpringAddonsMethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
	}
}
