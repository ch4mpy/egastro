package de.egastro.training.oidc.web;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.training.oidc.domain.RestaurantGrant;
import de.egastro.training.oidc.domain.UserRestaurantGrant;
import de.egastro.training.oidc.domain.persistence.UserRestaurantGrantRepository;
import de.egastro.training.oidc.dtos.users.UserGrantsDto;
import de.egastro.training.oidc.dtos.users.UserResponseDto;
import de.egastro.training.oidc.dtos.users.UserSessionResponseDto;
import de.egastro.training.oidc.feign.KeycloakUserService;
import de.egastro.training.oidc.security.EGastroAuthentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
@RequiredArgsConstructor
public class UsersController {

	private static final UserResponseDto ANONYMOUS = new UserResponseDto("", "", "", "", List.of());

	private final UserRestaurantGrantRepository userRestaurantGrantRepo;

	private final KeycloakUserService userService;

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("permitAll()")
	// this route should have permitAll() access policy (it returns ANONYMOUS to unauthorized requests)
	@Transactional(readOnly = true)
	public UserSessionResponseDto getMe(Authentication auth) {
		return auth instanceof EGastroAuthentication oauth2 ? toDto(oauth2) : new UserSessionResponseDto(ANONYMOUS, Map.of(), Long.MAX_VALUE);
	}

	@GetMapping(path = "/{realm}/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_MANAGER', 'EGASTRO_BUSINESS')")
	@Transactional(readOnly = true)
	public UserResponseDto getUser(@PathVariable("realm") @NotEmpty String realm, @PathVariable("username") @NotEmpty String username) {
		return userService.getUser(realm, username);
	}

	@GetMapping(path = "/{realm}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_MANAGER', 'EGASTRO_BUSINESS')")
	@Transactional(readOnly = true)
	public List<UserResponseDto> searchUsers(@PathVariable("realm") @NotEmpty String realm, @RequestParam(name = "search") @NotEmpty String search) {
		return userService.findUsers(realm, search, false);
	}

	@GetMapping(path = "/{realm}/{username}/grants", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_MANAGER', 'EGASTRO_BUSINESS', 'KEYCLOAK_MAPPER')")
	@Transactional(readOnly = true)
	public UserGrantsDto getUserGrants(@PathVariable("realm") String realm, @PathVariable("username") String username) {
		final var grants = userRestaurantGrantRepo.findByUsername(username);
		return new UserGrantsDto(toGrantsByRestaurants(grants));
	}

	static UserSessionResponseDto toDto(EGastroAuthentication oauth2) {
		final var iss = oauth2.getAttributes().getIssuer().toString().split("/");
		final var realm = iss.length > 0 ? iss[iss.length - 1] : "";
		final var roles = oauth2.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return new UserSessionResponseDto(
				new UserResponseDto(
						realm,
						oauth2.getAttributes().getSubject(),
						oauth2.getAttributes().getPreferredUsername(),
						oauth2.getAttributes().getEmail(),
						roles),
				oauth2
						.getGrantsByRestaurantId()
						.entrySet()
						.stream()
						.map(e -> Map.entry(e.getKey(), e.getValue().stream().map(RestaurantGrant::toString).toList()))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
				oauth2.getAttributes().getExpiresAt().getEpochSecond());
	}

	static Map<Long, List<String>> toGrantsByRestaurants(List<UserRestaurantGrant> grants) {
		return grants
				.stream()
				.collect(
						Collectors
								.groupingBy(
										g -> g.getRestaurant().getId(),
										TreeMap::new,
										Collectors.mapping((UserRestaurantGrant g) -> g.getGrant().toString(), Collectors.toList())));
	}
}
