package de.egastro.training.oidc.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record ErrorDto(@NotNull Integer status, String error, String message) {
}