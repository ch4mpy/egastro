package de.egastro.training.oidc.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.validator.constraints.Length;

import de.egastro.training.oidc.domain.persistence.StringSetConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "restaurants")
@Table(name = "restaurants", uniqueConstraints = { @UniqueConstraint(columnNames = { "realm", "name" }) })
@Data
@NoArgsConstructor
public class Restaurant implements Serializable {
	private static final long serialVersionUID = -1107120581141810642L;

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "realm", nullable = false)
	private String realmName;

	@Column(nullable = false)
	@Length(min = 1)
	private String name;

	@Column(nullable = false)
	@Convert(converter = StringSetConverter.class)
	private Set<String> managers = new HashSet<>();

	@Column(nullable = false)
	@Convert(converter = StringSetConverter.class)
	private Set<String> employees = new HashSet<>();

	@OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "restaurant")
	private List<Dish> dishes = new ArrayList<>();

	@OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "restaurant")
	private List<Order> orders = new ArrayList<>();

	public Restaurant(String realmName, String restaurantName, Collection<String> managersNames) {
		this.setName(restaurantName);
		this.setRealmName(realmName);
		this.managers = new HashSet<>(managersNames);
	}
}
