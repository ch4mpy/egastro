package de.egastro.training.oidc;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;

public class EGastroAuthentication extends OAuthentication<OpenidClaimSet> {
	private static final long serialVersionUID = -1325104147048800592L;

	public EGastroAuthentication(OpenidClaimSet claims, Collection<? extends GrantedAuthority> authorities, String tokenString) {
		super(claims, authorities, tokenString);
	}

	public String getRealm() {
		final var splits = getAttributes().getIssuer().toString().split("/");
		return splits.length > 0 ? splits[splits.length - 1] : null;
	}
}
