package de.egastro.training.oidc.dtos.users;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record UserGrantsDto(@NotNull Map<Long, List<String>> grantsByRestaurantId) {

}
