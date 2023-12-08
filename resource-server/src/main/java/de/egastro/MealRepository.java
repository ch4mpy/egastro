package de.egastro;

import java.util.Objects;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor class MealRepository implements Converter<String, Meal> {
	private final RestaurantRepository restaurantRepo;

	public Meal findById(Long id) {
		return restaurantRepo
				.findAll()
				.stream()
				.flatMap(r -> r.getMeals().stream())
				.filter(m -> Objects.equals(m.getId(), id))
				.findAny()
				.orElseThrow(() -> new EntityNotFoundException());
	}

	@Override
	public Meal convert(String source) {
		return findById(Long.parseLong(source));
	}
}