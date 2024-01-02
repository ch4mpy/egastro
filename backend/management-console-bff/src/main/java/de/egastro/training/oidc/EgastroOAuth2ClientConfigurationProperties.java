package de.egastro.training.oidc;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Custom application properties used to create new OAuth2 clients (add it to a Keycloak "realm" and to the Spring BFF "registrations").
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "egastro.keycloak.client-service")
public class EgastroOAuth2ClientConfigurationProperties {
	URI adminApiUri;
	String webClientRegistration;
	String realm = "egastro";
	String provider;
	String defaultSecret = null;
	List<String> redirectUris;
	List<String> webOrigins = List.of("+");
	List<String> postLogoutRedirectUris = List.of("+");
	String defaultLoginTheme = "";
	Set<String> scopes = Set.of("openid", "profile", "email", "offline_access");
	String magicLinkFlowId;
	UserGrantsMapperConf userGrantsMapperConf = new UserGrantsMapperConf();

	@Data
	public static class UserGrantsMapperConf {
		String clientId = "restaurants-employees-mapper";
		String clientSecret;
		URI tokenEndpoint = URI.create("https://localhost:7080/realms/master/protocol/openid-connect/token");
		URI apiBaseUri = URI.create("https://localhost:7084/users");
	}
}