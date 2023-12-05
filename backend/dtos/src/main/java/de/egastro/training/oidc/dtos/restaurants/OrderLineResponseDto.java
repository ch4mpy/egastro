package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotNull;

public record OrderLineResponseDto(@NotNull DishResponseDto dish, @NotNull Integer quantity) {

}
