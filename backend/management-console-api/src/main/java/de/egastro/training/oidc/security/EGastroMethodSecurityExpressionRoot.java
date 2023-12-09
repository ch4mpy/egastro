package de.egastro.training.oidc.security;

import java.util.Objects;

import com.c4_soft.springaddons.security.oidc.spring.C4MethodSecurityExpressionRoot;

import de.egastro.training.oidc.domain.Order;
import de.egastro.training.oidc.domain.Restaurant;

/**
 * Custom DSL for Spring Security SpEL
 *
 * @author Jérôme Wacongne &lt;ch4mp#64;c4-soft.com&gt;
 */
final class EGastroMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

	public boolean is(String username) {
		return Objects.equals(username, getAuthentication().getName());
	}

	public boolean worksFor(Restaurant restaurant) {
		return restaurant.getEmployees().contains(getAuthentication().getName()) || restaurant.getManagers().contains(getAuthentication().getName());
		/*
		 * alternative impl:
		 *
		 * if(getAuthentication() instanceof EGastroAuthentication egauth) { return egauth.getWorksAt().contains(restaurant.getId()) ||
		 * egauth.getManages().contains(restaurant.getId()); } return false;
		 */
	}

	public boolean worksFor(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return egauth.getWorksAt().contains(restaurantId) || egauth.getManages().contains(restaurantId);
		}
		return false;
	}

	public boolean manages(Restaurant restaurant) {
		return restaurant.getManagers().contains(getAuthentication().getName());
		/*
		 * alternative impl:
		 *
		 * if(getAuthentication() instanceof EGastroAuthentication egauth) { return egauth.getManages().contains(restaurant.getId()); } return false;
		 */
	}

	public boolean manages(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return egauth.getManages().contains(restaurantId);
		}
		return false;
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
}