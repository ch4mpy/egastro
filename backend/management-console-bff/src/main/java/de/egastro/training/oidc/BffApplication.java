package de.egastro.training.oidc;

import java.util.Objects;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BffApplication {

	public static void main(String[] args) {
		final var ctx = SpringApplication.run(BffApplication.class, args);

		final var clientRegistrationEntityRepo = ctx.getBean(ClientRegistrationEntityRepository.class);
		final var clientRegistrationRepo = ctx.getBean(JpaReactiveClientRegistrationRepository.class);
		final var keycloakService = ctx.getBean(KeycloakClientService.class);

		for (final var regEntity : clientRegistrationEntityRepo.findAll()) {
			keycloakService.getClientSecret(regEntity.getKeycloakId()).doOnError(e -> {
				// If the client was deleted in Keycloak, delete it from eGastro DB
				if (e.getMessage().contains("404")) {
					clientRegistrationEntityRepo.delete(regEntity);
				}
			}).subscribe(secret -> {
				// If the client secret was updated inKeycloak, update it in eGastro DB
				if (!Objects.equals(secret, regEntity.getClientSecret())) {
					regEntity.setClientSecret(secret);
					clientRegistrationEntityRepo.save(regEntity);
				}
				clientRegistrationRepo.findByRegistrationId(regEntity.getRegistrationId());
			});
		}

	}

}
