package de.egastro.training.oidc.feign;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.egastro.training.oidc.dtos.keycloak.RealmRepresentation;
import de.egastro.training.oidc.dtos.keycloak.RoleRepresentation;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeycloakRealmService {
	private final KeycloakRealmClient realmClient;

	public List<RealmRepresentation> getRealms() {
		return realmClient.getRealms();
	}

	public RealmRepresentation createRealm(String realmName, Optional<String> loginTheme) {
		final var realm = new RealmRepresentation(
				realmName,
				realmName,
				600,
				true,
				"all",
				true,
				loginTheme.orElse("keycloak"),
				false,
				false,
				false,
				false,
				true,
				false,
				true,
				true);

		realmClient.createRealm(realm);

		createRole(realmName, "MANAGER");

		return realm;
	}

	public RoleRepresentation createRole(String realmName, String name) {
		final var role = new RoleRepresentation(name);

		realmClient.createRole(realmName, role);
		return role;
	}

}
