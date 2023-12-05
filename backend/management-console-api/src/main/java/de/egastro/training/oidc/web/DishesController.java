package de.egastro.training.oidc.web;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import de.egastro.training.oidc.domain.Dish;
import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.persistence.DishRepository;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository.RestaurantNotFoundException;
import de.egastro.training.oidc.dtos.ErrorDto;
import de.egastro.training.oidc.dtos.restaurants.DishResponseDto;
import de.egastro.training.oidc.dtos.restaurants.DishUpdateDto;
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
@RequestMapping("/realms/{realmName}/restaurants/{restaurantName}/dishes")
@RequiredArgsConstructor
@Tag(name = "Dishes")
public class DishesController {

	private final RestaurantRepository restaurantRepo;
	private final DishRepository dishRepo;

	/*------------------*/
	/* Public interface */
	/*------------------*/

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@Operation(responses = { @ApiResponse(headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created dish")) })
	public List<DishResponseDto> listDishes(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantName") @NotEmpty String restaurantName) {
		final var restaurant = restaurantRepo.getRestaurant(realmName, restaurantName);
		return restaurant.getDishes().stream().map(DishesController::toDto).toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created dish")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid DishUpdateDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "404",
							description = "Restaurant not found",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<DishResponseDto> createDish(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantName") @NotEmpty String restaurantName,
			@RequestBody @Valid DishUpdateDto dto)
			throws RestaurantNotFoundException {
		final var restaurant = restaurantRepo.getRestaurant(realmName, restaurantName);
		final var dish = new Dish(restaurant, dto.name(), dto.priceInCents());
		restaurant.getDishes().add(dish);
		final var saved = dishRepo.save(new Dish(restaurant, dto.name(), dto.priceInCents()));
		return ResponseEntity
				.accepted()
				.location(URI.create("/realms/%s/restaurants/%s/dishes/%d".formatted(realmName, restaurantName, saved.getId())))
				.body(toDto(saved));
	}

	@GetMapping(path = "/{dishId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@Operation(
			responses = {
					@ApiResponse(headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created dish")),
					@ApiResponse(responseCode = "404", description = "Dish not found") })
	public DishResponseDto retrieveDish(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantName") @NotEmpty String restaurantName,
			@PathVariable("dishId") @Parameter(schema = @Schema(type = "integer")) Dish dish)
			throws DishNotFoundException {
		if (!Objects.equals(dish.getRestaurant().getId(), new Restaurant.RestaurantId(realmName, restaurantName))) {
			throw new DishNotFoundException(dish.getId(), restaurantName, realmName);
		}
		return toDto(dish);
	}

	@PutMapping(path = "/{dishId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the updated dish")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid DishUpdateDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(responseCode = "404", description = "Dish not found", content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<Void> updateDish(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantName") @NotEmpty String restaurantName,
			@PathVariable("dishId") @Parameter(schema = @Schema(type = "integer")) Dish dish,
			@RequestBody @Valid DishUpdateDto dto)
			throws DishNotFoundException {
		if (!Objects.equals(dish.getRestaurant().getId(), new Restaurant.RestaurantId(realmName, restaurantName))) {
			throw new DishNotFoundException(dish.getId(), restaurantName, realmName);
		}
		dish.setName(dto.name());
		dish.setPriceInCents(dto.priceInCents());
		final var saved = dishRepo.save(dish);
		return ResponseEntity
				.accepted()
				.location(URI.create("/realms/%s/restaurants/%s/dishes/%d".formatted(realmName, restaurantName, saved.getId())))
				.build();
	}

	@DeleteMapping("/{dishId}")
	@Transactional()
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", description = "Dish deletion accepted"),
					@ApiResponse(responseCode = "404", description = "Dish not found", content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<Void> deleteDish(
			@PathVariable("realmName") @NotEmpty String realmName,
			@PathVariable("restaurantName") @NotEmpty String restaurantName,
			@PathVariable("dishId") @Parameter(schema = @Schema(type = "integer")) Dish dish)
			throws DishNotFoundException {
		if (!Objects.equals(dish.getRestaurant().getId(), new Restaurant.RestaurantId(realmName, restaurantName))) {
			throw new DishNotFoundException(dish.getId(), restaurantName, realmName);
		}
		dish.getRestaurant().getDishes().remove(dish);
		dishRepo.delete(dish);
		return ResponseEntity.accepted().build();
	}

	/*------*/
	/* DTOs */
	/*------*/
	static DishResponseDto toDto(Dish dish) {
		return new DishResponseDto(dish.getId(), dish.getName(), dish.getPriceInCents());
	}

	/*------------*/
	/* Exceptions */
	/*------------*/

	@ResponseStatus(HttpStatus.NOT_FOUND)
	static class DishNotFoundException extends RuntimeException {
		private static final long serialVersionUID = -4122331808212443111L;

		public DishNotFoundException(Long id, String name, String realm) {
			super("No dish with ID %d in restaurant named %s from realm %s".formatted(id, name, realm));
		}
	}
}
