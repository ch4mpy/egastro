package de.egastro.training.oidc.web;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.egastro.training.oidc.dtos.keycloak.RealmRepresentation;
import de.egastro.training.oidc.dtos.restaurants.RealmCreationDto;
import de.egastro.training.oidc.dtos.restaurants.RealmResponseDto;
import de.egastro.training.oidc.feign.KeycloakRealmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/realms")
@RequiredArgsConstructor
@Tag(name = "Realms")
public class RealmsController {

	private final KeycloakRealmService realmService;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('EGASTRO_RESTAURANT_MANAGER')")
	@Transactional(readOnly = true)
	public List<RealmResponseDto> listRealms() {
		final var realms = realmService.getRealms();
		return realms.stream().map(RealmsController::toDto).toList();
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAuthority('EGASTRO_RESTAURANT_MANAGER')")
	@Transactional()
	@Operation(responses = { @ApiResponse(responseCode = "201", headers = @Header(name = HttpHeaders.LOCATION, description = "Path to the created realm")) })
	public ResponseEntity<Void> createRealm(@RequestBody @Valid RealmCreationDto dto) {
		final var realm = realmService.createRealm(dto.name(), Optional.ofNullable(dto.loginTheme()));

		return ResponseEntity.accepted().location(URI.create("/realms/%s".formatted(realm.realm()))).build();
	}

	static RealmResponseDto toDto(RealmRepresentation realm) {
		return new RealmResponseDto(realm.id(), realm.realm(), realm.loginTheme());
	}
}
