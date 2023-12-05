package de.egastro.training.oidc.dtos.keycloak;

import java.util.List;

public record UserRepresentation(String username, String email, List<String> realmRoles) {

}
