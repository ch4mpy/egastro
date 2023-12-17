package de.egastro.training.oidc.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import de.egastro.training.oidc.dtos.keycloak.UserRepresentation;

@FeignClient(name = "keycloak-admin-user")
public interface KeycloakUserClient {

	@GetMapping("/{realmName}/users/{subject}")
	UserRepresentation getUser(@PathVariable("realmName") String realmName, @PathVariable("subject") String subject);

	@GetMapping("/{realmName}/users")
	List<UserRepresentation> getUsersLike(
			@PathVariable("realmName") String realmName,
			@RequestParam("search") String search,
			@RequestParam(name = "exact", required = false, defaultValue = "false") boolean exact);

	@GetMapping("/{realmName}/users")
	Long countUsers(
			@PathVariable("realmName") String realmName,
			@RequestParam("search") String search,
			@RequestParam(name = "exact", required = false, defaultValue = "false") boolean exact);

	@PostMapping("/{realmName}/users")
	void createUser(@PathVariable("realmName") String realmName, @RequestBody UserRepresentation user);

}
