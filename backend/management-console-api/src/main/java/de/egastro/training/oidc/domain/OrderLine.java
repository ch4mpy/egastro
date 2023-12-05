package de.egastro.training.oidc.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderLine {

	@EmbeddedId
	private OrderLineId id;

	@Column(nullable = false)
	private Integer quantity;

	@Embeddable
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OrderLineId implements Serializable {
		private static final long serialVersionUID = -2959348871514881117L;

		@ManyToOne(optional = false)
		@JoinColumn(name = "order_id", nullable = false, updatable = false)
		private Order order;

		@ManyToOne(optional = false)
		@JoinColumn(name = "dish_id", nullable = false, updatable = false)
		private Dish dish;
	}

}
