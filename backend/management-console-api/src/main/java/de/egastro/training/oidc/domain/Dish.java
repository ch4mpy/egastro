package de.egastro.training.oidc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "dishes")
@Table(name = "dishes")
@Data
@NoArgsConstructor
public class Dish {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumns({
			@JoinColumn(name = "restaurant_realm", referencedColumnName = "realm", nullable = false, updatable = false),
			@JoinColumn(name = "restaurant_name", referencedColumnName = "name", nullable = false, updatable = false) })
	private Restaurant restaurant;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(nullable = false)
	private Integer priceInCents;

	public Dish(Restaurant restaurant, String name, Integer priceInCents) {
		this.restaurant = restaurant;
		this.name = name;
		this.priceInCents = priceInCents;
	}

}
