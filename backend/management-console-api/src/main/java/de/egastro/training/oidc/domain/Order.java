package de.egastro.training.oidc.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.egastro.training.oidc.domain.persistence.InstantEpochSecondConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "orders")
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private String customerName;

	@ManyToOne(optional = false)
	private Restaurant restaurant;

	@OneToMany(orphanRemoval = true, cascade = CascadeType.ALL, mappedBy = "id.order")
	private List<OrderLine> lines = new ArrayList<>();

	@Column(nullable = false)
	@Convert(converter = InstantEpochSecondConverter.class)
	private Instant passedAt;

	@Column(nullable = false)
	@Convert(converter = InstantEpochSecondConverter.class)
	private Instant askedFor;

	@Column
	@Convert(converter = InstantEpochSecondConverter.class)
	private Instant engagedFor;

	@Column
	@Convert(converter = InstantEpochSecondConverter.class)
	private Instant readyAt;

	@Column
	@Convert(converter = InstantEpochSecondConverter.class)
	private Instant pickedAt;

	public Order(Restaurant restaurant, String customerName, List<OrderLine> lines, Instant passedAt, Instant askedFor) {
		super();
		this.customerName = customerName;
		this.restaurant = restaurant;
		this.lines = lines;
		this.passedAt = passedAt;
		this.askedFor = askedFor;
	}

}
