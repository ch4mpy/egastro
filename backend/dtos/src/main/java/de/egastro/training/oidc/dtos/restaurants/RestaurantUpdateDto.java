package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotEmpty;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record RestaurantUpdateDto(@NotEmpty String name, @NotEmpty String authorizedParty) {

}
