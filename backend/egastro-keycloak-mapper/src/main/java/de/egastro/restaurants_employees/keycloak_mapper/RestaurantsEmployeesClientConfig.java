package de.egastro.restaurants_employees.keycloak_mapper;

public record RestaurantsEmployeesClientConfig(String tokenEndpointUri, String clientId, String clientSecret, String usersApiBaseUri) {
}