package de.egastro.training.oidc.dtos.restaurants;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record OrderResponseDto(
		@NotEmpty Long id,
		@NotEmpty String customer,
		@NotNull List<OrderLineResponseDto> lines,
		@NotNull Long passedAt,
		@NotNull Long askedFor,
		Long engagedFor,
		Long readyAt,
		Long pickedAt) {

}
