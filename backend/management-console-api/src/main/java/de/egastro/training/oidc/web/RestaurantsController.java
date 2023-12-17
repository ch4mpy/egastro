package de.egastro.training.oidc.web;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.RestaurantGrant;
import de.egastro.training.oidc.domain.UserRestaurantGrant;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.domain.persistence.UserRestaurantGrantRepository;
import de.egastro.training.oidc.dtos.ErrorDto;
import de.egastro.training.oidc.dtos.restaurants.RestaurantCreationDto;
import de.egastro.training.oidc.dtos.restaurants.RestaurantOverviewDto;
import de.egastro.training.oidc.dtos.restaurants.RestaurantUpdateDto;
import de.egastro.training.oidc.dtos.users.UserResponseDto;
import de.egastro.training.oidc.feign.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Restaurants")
public class RestaurantsController {

	private final KeycloakUserService userService;
	private final RestaurantRepository restaurantRepo;
	private final UserRestaurantGrantRepository userRestaurantGrantRepo;

	/*------------------*/
	/* Public interface */
	/*------------------*/

	@GetMapping(path = "/authorized-parties", produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")
	public List<String> listAuthorizedParties(@RequestParam(name = "like", required = false, defaultValue = "") @NotNull String like) {
		final var restaurants = restaurantRepo.findByAuthorizedPartyContainsIgnoreCase(like);
		return restaurants.stream().map(Restaurant::getAuthorizedParty).toList();
	}

	@GetMapping(path = "/authorized-parties/{authorizedParty}/restaurants", produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@PreAuthorize("permitAll()")
	public List<RestaurantOverviewDto> listRestaurants(@PathVariable("authorizedParty") @NotEmpty String authorizedParty) {
		final var restaurants = restaurantRepo.findByAuthorizedParty(authorizedParty);
		return restaurants.stream().map(RestaurantsController::toDto).toList();
	}

	@PostMapping(
			path = "/authorized-parties/{authorizedParty}/restaurants",
			produces = MediaType.APPLICATION_JSON_VALUE,
			consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@PreAuthorize("hasAnyAuthority('EGASTRO_MANAGER')")
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created restaurant")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid RestaurantCreationDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "409",
							description = "Restaurant with same name already exists for that authorized party, content = @Content(schema = @Schema(implementation = ErrorDto.class))"),
					@ApiResponse(
							responseCode = "409",
							description = "User with same name but different email exists in the realm",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<RestaurantOverviewDto> createRestaurant(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@RequestBody @Valid RestaurantCreationDto dto)
			throws RestaurantWithSameNameAlreadyExistsException,
			UserWithSameNameButDifferentEmailAlreadyExistsException {
		if (restaurantRepo
				.findByAuthorizedPartyContainsIgnoreCase(authorizedParty)
				.stream()
				.filter(r -> Objects.equals(r.getName().toLowerCase(), dto.name().toLowerCase()))
				.findAny()
				.isPresent()) {
			throw new RestaurantWithSameNameAlreadyExistsException(dto.name(), authorizedParty);
		}
		final var restaurant = restaurantRepo.save(new Restaurant(dto.name(), authorizedParty));
		final var manager = getOrCreateUser("business", dto.managerName(), dto.managerEmail());
		userRestaurantGrantRepo.saveAll(Stream.of(RestaurantGrant.values()).map(g -> new UserRestaurantGrant(manager.subject(), restaurant, g)).toList());

		return ResponseEntity
				.accepted()
				.location(URI.create("/authorized-parties/%s/restaurants/%d".formatted(authorizedParty, restaurant.getId())))
				.body(toDto(restaurant));
	}

	@GetMapping(path = "/authorized-parties/{authorizedParty}/restaurants/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("permitAll()")
	@Transactional(readOnly = true)
	@Operation(responses = { @ApiResponse(), @ApiResponse(responseCode = "404", description = "Restaurant not found") })
	public RestaurantOverviewDto retrieveRestaurant(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant)
			throws RestaurantNotFoundException {
		if (!Objects.equals(restaurant.getAuthorizedParty(), authorizedParty)) {
			throw new RestaurantNotFoundException(authorizedParty, restaurant.getName());
		}
		return toDto(restaurant);
	}

	@PutMapping(path = "/authorized-parties/{authorizedParty}/restaurants/{restaurantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")
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
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant,
			@RequestBody @Valid RestaurantUpdateDto dto)
			throws RestaurantNotFoundException,
			UserNotFoundException {
		if (!Objects.equals(restaurant.getAuthorizedParty(), authorizedParty)) {
			throw new RestaurantNotFoundException(authorizedParty, restaurant.getName());
		}
		restaurant.setName(dto.name());
		restaurant.setAuthorizedParty(authorizedParty);

		return ResponseEntity
				.accepted()
				.location(URI.create("/realms/%s/restaurants/%d".formatted(authorizedParty, restaurantRepo.save(restaurant).getId())))
				.build();
	}

	@DeleteMapping(path = "/authorized-parties/{authorizedParty}/restaurants/{restaurantId}")
	@Transactional()
	@PreAuthorize("hasAuthority('EGASTRO_REALM_MANAGER')")
	@Operation(responses = { @ApiResponse(responseCode = "201") })
	public ResponseEntity<Void> deleteRestaurant(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant) {
		if (!Objects.equals(restaurant.getAuthorizedParty(), authorizedParty)) {
			throw new RestaurantNotFoundException(authorizedParty, restaurant.getName());
		}
		restaurantRepo.delete(restaurant);
		return ResponseEntity.accepted().build();
	}

	/*-----------*/
	/* Internals */
	/*-----------*/

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
	UserResponseDto getOrCreateUser(String realmName, String username, String email) throws UserWithSameNameButDifferentEmailAlreadyExistsException {
		final var usersWithSameUsername = userService.findUsers(realmName, username, true);
		if (usersWithSameUsername.size() > 0) {
			if (Objects.equals(email, usersWithSameUsername.get(0).email())) {
				return usersWithSameUsername.get(0);
			}
			throw new UserWithSameNameButDifferentEmailAlreadyExistsException(username, realmName);
		}

		final var usersWithSameEmail = userService.findUsers(realmName, email, true);
		if (usersWithSameEmail.size() > 0) {
			return usersWithSameEmail.get(0);
		}

		return userService.createUser(realmName, username, email);
	}

	/*------*/
	/* DTOs */
	/*------*/

	static RestaurantOverviewDto toDto(Restaurant restaurant) {
		return new RestaurantOverviewDto(restaurant.getId(), restaurant.getAuthorizedParty(), restaurant.getName());
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

		public RestaurantWithSameNameAlreadyExistsException(String name, String authorizedParty) {
			super("A restaurant named %s already exist for authorized party %s".formatted(name, authorizedParty));
		}
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static class RestaurantNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 6256445317080759234L;

		public RestaurantNotFoundException(String authorizedParty, String name) {
			super("No restaurant named %s found in realm %s".formatted(name, authorizedParty));
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
