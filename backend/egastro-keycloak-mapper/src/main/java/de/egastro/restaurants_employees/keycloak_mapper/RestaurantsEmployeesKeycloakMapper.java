package de.egastro.restaurants_employees.keycloak_mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
import org.springframework.util.StringUtils;

public class RestaurantsEmployeesKeycloakMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {
	private static final String TOKEN_ENDPOINT_URI = "restaurants-employees-client.token-endpoint-uri";
	private static final String RESTAURANTS_EMPLOYEES_CLIENT_ID = "restaurants-employees-client.client-id";
	private static final String RESTAURANTS_EMPLOYEES_CLIENT_SECRET = "restaurants-employees-client.client-secret";
	private static final String PROVIDER_ID = "egastro.de";
	private static final String RESTAURANTS_EMPLOYEES_API_BASE_URI = "restaurants-employees-api.base-uri";

	private final List<ProviderConfigProperty> configProperties = new ArrayList<>();

	public RestaurantsEmployeesKeycloakMapper() {
		ProviderConfigProperty property;

		property = new ProviderConfigProperty();
		property.setName(RESTAURANTS_EMPLOYEES_API_BASE_URI);
		property.setLabel("Resturants-employees API base URI");
		property.setHelpText("Base URI for API exposing relations between users and restaurants");
		property.setType(ProviderConfigProperty.STRING_TYPE);
		property.setDefaultValue("https://localhost:8080/direct/v1/users");
		configProperties.add(property);

		property = new ProviderConfigProperty();
		property.setName(RESTAURANTS_EMPLOYEES_CLIENT_ID);
		property.setLabel("Resturants-employees mapper client ID");
		property.setHelpText("Resturants-employees mapper client ID");
		property.setType(ProviderConfigProperty.STRING_TYPE);
		property.setDefaultValue("restaurants-employees-mapper");
		configProperties.add(property);

		property = new ProviderConfigProperty();
		property.setName(RESTAURANTS_EMPLOYEES_CLIENT_SECRET);
		property.setLabel("Resturants-employees mapper client secret");
		property.setHelpText("Resturants-employees mapper client secret");
		property.setType(ProviderConfigProperty.PASSWORD);
		configProperties.add(property);

		property = new ProviderConfigProperty();
		property.setName(TOKEN_ENDPOINT_URI);
		property.setLabel("Token endpoint");
		property.setHelpText("Token end-point for authorizing proxies mapper");
		property.setType(ProviderConfigProperty.STRING_TYPE);
		property.setDefaultValue("https://localhost:8443/realms/master/protocol/openid-connect/token");
		configProperties.add(property);
	}

	@Override
	public IDToken transformIDToken(
			IDToken token,
			ProtocolMapperModel mappingModel,
			KeycloakSession keycloakSession,
			UserSessionModel userSession,
			ClientSessionContext clientSessionCtx) {
		return transform(token, mappingModel, keycloakSession, userSession, clientSessionCtx);
	}

	@Override
	public AccessToken transformAccessToken(
			AccessToken token,
			ProtocolMapperModel mappingModel,
			KeycloakSession keycloakSession,
			UserSessionModel userSession,
			ClientSessionContext clientSessionCtx) {
		return transform(token, mappingModel, keycloakSession, userSession, clientSessionCtx);
	}

	@Override
	public AccessToken transformUserInfoToken(
			AccessToken token,
			ProtocolMapperModel mappingModel,
			KeycloakSession keycloakSession,
			UserSessionModel userSession,
			ClientSessionContext clientSessionCtx) {
		return transform(token, mappingModel, keycloakSession, userSession, clientSessionCtx);
	}

	@Override
	public String getDisplayCategory() {
		return TOKEN_MAPPER_CATEGORY;
	}

	@Override
	public String getDisplayType() {
		return "User employments mapper";
	}

	@Override
	public String getId() {
		return PROVIDER_ID;
	}

	@Override
	public String getHelpText() {
		return "Adds \"manages\" and \"worksAt\" private claims containing lists of restaurants employing the current user";
	}

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
		return configProperties;
	}

	private <T extends IDToken> T transform(
			T token,
			ProtocolMapperModel mappingModel,
			KeycloakSession keycloakSession,
			UserSessionModel userSession,
			ClientSessionContext clientSessionCtx) {
		final var clientConfig = new RestaurantsEmployeesClientConfig(
				mappingModel.getConfig().get(TOKEN_ENDPOINT_URI),
				mappingModel.getConfig().get(RESTAURANTS_EMPLOYEES_CLIENT_ID),
				mappingModel.getConfig().get(RESTAURANTS_EMPLOYEES_CLIENT_SECRET),
				mappingModel.getConfig().get(RESTAURANTS_EMPLOYEES_API_BASE_URI));
		final var realm = Optional.ofNullable(userSession.getRealm()).map(RealmModel::getName).orElse("");
		final var username = Optional.ofNullable(userSession.getUser()).map(UserModel::getUsername).orElse("");
		if (StringUtils.hasText(realm) && StringUtils.hasText(username)) {
			RestaurantsEmployeesClient.getInstance(clientConfig).getUserEmployments(realm, username).ifPresent(userEmployments -> {
				token.getOtherClaims().put("manages", userEmployments.manages());
				token.getOtherClaims().put("worksAt", userEmployments.worksAt());
				setClaim(token, mappingModel, userSession, keycloakSession, clientSessionCtx);
			});
		}
		return token;

	}
}