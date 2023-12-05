package de.egastro.training.oidc.domain.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.Restaurant.RestaurantId;

public interface RestaurantRepository extends JpaRepository<Restaurant, RestaurantId>, JpaSpecificationExecutor<Restaurant> {

	List<Restaurant> findByIdRealmName(String realmId);

	default Restaurant getRestaurant(String realmName, String restaurantName) throws RestaurantNotFoundException {
		return this.findById(new RestaurantId(realmName, restaurantName)).orElseThrow(() -> new RestaurantNotFoundException(restaurantName, realmName));
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static class RestaurantNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 6256445317080759234L;

		public RestaurantNotFoundException(String name, String realm) {
			super("No restaurant named %s found in realm %s".formatted(name, realm));
		}
	}
}
