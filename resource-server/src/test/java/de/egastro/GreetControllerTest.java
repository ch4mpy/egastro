package de.egastro;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = GreetController.class)
@Import(SecurityConfiguration.class)
class GreetControllerTest {
	@Autowired
	MockMvc api;

	@MockBean
	RestaurantRepository restaurantRepo;

	@MockBean
	MealRepository mealRepo;

	final ObjectMapper om = new ObjectMapper();

	static final Restaurant sushibach = new Restaurant(42L, "Sushi Bach", List.of("thom"), new ArrayList<>());
	static final Meal ch4mpMeal = new Meal("ch4mp");
	static final Meal tontonPirateMeal = new Meal("tonton-pirate");

	@BeforeEach
	public void setup() {
		when(restaurantRepo.convert(sushibach.getId().toString())).thenReturn(sushibach);
		when(mealRepo.convert("1")).thenReturn(ch4mpMeal);
		when(mealRepo.convert("2")).thenReturn(tontonPirateMeal);
	}

	@Test
	@WithAnonymousUser
	void givenTheRequestIsAnonymous_whenGetGreet_thenUnauthorized() throws Exception {
		api.perform(get("/greet")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithJwt("thom.json")
	void givenUserIsThom_whenGetGreet_thenOk() throws Exception {
		api
				.perform(get("/greet"))
				.andExpect(status().isOk())
				.andExpect(
						MockMvcResultMatchers
								.jsonPath("$.message")
								.value(
										"Hello thom!, you are authenticated in \"master\" realm, are granted with [default-roles-master, offline_access, uma_authorization], manage [42] and work at [42]"));
	}

	@Test
	@WithAnonymousUser
	void givenRequestIsAnonymous_whenGetUserEmployers_thenUnauthorized() throws Exception {
		api.perform(get("/users/thom/employers")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithJwt("thom.json")
	void givenUserIsThom_whenGetUserEmployers_thenForbidden() throws Exception {
		api.perform(get("/users/thom/employers")).andExpect(status().isForbidden());
	}

	@Test
	@WithJwt("keycloak-mapper.json")
	void givenUserIsKeycloakMapper_whenGetUserEmployers_thenOk() throws Exception {
		api.perform(get("/users/thom/employers")).andExpect(status().isOk());
	}

	@Test
	@WithJwt("ch4mp.json")
	void givenUserIsCh4mp_whenCreateMealHeAtsushibach_thenCreated() throws Exception {
		api
				.perform(
						post("/restaurants/42/meals")
								.contentType(MediaType.APPLICATION_JSON)
								.content(om.writeValueAsString(new GreetController.MealUpdateDto("test"))))
				.andExpect(status().isCreated());
	}

	@Test
	@WithJwt("thom.json")
	void givenUserIsThom_whenGetsushibachMealFromSomeoneElse_thenOk() throws Exception {
		api.perform(get("/restaurants/42/meals/1")).andExpect(status().isOk());
	}

	@Test
	@WithJwt("ch4mp.json")
	void givenUserIsCh4mp_whenGetsushibachMealHeOrdered_thenOk() throws Exception {
		api.perform(get("/restaurants/42/meals/1")).andExpect(status().isOk());
	}

	@Test
	@WithJwt("ch4mp.json")
	void givenUserIsCh4mp_whenGetsushibachMealFromSomeoneElse_thenForbidden() throws Exception {
		api.perform(get("/restaurants/42/meals/2")).andExpect(status().isForbidden());
	}

}
