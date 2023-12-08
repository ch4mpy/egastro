package de.egastro;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.SecurityConfiguration.EGastroAuthentication;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class GreetController {
	private final RestaurantRepository restaurantRepo;

	/*-------------------------------------------------------*/
	/* Allow only authorized requests (valid authentication) */
	/*-------------------------------------------------------*/

	@GetMapping("/greet")
	@PreAuthorize("isAuthenticated()")
	public GreetingDto greet(EGastroAuthentication auth) {
		return new GreetingDto(
				"Hello %s!, you are authenticated in \"%s\" realm, are granted with %s, manage %s and work at %s"
						.formatted(auth.getName(), auth.getRealm(), auth.getAuthorities(), auth.getManages(), auth.getWorksAt()));
	}

	static record GreetingDto(String message) {
	}

	/*---------------------------------------------------------*/
	/* Check for authentication type when anonymous can access */
	/*---------------------------------------------------------*/

	@GetMapping("/me")
	public UserDto getMe(Authentication auth) {
		if (auth instanceof EGastroAuthentication egAuth) {
			return new UserDto(
					egAuth.getRealm(),
					egAuth.getName(),
					egAuth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
					egAuth.getManages(),
					egAuth.getWorksAt(),
					egAuth.getPrincipal().getExpiresAt().getEpochSecond());
		}
		return UserDto.ANONYMOUS;
	}

	static record UserDto(String realm, String username, List<String> roles, List<Long> manages, List<Long> worksAt, Long exp) {
		static final UserDto ANONYMOUS = new GreetController.UserDto("", "", List.of(), List.of(), List.of(), 0L);
	}

	/*------*/
	/* RBAC */
	/*------*/

	@GetMapping("/users/{username}/employers")
	@PreAuthorize("hasAuthority('KEYCLOAK_MAPPER')")
	public List<Long> getUserEmployers(@PathVariable("username") String username) {
		// An actual implementation would probably use a DB repository (or another micro-service) to retrieve the list of restaurants the user works for
		return List.of(42L);
	}

	/*----------------------------------*/
	/* Expressions on accessed entities */
	/*----------------------------------*/

	@PostMapping("/restaurants/{restaurantId}/meals")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Meal> createMeal(@PathVariable("restaurantId") Restaurant restaurant, @RequestBody MealUpdateDto dto, EGastroAuthentication auth)
			throws URISyntaxException {
		final var meal = new Meal(auth.getName());
		meal.setDescription(dto.description());
		restaurant.getMeals().add(meal);
		restaurantRepo.save(restaurant);

		return ResponseEntity.created(new URI("/restaurants/%s/meals/%d".formatted(restaurant.getId(), meal.getId()))).body(meal);
	}

	@GetMapping("/restaurants/{restaurantId}/meals/{mealId}")
	@PreAuthorize("hasOrdered(#meal) || worksFor(#restaurant)")
	public Meal retreiveMeal(@PathVariable("restaurantId") Restaurant restaurant, @PathVariable("mealId") Meal meal) {

		return meal;
	}

	@PutMapping("/restaurants/{restaurantId}/meals/{mealId}")
	@PreAuthorize("hasOrdered(#meal) || worksFor(#restaurant)")
	public ResponseEntity<Void> updateMeal(
			@PathVariable("restaurantId") Restaurant restaurant,
			@PathVariable("mealId") Meal meal,
			@RequestBody MealUpdateDto dto) {
		meal.description = dto.description();

		return ResponseEntity.accepted().build();
	}

	static record MealUpdateDto(String description) {
	}
}
