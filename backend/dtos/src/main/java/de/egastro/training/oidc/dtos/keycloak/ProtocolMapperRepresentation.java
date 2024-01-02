package de.egastro.training.oidc.dtos.keycloak;

import java.util.Map;

public record ProtocolMapperRepresentation(String name, String protocol, String protocolMapper, boolean consentRequired, Map<String, Object> config) {

}
