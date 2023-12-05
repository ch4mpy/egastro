package de.egastro.training.oidc.domain.persistence;

import java.time.Instant;

import org.springframework.lang.Nullable;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InstantEpochSecondConverter implements AttributeConverter<Instant, Long> {

	@Override
	public Long convertToDatabaseColumn(Instant instant) {
		return toEpochSechond(instant);
	}

	@Override
	public Instant convertToEntityAttribute(Long epochSecond) {
		return toInstant(epochSecond);
	}
	
	public static Long toEpochSechond(@Nullable Instant instant) {
		return instant == null ? null : instant.getEpochSecond();
	}
	
	public static Instant toInstant(@Nullable Long epochSecond) {
		return epochSecond == null ? null : Instant.ofEpochSecond(epochSecond);
	}
}