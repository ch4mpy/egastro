package de.egastro;

import java.util.List;

import lombok.Data;

@Data class Restaurant {

	private final Long id;

	private final String name;

	private final List<String> employees;

	private final List<Meal> meals;

}