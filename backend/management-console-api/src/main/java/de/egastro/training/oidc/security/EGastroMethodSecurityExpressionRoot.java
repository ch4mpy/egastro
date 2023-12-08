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
	}

	public boolean manages(Restaurant restaurant) {
		return restaurant.getManagers().contains(getAuthentication().getName());
	}

	public boolean hasPassed(Order order) {
		return Objects.equals(order.getCustomerName(), getAuthentication().getName());
	}
}