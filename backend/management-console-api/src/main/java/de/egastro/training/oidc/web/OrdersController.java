package de.egastro.training.oidc.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

import de.egastro.training.oidc.domain.Order;
import de.egastro.training.oidc.domain.OrderLine;
import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.RestaurantGrant;
import de.egastro.training.oidc.domain.persistence.DishRepository;
import de.egastro.training.oidc.domain.persistence.InstantEpochSecondConverter;
import de.egastro.training.oidc.domain.persistence.OrderRepository;
import de.egastro.training.oidc.dtos.ErrorDto;
import de.egastro.training.oidc.dtos.restaurants.OrderCreationDto;
import de.egastro.training.oidc.dtos.restaurants.OrderLineResponseDto;
import de.egastro.training.oidc.dtos.restaurants.OrderLineUpdateDto;
import de.egastro.training.oidc.dtos.restaurants.OrderResponseDto;
import de.egastro.training.oidc.dtos.restaurants.OrderUpdateDto;
import de.egastro.training.oidc.security.EGastroAuthentication;
import de.egastro.training.oidc.web.RestaurantsController.RestaurantNotFoundException;
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
@RequestMapping("/authorized-parties/{authorizedParty}/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrdersController {

	private final OrderRepository orderRepo;
	private final DishRepository dishRepo;

	/*------------------*/
	/* Public interface */
	/*------------------*/

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@PreAuthorize("worksFor(#restaurant)")
	@Operation(responses = { @ApiResponse(description = "Ok"), @ApiResponse(responseCode = "404", description = "Restaurant not found") })
	public List<OrderResponseDto> listOrders(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant)
			throws RestaurantNotFoundException {
		if (!Objects.equals(restaurant.getAuthorizedParty(), authorizedParty)) {
			throw new RestaurantNotFoundException(authorizedParty, restaurant.getName());
		}
		return restaurant.getOrders().stream().map(OrdersController::toDto).toList();
	}

	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@PreAuthorize("isAuthenticated()")
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created order")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid OrderCreationDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "404",
							description = "Restaurant not found",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "409",
							description = "Dish from another restaurant",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<OrderResponseDto> createOrder(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @Parameter(schema = @Schema(type = "integer")) Restaurant restaurant,
			@RequestBody @Valid OrderCreationDto dto,
			EGastroAuthentication auth)
			throws RestaurantNotFoundException,
			DishesFromAnotherRestaurantException {
		if (!Objects.equals(restaurant.getAuthorizedParty(), authorizedParty)) {
			throw new RestaurantNotFoundException(authorizedParty, restaurant.getName());
		}
		final var orderedDishesIds = dto.lines().stream().map(OrderLineUpdateDto::dishId).toList();
		final var orderedDishes = dishRepo.findAllById(orderedDishesIds);
		if (orderedDishes.stream().anyMatch(d -> !Objects.equals(restaurant.getId(), d.getRestaurant().getId()))) {
			throw new DishesFromAnotherRestaurantException();
		}
		if (!Objects.equals(dto.customer(), auth.getName()) && auth.getGrantsFor(restaurant.getId()).contains(RestaurantGrant.UPDATE_ORDERS)) {
			throw new ForbiddenException();
		}
		final var order = new Order(restaurant, dto.customer(), List.of(), Instant.now(), Instant.ofEpochSecond(dto.askedFor()));
		final var lines = dto.lines().stream().map(lineDto -> {
			final var dish = orderedDishes
					.stream()
					.filter(d -> d.getId().equals(lineDto.dishId()))
					.findAny()
					.orElseThrow(() -> new DishNotFoundException(lineDto.dishId(), restaurant.getName(), authorizedParty));
			final var line = new OrderLine(new OrderLine.OrderLineId(order, dish), lineDto.quantity());
			return line;
		}).toList();
		order.setLines(lines);
		restaurant.getOrders().add(order);
		final var saved = orderRepo.save(order);
		return ResponseEntity
				.accepted()
				.location(URI.create("/realms/%s/restaurants/%d/orders/%d".formatted(authorizedParty, restaurant.getId(), saved.getId())))
				.body(toDto(saved));
	}

	@GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Transactional(readOnly = true)
	@PreAuthorize("hasPassed(#order) or on(#restaurantId).isGrantedWith('VIEW_ORDERS')")
	@Operation(
			responses = {
					@ApiResponse(headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the updated order")),
					@ApiResponse(responseCode = "404", description = "Order not found") })
	public OrderResponseDto retrieveOrder(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @NotNull Long restaurantId,
			@PathVariable("orderId") @Parameter(schema = @Schema(type = "integer")) Order order)
			throws OrderNotFoundException {
		if (!Objects.equals(order.getRestaurant().getId(), restaurantId)) {
			throw new OrderNotFoundException(order.getId(), restaurantId, authorizedParty);
		}
		return toDto(order);
	}

	@PutMapping(path = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Transactional()
	@PreAuthorize("hasPassed(#order) or on(#restaurantId).isGrantedWith('UPDATE_ORDERS')")
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the updated order")),
					@ApiResponse(
							responseCode = "400",
							description = "Invalid OrderUpdateDto",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))),
					@ApiResponse(
							responseCode = "404",
							description = "Order not found",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<Void> updateOrder(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @NotNull Long restaurantId,
			@PathVariable("orderId") @Parameter(schema = @Schema(type = "integer")) Order order,
			@RequestBody @Valid OrderUpdateDto dto)
			throws OrderNotFoundException {
		if (!Objects.equals(order.getRestaurant().getId(), restaurantId)) {
			throw new OrderNotFoundException(order.getId(), restaurantId, authorizedParty);
		}
		order.setEngagedFor(InstantEpochSecondConverter.toInstant(dto.engagedFor()));
		order.setReadyAt(InstantEpochSecondConverter.toInstant(dto.readyAt()));
		order.setPickedAt(InstantEpochSecondConverter.toInstant(dto.pickedAt()));
		final var saved = orderRepo.save(order);
		return ResponseEntity
				.accepted()
				.location(URI.create("/realms/%s/restaurants/%d/orders/%d".formatted(authorizedParty, restaurantId, saved.getId())))
				.build();
	}

	@DeleteMapping(path = "/{orderId}")
	@Transactional()
	@PreAuthorize("hasPassed(#order) or on(#restaurantId).isGrantedWith('UPDATE_ORDERS')")
	@Operation(
			responses = {
					@ApiResponse(responseCode = "201", description = "Deletion accepted"),
					@ApiResponse(
							responseCode = "404",
							description = "Order not found",
							content = @Content(schema = @Schema(implementation = ErrorDto.class))) })
	public ResponseEntity<Void> deleteOrder(
			@PathVariable("authorizedParty") @NotEmpty String authorizedParty,
			@PathVariable("restaurantId") @NotNull Long restaurantId,
			@PathVariable("orderId") @Parameter(schema = @Schema(type = "integer")) Order order)
			throws OrderNotFoundException {
		if (!Objects.equals(order.getRestaurant().getId(), restaurantId)) {
			throw new OrderNotFoundException(order.getId(), restaurantId, authorizedParty);
		}
		order.getRestaurant().getOrders().remove(order);
		orderRepo.delete(order);
		return ResponseEntity.accepted().build();
	}

	/*------*/
	/* DTOs */
	/*------*/

	static OrderResponseDto toDto(Order order) {
		return new OrderResponseDto(
				order.getId(),
				order.getCustomerName(),
				order.getLines().stream().map(OrdersController::toDto).toList(),
				order.getPassedAt().getEpochSecond(),
				order.getAskedFor().getEpochSecond(),
				InstantEpochSecondConverter.toEpochSechond(order.getEngagedFor()),
				InstantEpochSecondConverter.toEpochSechond(order.getReadyAt()),
				InstantEpochSecondConverter.toEpochSechond(order.getPickedAt()));
	}

	static OrderLineResponseDto toDto(OrderLine line) {
		return new OrderLineResponseDto(DishesController.toDto(line.getId().getDish()), line.getQuantity());
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

	@ResponseStatus(HttpStatus.NOT_FOUND)
	static class OrderNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 4211451914083237049L;

		public OrderNotFoundException(Long id, Long restaurantId, String realm) {
			super("No order with ID %d in restaurant %d from realm %s".formatted(id, restaurantId, realm));
		}
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	static class DishesFromAnotherRestaurantException extends RuntimeException {
		private static final long serialVersionUID = 8007189359123503370L;

		public DishesFromAnotherRestaurantException() {
			super("Order can't be accpeted: dishes from another restaurant");
		}
	}

	@ResponseStatus(HttpStatus.FORBIDDEN)
	static class ForbiddenException extends RuntimeException {
		private static final long serialVersionUID = -3957883050266139229L;

	}
}
