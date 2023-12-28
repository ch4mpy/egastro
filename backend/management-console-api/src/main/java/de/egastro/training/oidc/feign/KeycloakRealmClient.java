package de.egastro.training.oidc.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import de.egastro.training.oidc.dtos.keycloak.RealmRepresentation;
import de.egastro.training.oidc.dtos.keycloak.RoleRepresentation;

@FeignClient(name = "keycloak-admin-realm")
public interface KeycloakRealmClient {

	@GetMapping
	List<RealmRepresentation> getRealms();

	@PostMapping
	void createRealm(@RequestBody RealmRepresentation realm);

	@PostMapping("/{realmName}/roles")
	void createRole(@PathVariable("realmName") String realmName, @RequestBody RoleRepresentation role);

}
