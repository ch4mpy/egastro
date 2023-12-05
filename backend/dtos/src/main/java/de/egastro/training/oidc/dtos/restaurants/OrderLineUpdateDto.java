package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderLineUpdateDto(@NotEmpty Long dishId, @NotNull @Min(0) Integer quantity) {

}
