package de.egastro.training.oidc.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.validator.constraints.Length;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "restaurants")
@Table(name = "restaurants", uniqueConstraints = { @UniqueConstraint(columnNames = { "authorized_party", "name" }) })
@Data
@NoArgsConstructor
public class Restaurant implements Serializable {
	private static final long serialVersionUID = -1107120581141810642L;

	public Restaurant(String restaurantName, String authorizedParty) {
		this.setName(restaurantName);
		this.setAuthorizedParty(authorizedParty);
	}

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	@Length(min = 1)
	private String name;

	@Column(name = "authorized_party", nullable = false)
	private String authorizedParty;

	@OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "restaurant")
	private List<Dish> dishes = new ArrayList<>();

	@OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "restaurant")
	private List<Order> orders = new ArrayList<>();
}
