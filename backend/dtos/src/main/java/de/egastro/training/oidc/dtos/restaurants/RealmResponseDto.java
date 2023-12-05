package de.egastro.training.oidc.dtos.restaurants;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record RealmResponseDto(@NotNull String id, @NotNull String name, @NotNull String loginTheme) {

}
