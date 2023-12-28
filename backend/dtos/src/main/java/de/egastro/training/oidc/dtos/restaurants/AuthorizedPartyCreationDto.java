package de.egastro.training.oidc.dtos.restaurants;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

public record AuthorizedPartyCreationDto(
		@NotEmpty String registrationId,
		@NotEmpty String clientId,
		@Nullable String clientSecret,
		@Nullable String loginTheme) {

}
