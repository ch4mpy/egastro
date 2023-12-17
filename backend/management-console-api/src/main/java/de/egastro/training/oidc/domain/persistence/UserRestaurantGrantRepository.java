package de.egastro.training.oidc.domain.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import de.egastro.training.oidc.domain.UserRestaurantGrant;

public interface UserRestaurantGrantRepository extends JpaRepository<UserRestaurantGrant, Long>, JpaSpecificationExecutor<UserRestaurantGrant> {

	List<UserRestaurantGrant> findByUsername(String username);
}
