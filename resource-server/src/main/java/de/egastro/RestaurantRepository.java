package de.egastro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Repository;

@Repository class RestaurantRepository implements Converter<String, Restaurant> {
	private final Map<Long, Restaurant> data = new HashMap<>();
	private long sequence = 0L;

	public RestaurantRepository() {
		final var sushiBar = new Restaurant(42L, "Sushi Bach", List.of("thom"), new ArrayList<>());
		this.data.put(sushiBar.getId(), sushiBar);
	}

	public Restaurant save(Restaurant restaurant) {
		for (var m : restaurant.getMeals()) {
			if (m.getId() == null) {
				m.setId(++sequence);
			}
		}
		data.put(restaurant.getId(), restaurant);
		return restaurant;
	}

	public Restaurant findById(Long id) {
		return Optional.ofNullable(data.get(id)).orElseThrow(() -> new EntityNotFoundException());
	}

	public Collection<Restaurant> findAll() {
		return data.values();
	}

	@Override
	public Restaurant convert(String source) {
		return findById(Long.parseLong(source));
	}
}