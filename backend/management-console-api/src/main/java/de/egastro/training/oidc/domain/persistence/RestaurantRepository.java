package de.egastro.training.oidc.domain.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import de.egastro.training.oidc.domain.Restaurant;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

	List<Restaurant> findByAuthorizedParty(String authorizedParty);

	List<Restaurant> findByAuthorizedPartyContainsIgnoreCase(String authorizedParty);
}
