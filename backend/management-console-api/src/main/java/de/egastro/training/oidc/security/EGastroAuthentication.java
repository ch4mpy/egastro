package de.egastro.training.oidc.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;

/**
 * Custom `Authentication` exposing eGastro private claims and Keycloak realm which issued the access token
 *
 * @author Jérôme Wacongne &lt;ch4mp#64;c4-soft.com&gt;
 */
public class EGastroAuthentication extends OAuthentication<OpenidClaimSet> {
	private static final long serialVersionUID = -1325104147048800592L;

	public EGastroAuthentication(OpenidClaimSet claims, Collection<? extends GrantedAuthority> authorities, String tokenString) {
		super(claims, authorities, tokenString);
	}

	public String getRealm() {
		final var splits = getAttributes().getIssuer().toString().split("/");
		return splits.length > 0 ? splits[splits.length - 1] : null;
	}

	public List<String> getManages() {
		return this.getAttributes().getClaimAsStringList("manages");
	}

	public List<String> getWorksAt() {
		return this.getAttributes().getClaimAsStringList("worksAt");
	}
}
