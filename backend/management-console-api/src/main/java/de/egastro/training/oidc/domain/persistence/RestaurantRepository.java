package de.egastro.training.oidc.domain.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import de.egastro.training.oidc.domain.Restaurant;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

	List<Restaurant> findByRealmName(String realm);

	Optional<Restaurant> findByRealmNameAndName(String realm, String name);

	default Restaurant getRestaurant(String realmName, String restaurantName) throws RestaurantNotFoundException {
		return this.findByRealmNameAndName(realmName, restaurantName).orElseThrow(() -> new RestaurantNotFoundException(restaurantName, realmName));
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static class RestaurantNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 6256445317080759234L;

		public RestaurantNotFoundException(String name, String realm) {
			super("No restaurant named %s found in realm %s".formatted(name, realm));
		}
	}
}
