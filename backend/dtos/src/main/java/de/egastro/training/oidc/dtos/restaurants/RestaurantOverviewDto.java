package de.egastro.training.oidc.dtos.restaurants;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record RestaurantOverviewDto(
		@NotNull Long id,
		@NotNull String realmName,
		@NotNull String name,
		@NotNull List<String> managers,
		@NotNull List<String> employees) {

}
