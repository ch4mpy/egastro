package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotEmpty;

public record RestaurantIdDto(@NotEmpty String realmName, @NotEmpty String restaurantName) {

}
