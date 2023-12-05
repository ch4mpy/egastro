package de.egastro.training.oidc.dtos.restaurants;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record OrderCreationDto(@NotEmpty String customer, @NotNull List<OrderLineUpdateDto> lines, @NotNull Long askedFor) {

}
