package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record DishUpdateDto(@NotEmpty String name, @NotNull @Min(0) Integer priceInCents) {

}
