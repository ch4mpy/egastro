package de.egastro.training.oidc.dtos.users;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record UserEmployersDto(@NotNull List<Long> manages, @NotNull List<Long> worksAt) {

}
