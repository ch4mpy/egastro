package de.egastro.training.oidc;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.feign.KeycloakRealmService;

@SpringBootApplication
public class ManagementConsoleApi {

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(ManagementConsoleApi.class, args);
		final var restaurantRepo = ctx.getBean(RestaurantRepository.class);
		final var realmService = ctx.getBean(KeycloakRealmService.class);

		final var realms = realmService.getRealms();
		if (realms.stream().filter(r -> "test".equals(r.realm())).count() == 0) {
			realmService.createRealm("test", Optional.empty());
		}

		final var sushibach = restaurantRepo.findByRealmNameAndName("sushibach", "Sushi Bach");
		if (sushibach.isEmpty()) {
			restaurantRepo.save(new Restaurant("sushibach", "Sushi Bach", List.of("ch4mp", "tonton-pirate")));
		}
	}

}
