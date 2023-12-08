package de.egastro;

import lombok.Data;

@Data class Meal {

	private Long id;

	private final String orderedBy;

	String description;

}