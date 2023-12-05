package de.egastro.training.oidc.domain.persistence;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {
	private static final String SPLIT_CHAR = ";";

	@Override
	public String convertToDatabaseColumn(Set<String> stringList) {
		return stringList != null ? String.join(SPLIT_CHAR, stringList) : "";
	}

	@Override
	public Set<String> convertToEntityAttribute(String string) {
		return string != null ? Stream.of(string.split(SPLIT_CHAR)).collect(Collectors.toSet()) : Set.of();
	}
}