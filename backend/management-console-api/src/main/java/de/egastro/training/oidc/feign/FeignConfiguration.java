package de.egastro.training.oidc.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.egastro.training.oidc.security.EGastroAuthentication;
import feign.RequestInterceptor;
import feign.RequestTemplate;

@Configuration
@EnableFeignClients(basePackageClasses = FeignConfiguration.class)
public class FeignConfiguration {

	@Profile("forward")
	@Component
	public class ClientBearerRequestInterceptor implements RequestInterceptor {

		@Override
		public void apply(RequestTemplate template) {
			final var auth = SecurityContextHolder.getContext().getAuthentication();
			if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
				if (auth instanceof EGastroAuthentication egAuth) {
					template.header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(egAuth.getTokenString()));
				}
			}
		}
	}
}
