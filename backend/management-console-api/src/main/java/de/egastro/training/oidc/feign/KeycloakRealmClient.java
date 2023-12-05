package de.egastro.training.oidc.feign;

import java.util.List;
import java.util.Optional;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import de.egastro.training.oidc.dtos.keycloak.ClientRepresentation;
import de.egastro.training.oidc.dtos.keycloak.RealmRepresentation;
import de.egastro.training.oidc.dtos.keycloak.RoleRepresentation;
import de.egastro.training.oidc.dtos.keycloak.UserRepresentation;

@FeignClient(name = "keycloak-admin-realm", url = "${spring.cloud.openfeign.client.config.keycloak-admin-realm.url}")
public interface KeycloakRealmClient {

	@GetMapping
	List<RealmRepresentation> getRealms();

	@PostMapping
	void createRealm(@RequestBody RealmRepresentation realm);

	@PostMapping("/{realmName}/clients")
	void createClient(@PathVariable("realmName") String realmName, @RequestBody ClientRepresentation client);

	@PostMapping("/{realmName}/roles")
	void createRole(@PathVariable("realmName") String realmName, @RequestBody RoleRepresentation role);

	@GetMapping("/{realmName}/users")
	Long countUsers(
			@PathVariable("realmName") String realmName,
			@RequestParam(name = "username", required = false) Optional<String> username,
			@RequestParam(name = "email", required = false) Optional<String> email,
			@RequestParam(name = "exact", required = false, defaultValue = "false") boolean exact);

	@GetMapping("/{realmName}/users")
	List<UserRepresentation> listUsers(
			@PathVariable("realmName") String realmName,
			@RequestParam(name = "username", required = false) Optional<String> username,
			@RequestParam(name = "email", required = false) Optional<String> email,
			@RequestParam(name = "exact", required = false, defaultValue = "false") boolean exact);

	@PostMapping("/{realmName}/users")
	void createUser(@PathVariable("realmName") String realmName, @RequestBody UserRepresentation user);

}
