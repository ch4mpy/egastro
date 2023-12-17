package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record RestaurantOverviewDto(@NotNull Long id, @NotNull String authorizedParty, @NotNull String name) {

}
