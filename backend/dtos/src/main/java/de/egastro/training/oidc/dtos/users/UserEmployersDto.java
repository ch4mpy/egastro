package de.egastro.training.oidc.dtos.users;

import java.util.List;

import de.egastro.training.oidc.dtos.restaurants.RestaurantIdDto;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record UserEmployersDto(@NotNull List<RestaurantIdDto> manages, @NotNull List<RestaurantIdDto> worksAt) {

}
