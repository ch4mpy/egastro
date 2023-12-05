package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record DishResponseDto(@NotNull Long id, @NotEmpty String name, @NotNull Integer priceInCents) {
}