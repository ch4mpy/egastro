package de.egastro.training.oidc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class UserRestaurantGrant {

	public UserRestaurantGrant(String username, Restaurant restaurant, RestaurantGrant grant) {
		super();
		this.username = username;
		this.restaurant = restaurant;
		this.grant = grant;
	}

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private String username;

	@ManyToOne(optional = false)
	private Restaurant restaurant;

	@Enumerated(EnumType.STRING)
	@Column(name = "grant_label", nullable = false)
	private RestaurantGrant grant;

}
