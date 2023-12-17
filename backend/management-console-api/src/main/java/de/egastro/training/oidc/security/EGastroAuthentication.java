package de.egastro.training.oidc.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;

import de.egastro.training.oidc.domain.RestaurantGrant;

/**
 * Custom `Authentication` exposing eGastro private claims and Keycloak realm which issued the access token
 *
 * @author Jérôme Wacongne &lt;ch4mp#64;c4-soft.com&gt;
 */
public class EGastroAuthentication extends OAuthentication<OpenidClaimSet> {
	private static final long serialVersionUID = -1325104147048800592L;

	private final Map<Long, Set<RestaurantGrant>> grants;

	public EGastroAuthentication(OpenidClaimSet claims, Collection<? extends GrantedAuthority> authorities, String tokenString) {
		super(claims, authorities, tokenString);
		final Map<Long, List<String>> grantStrings = this.getAttributes().containsKey("grantsByRestaurantId")
				? this.getAttributes().getClaim("grantsByRestaurantId")
				: Map.of();
		this.grants = Collections
				.unmodifiableMap(
						grantStrings
								.entrySet()
								.stream()
								.map(
										e -> Map
												.entry(
														e.getKey(),
														Collections
																.unmodifiableSet(
																		e.getValue().stream().map(RestaurantGrant::valueOf).collect(Collectors.toSet()))))
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}

	public String getRealm() {
		final var splits = getAttributes().getIssuer().toString().split("/");
		return splits.length > 0 ? splits[splits.length - 1] : null;
	}

	public String getAuthorizedParty() {
		return getAttributes().getAuthorizedParty();
	}

	public Map<Long, Set<RestaurantGrant>> getGrantsByRestaurantId() {
		return grants;
	}

	public Set<RestaurantGrant> getGrantsFor(Long restaurantId) {
		return grants.getOrDefault(restaurantId, Set.of());
	}
}
