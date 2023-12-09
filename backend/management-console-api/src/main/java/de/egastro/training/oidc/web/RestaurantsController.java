package de.egastro.training.oidc.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository.RestaurantNotFoundException;
import de.egastro.training.oidc.dtos.ErrorDto;
import de.egastro.training.oidc.dtos.keycloak.UserRepresentation;
import de.egastro.training.oidc.dtos.restaurants.RestaurantCreationDto;
import de.egastro.training.oidc.dtos.restaurants.RestaurantOverviewDto;
import de.egastro.training.oidc.dtos.restaurants.RestaurantUpdateDto;
import de.egastro.training.oidc.feign.KeycloakRealmClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/realms/{realmName}/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurants")
public class RestaurantsController {

	private final KeycloakRealmClient realmClient;
	private final RestaurantRepository restaurantRepo;

	/*------------------*/
	/* Public interface */
	/*------------------*/

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	public List<RestaurantOverviewDto> listRestaurants(@PathVariable("realmName") @NotEmpty String realmName) throws RestaurantNotFoundException {
		final var restaurants = restaurantRepo.findByRealmName(realmName);
		return restaurants.stream().map(RestaurantsController::toDto).toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created restaurant")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid RestaurantCreationDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "409",
							description = "Restaurant with same name already exists in the realm, content = @Content(schema = @Schema(implementation = ErrorDto.class))"),
					@ApiResponse(
							responseCode = "409",
							description = "User with same name but different email exists in the realm",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<RestaurantOverviewDto> createRestaurant(
			@PathVariable("realmName") @NotEmpty String realmName,
			@RequestBody @Valid RestaurantCreationDto dto)
			throws RestaurantWithSameNameAlreadyExistsException,
			UserWithSameNameButDifferentEmailAlreadyExistsException {
		if (restaurantRepo.findByRealmNameAndName(realmName, dto.name()).isPresent()) {
			throw new RestaurantWithSameNameAlreadyExistsException(dto.name(), realmName);
		}
		final var manager = getOrCreateUser(realmName, dto.managerName(), dto.managerEmail());
		final var restaurant = restaurantRepo.save(new Restaurant(realmName, dto.name(), List.of(manager.username())));

		return ResponseEntity.accepted().location(URI.create("/realms/%s/restaurants/%d".formatted(realmName, restaurant.getId()))).body(toDto(restaurant));
	}

	@GetMapping(path = "/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_REALM_MANAGER', 'EGASTRO_CLIENT')")
	@Transactional(readOnly = true)
	@Operation(responses = { @ApiResponse(), @ApiResponse(responseCode = "404", description = "Restaurant not found") })
	public RestaurantOverviewDto retrieveRestaurant(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant)
			throws RestaurantNotFoundException {
		if (!Objects.equals(restaurant.getRealmName(), realmName)) {
			throw new RestaurantNotFoundException(realmName, restaurant.getName());
		}
		return toDto(restaurant);
	}

	@PutMapping(path = "/{restaurantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_REALM_MANAGER', 'EGASTRO_CLIENT')")
	@Transactional()
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the updated order")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid RestaurantUpdateDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "404",
							description = "Restaurant not found",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<Void> updateRestaurant(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant,
			@RequestBody @Valid RestaurantUpdateDto dto)
			throws RestaurantNotFoundException,
			UserNotFoundException {
		if (!Objects.equals(restaurant.getRealmName(), realmName)) {
			throw new RestaurantNotFoundException(realmName, restaurant.getName());
		}
		if (!restaurant.getManagers().contains(dto.managerName())) {
			if (!userExists(realmName, dto.managerName())) {
				throw new UserNotFoundException(dto.managerName(), realmName);
			}
			restaurant.getManagers().add(dto.managerName());
		}
		for (var u : restaurant.getEmployees()) {
			if (!dto.employees().contains(u) || !userExists(realmName, u)) {
				restaurant.getEmployees().remove(u);
			}
		}
		for (var u : dto.employees()) {
			if (!restaurant.getEmployees().contains(u) && userExists(realmName, u)) {
				restaurant.getEmployees().add(u);
			}
		}

		return ResponseEntity.accepted().location(URI.create("/realms/%s/restaurants/%d".formatted(realmName, restaurant.getId()))).build();
	}

	@DeleteMapping(path = "/{restaurantId}")
	@Transactional()
	@Operation(responses = { @ApiResponse(responseCode = "201") })
	public ResponseEntity<Void> deleteRestaurant(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant) {
		if (!Objects.equals(restaurant.getRealmName(), realmName)) {
			throw new RestaurantNotFoundException(realmName, restaurant.getName());
		}
		restaurantRepo.delete(restaurant);
		return ResponseEntity.accepted().build();
	}

	/*-----------*/
	/* Internals */
	/*-----------*/

	boolean userExists(String realmName, String username) {
		return realmClient.countUsers(realmName, Optional.ofNullable(username), Optional.empty(), true) > 0;
	}

	/**
	 * <ul>
	 * <li>if a user with same name and email already exist in this realm, return it.</li>
	 * <li>if a user with same name but different email, throw an exception</li>
	 * <li>if a user with same email but different name, ignore the passed username in favor of the one already registered in Keycloak</li>
	 * <li>if no user with same name or email, create a new one and return it</li>
	 * </ul>
	 *
	 * @param  realmName
	 * @param  username
	 * @param  email
	 * @return
	 * @throws UserWithSameNameButDifferentEmailAlreadyExistsException
	 */
	UserRepresentation getOrCreateUser(String realmName, String username, String email) throws UserWithSameNameButDifferentEmailAlreadyExistsException {
		final var usersWithSameUsername = realmClient.listUsers(realmName, Optional.ofNullable(username), Optional.empty(), true);
		if (usersWithSameUsername.size() > 0) {
			if (Objects.equals(email, usersWithSameUsername.get(0).email())) {
				return usersWithSameUsername.get(0);
			}
			throw new UserWithSameNameButDifferentEmailAlreadyExistsException(username, realmName);
		}

		final var usersWithSameEmail = realmClient.listUsers(realmName, Optional.empty(), Optional.ofNullable(email), true);
		if (usersWithSameEmail.size() > 0) {
			return usersWithSameEmail.get(0);
		}

		final var user = new UserRepresentation(username, email, List.of());
		return user;
	}

	/*------*/
	/* DTOs */
	/*------*/

	static RestaurantOverviewDto toDto(Restaurant restaurant) {
		return new RestaurantOverviewDto(
				restaurant.getId(),
				restaurant.getRealmName(),
				restaurant.getName(),
				new ArrayList<>(restaurant.getManagers()),
				new ArrayList<>(restaurant.getEmployees()));
	}

	/*------------*/
	/* Exceptions */
	/*------------*/

	@ResponseStatus(HttpStatus.CONFLICT)
	static class UserWithSameNameButDifferentEmailAlreadyExistsException extends RuntimeException {
		private static final long serialVersionUID = 8276185341853524238L;

		public UserWithSameNameButDifferentEmailAlreadyExistsException(String username, String realm) {
			super("A user named %s already exist in realm %s but with a different email".formatted(username, realm));
		}
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	static class RestaurantWithSameNameAlreadyExistsException extends RuntimeException {
		private static final long serialVersionUID = 752146016573959127L;

		public RestaurantWithSameNameAlreadyExistsException(String name, String realm) {
			super("A restaurant named %s already exist in realm %s".formatted(name, realm));
		}
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	static class UserNotFoundException extends RuntimeException {
		private static final long serialVersionUID = -6805597861946532891L;

		public UserNotFoundException(String name, String realm) {
			super("No user named %s found in realm %s".formatted(name, realm));
		}
	}
}
