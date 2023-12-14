package de.egastro.training.oidc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.persistence.RestaurantRepository;
import de.egastro.training.oidc.feign.KeycloakRealmService;

@SpringBootApplication
public class ManagementConsoleApi {

	public static void main(String[] args) {
		final var ctx = SpringApplication.run(ManagementConsoleApi.class, args);

		final var clientRegistrationRepo = ctx.getBean(InMemoryClientRegistrationRepository.class);
		final var clientCredentialRegistrations = StreamSupport
				.stream(Spliterators.spliteratorUnknownSize(clientRegistrationRepo.iterator(), Spliterator.ORDERED), false)
				.filter(r -> Objects.equals(AuthorizationGrantType.CLIENT_CREDENTIALS, r.getAuthorizationGrantType()))
				.toList();
		if (clientCredentialRegistrations.size() > 0) {
			initDomain(ctx);
		}
	}

	/**
	 * This method require that the application has a client registration with client_credentials flow and that #64;Feign client behind the RealmService is
	 * configured to use that registration: this code is executed at startup, without the context of any user sending a request.
	 *
	 * @param ctx
	 */
	private static void initDomain(ApplicationContext ctx) {

		final var restaurantRepo = ctx.getBean(RestaurantRepository.class);
		final var realmService = ctx.getBean(KeycloakRealmService.class);

		final var realms = realmService.getRealms();
		if (realms.stream().filter(r -> "sushibach".equals(r.realm())).count() == 0) {
			realmService.createRealm("sushibach", Optional.empty());
		}

		final var sushibach = restaurantRepo.findByRealmNameAndName("sushibach", "Sushi Bach");
		if (sushibach.isEmpty()) {
			restaurantRepo.save(new Restaurant("sushibach", "Sushi Bach", List.of("ch4mp", "tonton-pirate")));
		}
	}

}
