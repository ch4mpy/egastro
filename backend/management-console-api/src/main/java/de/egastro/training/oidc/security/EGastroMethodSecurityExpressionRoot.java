package de.egastro.training.oidc.security;

import java.util.Objects;
import java.util.Set;

import com.c4_soft.springaddons.security.oidc.spring.SpringAddonsMethodSecurityExpressionRoot;

import de.egastro.training.oidc.domain.Order;
import de.egastro.training.oidc.domain.Restaurant;
import de.egastro.training.oidc.domain.RestaurantGrant;

/**
 * Custom DSL for Spring Security SpEL
 *
 * @author Jérôme Wacongne &lt;ch4mp#64;c4-soft.com&gt;
 */
final class EGastroMethodSecurityExpressionRoot extends SpringAddonsMethodSecurityExpressionRoot {

	public boolean is(String username) {
		return Objects.equals(username, getAuthentication().getName());
	}

	public boolean worksFor(Restaurant restaurant) {
		return worksFor(restaurant.getId());
	}

	public boolean worksFor(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return egauth.getGrantsFor(restaurantId).size() > 0;
		}
		return false;
	}

	public RestaurantPermissions on(Restaurant restaurant) {
		return on(restaurant.getId());
	}

	public RestaurantPermissions on(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return new RestaurantPermissions(egauth.getGrantsFor(restaurantId));
		}
		return RestaurantPermissions.EMPTY;
	}

	public boolean hasPassed(Order order) {
		return Objects.equals(order.getCustomerName(), getAuthentication().getName());
	}

	public boolean isFromMasterOr(String realm) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return Objects.equals(realm, egauth.getRealm()) || Objects.equals("master", egauth.getRealm());
		}
		return false;
	}

	public boolean isFrom(String realm) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return Objects.equals(realm, egauth.getRealm());
		}
		return false;
	}

	public static class RestaurantPermissions {
		private static final RestaurantPermissions EMPTY = new RestaurantPermissions(Set.of());

		private final Set<RestaurantGrant> grants;

		public RestaurantPermissions(Set<RestaurantGrant> grants) {
			this.grants = grants;
		}

		public boolean isGrantedWith(String grant) {
			return grants.contains(RestaurantGrant.valueOf(grant));
		}
	}
}