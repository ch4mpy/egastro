package de.egastro.training.oidc.dtos.users;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record UserResponseDto(@NotNull String realm, @NotNull String subject, @NotNull String username, @NotNull String email, @NotNull List<String> roles) {

}
