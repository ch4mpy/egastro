package de.egastro.training.oidc.feign;

import java.util.List;

import org.springframework.stereotype.Service;

import de.egastro.training.oidc.dtos.keycloak.UserRepresentation;
import de.egastro.training.oidc.dtos.users.UserResponseDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {
	private final KeycloakUserClient userClient;

	public UserResponseDto getUser(String realmName, String subject) {
		final var userRepresentation = userClient.getUser(realmName, subject);
		if (userRepresentation == null) {
			throw new UserNotFoundException(realmName, subject);
		}
		return toDto(realmName, userRepresentation);
	}

	public List<UserResponseDto> findUsers(String realmName, String search, boolean exact) {
		return userClient.getUsersLike(realmName, search, exact).stream().map(keycloakObject -> toDto(realmName, keycloakObject)).toList();
	}

	public Long countUsers(String realmName, String search, boolean exact) {
		return userClient.countUsers(realmName, search, exact);
	}

	public UserResponseDto createUser(String realmName, String name, String email) {
		userClient.createUser(realmName, new UserRepresentation(null, name, email, List.of()));
		final var realmUsers = userClient.getUsersLike(realmName, name, true);
		if (realmUsers.size() == 0) {
			throw new UserNotFoundException(realmName, name);
		}
		return toDto(realmName, realmUsers.get(0));
	}

	UserResponseDto toDto(String realmName, UserRepresentation keycloakObject) {
		return new UserResponseDto(realmName, keycloakObject.id(), keycloakObject.username(), keycloakObject.email(), keycloakObject.realmRoles());
	}

	static final class UserNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 2803312829524136503L;

		public UserNotFoundException(String realm, String subject) {
			super("No user with name %s in realm %s".formatted(subject, realm));
		}
	}

}
