package de.egastro.training.oidc.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.dtos.restaurants.RestaurantIdDto;
import de.egastro.training.oidc.dtos.users.UserEmployersDto;
import de.egastro.training.oidc.dtos.users.UserResponseDto;
import de.egastro.training.oidc.security.EGastroAuthentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
@RequiredArgsConstructor
public class UsersController {

	private static final UserResponseDto ANONYMOUS = new UserResponseDto("", "", "", List.of());

	private final RestaurantRepository restaurantRepo;

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	// this route should have permitAll() access policy (it returns ANONYMOUS to unauthorized requests)
	@Transactional(readOnly = true)
	public UserResponseDto getMe(Authentication auth) {
		return auth instanceof EGastroAuthentication oauth2 ? toDto(oauth2) : ANONYMOUS;
	}

	@GetMapping(path = "/{realm}/{username}/employers", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('KEYCLOAK_MAPPER')")
	@Transactional(readOnly = true)
	public UserEmployersDto getUserEmployers(@PathVariable("realm") String realm, @PathVariable("username") String username) {
		final var restaurants = restaurantRepo.findByIdRealmName(realm);
		final var manages = new ArrayList<RestaurantIdDto>();
		final var worksAt = new ArrayList<RestaurantIdDto>();
		restaurants.stream().forEach(r -> {
			if (r.getManagers().contains(username)) {
				manages.add(new RestaurantIdDto(realm, r.getId().getName()));
			}
			if (r.getEmployees().contains(username)) {
				worksAt.add(new RestaurantIdDto(realm, r.getId().getName()));
			}
		});
		return new UserEmployersDto(manages, worksAt);
	}

	static UserResponseDto toDto(EGastroAuthentication oauth2) {
		final var iss = oauth2.getAttributes().getIssuer().toString().split("/");
		final var realm = iss.length > 0 ? iss[iss.length - 1] : "";
		final var name = oauth2.getName();
		final var email = oauth2.getAttributes().getEmail();
		final var roles = oauth2.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return new UserResponseDto(realm, name, email, roles);
	}
}
