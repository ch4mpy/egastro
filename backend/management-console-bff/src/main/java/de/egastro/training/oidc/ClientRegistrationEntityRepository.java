package de.egastro.training.oidc;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRegistrationEntityRepository extends JpaRepository<ClientRegistrationEntity, String> {
	List<ClientRegistrationEntity> findAllByKeycloakId(String keycloakId);
}