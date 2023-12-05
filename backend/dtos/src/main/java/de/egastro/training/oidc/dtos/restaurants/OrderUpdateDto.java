package de.egastro.training.oidc.dtos.restaurants;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public record OrderUpdateDto(Long engagedFor, Long readyAt, Long pickedAt) {

}
