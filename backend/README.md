# OIDC Training by Jérôme Wacongne - Backend

This labs aim at getting confident to:

- configure Spring OAuth2 clients
- configure Spring OAuth2 resource servers
- implement & test access control rules with Spring Security

## Table of content

- [1. Accept tokens issued by the master realm](#master-realm)
  - [1.1. Test drive](#master-realm-resource-server-test-drive)
  - [1.2. Resource server explicit configuration](#master-realm-resource-server-basic-conf)
  - [1.3. Customizing the `Authentication`](#master-realm-resource-server-custom-auth)
- [2. Implement simple access control](#simple-access-control)
- [3. Unit-test access control](#access-control-test)
- [4. Advanced access control rules](#access-control-advanced)
  - [4.1. RBAC](#access-control-rbac)
  - [4.2. Security expressions with method parameters](#access-control-security-expressions-with-parameters)
    - [4.2.1. Minimal domain](#access-control-security-expressions-with-parameters-domain)
    - [4.2.2. Secured REST endpoints](#access-control-security-expressions-with-parameters-endpoints)
    - [4.2.3. Unit tests](#access-control-security-expressions-with-parameters-tests)
  - [4.3. Security expressions factorization](#access-control-security-expressions-factor)
- [5. Dynamic multi-tenancy (accept tokens from any realm)](#dynamic)
- [6. `spring-cloud-gateway` as BFF](#client)
  - [6.1. OAuth2 client with `authorization_code`](#client-login)
    - [6.1.1. RP-Initiated Logout](#client-logout)
    - [6.1.2. CSRF protection for SPAs](#client-csrf)
    - [6.1.3. Status and location of BFF responses during login](#client-response-status)
    - [6.1.4. Adding request params to the authorization-code request](#client-auth-code-params)
    - [6.1.5. Mixing `oauth2Login` and `oauth2ResourceServer` in single application](#client-resource-server)
  - [6.2. `spring-cloud-gateway` configuration](#client-gateway)
- [7. Configuration cut-down with `spring-addons`](#spring-addons)
  - [7.1. `EGastroAuthentication`](#spring-addons-egastro-authentication)
  - [7.2. `EGastroMethodSecurityExpressionRoot`](#spring-addons-expression-root)
  - [7.3. `IssuerStartsWithAuthenticationManagerResolver`](#spring-addons-authentication-manager-resolver)
  - [7.4. OAuth2 REST API Configuration](#spring-addons-api-conf)
  - [7.5. BFF Configuration](#spring-addons-bff-conf)

## <a name="master-realm"/>1. Accept tokens issued by the master realm

As a 1st step, we'll see how to accept tokens issued by a single Keycloak realm. We'll later see how to trust tokens from any realm on a given Keycloak server.

As a last step, we'll see that additional (3rd party) Spring Boot starters can ease our life when configuring Spring application with OAuth2. But to avoid any vendor lock-in, let's 1st see what it takes to write security configuration without it.

#### <a name="master-realm-resource-server-test-drive"/>1.1. Test drive

Create a new Spring Boot project with:

- maven
- JDK 21
- Spring Web
- OAuth2 Resource Server
- Spring Boot DevTools
- Lombok

Rename `application.properties` to `application.yml` and add the following:

```yaml
server:
  port: 7084

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://localhost:8443/realms/master

logging:
  level:
    root: INFO
    org:
      springframework:
        security: DEBUG
        boot: INFO
```

Add the following controller:

```java
@RestController
public class GreetController {

    @GetMapping("/greet")
    public GreetingDto greet(Authentication auth) {
        return new GreetingDto("Hello %s!, you are granted with %s".formatted(auth.getName(), auth.getAuthorities()));
    }

    static record GreetingDto(String message) {
    }
}
```

Add the following `SecurityConfiguration`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
}
```

Run as Spring Boot application and try with Postman

#### <a name="master-realm-resource-server-basic-conf"/>1.2. Resource server explicit configuration

To mimic the default configuration, we can define:

```java
@Bean
SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
    http
            .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
            }));
    return http.build();
}
```

As we can see in the answer from Postman, there are aspects of this configuration that we would probably like to change:

- using the `preferred_username` claim as username (instead of `sub`)
- using the `realm_access.roles` claim as source for authorities

Both are done by configuring the authentication converter:

```java
@Bean
SecurityFilterChain resourceServerFilterChain(HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) throws Exception {
    http
            .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(resourceServer -> resourceServer.jwtAuthenticationConverter(authenticationConverter)))
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
            }));
    return http.build();
}

@Bean
@SuppressWarnings("unchecked")
IJwtAuthenticationConverter authenticationConverter() {
    return jwt -> {
        final var realmAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("realm_access", Map.of());
        final var realmRoles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        final var authorities = realmRoles.stream().map(SimpleGrantedAuthority::new).toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME));
    };
}

static interface IJwtAuthenticationConverter extends Converter<Jwt, AbstractAuthenticationToken> {
}
```

### <a name="master-realm-resource-server-custom-auth"/>1.3. Customizing the `Authentication`

At eGastro, we'd like to know the realm in which the resource owner was authenticated. This is an information that we can get from the `iss` claim.

We'd also like to know which restaurant he manages and which employ him. This info is added to tokens as private claims by a custom "mapper" (see [Keycloak lab](https://github.com/ch4mpy/egastro/blob/main/keycloak.md)).

```java
@Bean
SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
    http
            .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
            .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(resourceServer -> resourceServer.jwtAuthenticationConverter(EGastroAuthentication::new)))
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
            }));
    return http.build();
}

static class EGastroAuthentication extends AbstractAuthenticationToken {
    private static final long serialVersionUID = -6421797824331073601L;

    private final Jwt jwt;
    private final String realm;

    public EGastroAuthentication(Jwt jwt) {
        super(extractAuthorities(jwt));
        setAuthenticated(jwt != null);
        this.jwt = jwt;
        setDetails(jwt);
        final var splits = jwt.getClaimAsString(JwtClaimNames.ISS).split("/");
        this.realm = splits.length > 0 ? splits[splits.length - 1] : null;

    }

    public String getRealm() {
        return realm;
    }

    @SuppressWarnings("unchecked")
    public List<Long> getManages() {
        return (List<Long>) jwt.getClaims().getOrDefault("manages", List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Long> getWorksAt() {
        return (List<Long>) jwt.getClaims().getOrDefault("worksAt", List.of());
    }

    @Override
    public String getName() {
        return jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME);
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    @Override
    public Jwt getPrincipal() {
        return jwt;
    }

    @Override
    public Jwt getDetails() {
        return jwt;
    }

    @SuppressWarnings("unchecked")
    static List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
        final var realmAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("realm_access", Map.of());
        final var realmRoles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        return realmRoles.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
```

Which allow us to enhance the controller as follow:

```java
@RestController
public class GreetController {

    @GetMapping("/greet")
    public GreetingDto greet(EGastroAuthentication auth) {
        return new GreetingDto(
                "Hello %s!, you are authenticated in \"%s\" realm, are granted with %s, manage %s and work at %s"
                        .formatted(auth.getName(), auth.getRealm(), auth.getAuthorities(), auth.getManages(), auth.getWorksAt()));
    }

    static record GreetingDto(String message) {
    }
}
```

Be careful that **injecting a `EGastroAuthentication` (instead of the very generic `Authentication`) is safe only because we have a security configuration specifying that the requests should be authenticated**. If the access to `/greet` endpoint was allowed to anonymous requests, then we'd have to check wether the `Authentication` instance is an `AbstractAuthenticationToken` (would be the case for anonymous requests) or an `EGastroAuthentication` (would be the case for authenticated requests).

## <a name="simple-access-control"/>2. Implement simple access control

So far, all we have about access control is `.authorizeHttpRequests(authz -> authz.anyRequest().authenticated())` in the security configuration. It just checks that every request is authorized with a valid access token, which is the simplest possible access control, but in most cases, we will need to selectively remove this control for some resources to be exposed publicly (allow anonymous requests).

Let's update the Security conf to allow anonymous access to `/me`:

```java
        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/me").permitAll()
                .anyRequest().authenticated())
            ...
```

Now, we should be careful with the type of `Authentication` that we get at this `/me` endpoint as it could be:

- `AnonymousAuthenticationToken` for anonymous requests
- what our authentication converter returns (`EGastroAuthentication`) for authorized requests

```java
    @GetMapping("/me")
    public UserDto getMe(Authentication auth) {
        if (auth instanceof EGastroAuthentication egAuth) {
            return new UserDto(
                    egAuth.getRealm(),
                    egAuth.getName(),
                    egAuth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList(),
                    egAuth.getManages(),
                    egAuth.getWorksAt(),
                    egAuth.getPrincipal().getExpiresAt().getEpochSecond());
        }
        return UserDto.ANONYMOUS;
    }

    static record UserDto(String realm, String username, List<String> roles, List<Long> manages, List<Long> worksAt, Long exp) {
        static final UserDto ANONYMOUS = new GreetController.UserDto("", "", List.of(), List.of(), List.of(), 0L);
    }
```

Here we return something smart for authenticated requests (when the `Authentication` is a `EGastroAuthentication`) and a stub `ANONYMOUS` DTO for anonymous requests.

## <a name="access-control-test"/>3. Unit-test access control

To populate the test security context with mocked `Authentication` instances, we will use annotations from [spring-addons-oauth2-test](https://central.sonatype.com/search?q=spring-addons-oauth2-test). Let's add the following maven dependency:

```xml
<dependency>
    <groupId>com.c4-soft.springaddons</groupId>
    <artifactId>spring-addons-oauth2-test</artifactId>
    <version>7.1.16</version>
    <scope>test</scope>
</dependency>
```

One of the annotations this library provides is `@WithJwt` which scans the test context for an authentication converter bean (a `Converter<Jwt, Authentication>`) and then use it to create the `Authentication` instance to put in the test security context.

Let's modify a bit the security configuration to expose the authentication converter as a bean instead of just building it internally when configuring the `SecurityFilterChain`.

First expose the authentication converter bean:

```java
@Bean
IAuthenticationConverter authenticationConverter() {
    return EGastroAuthentication::new;
}

// hack to keep the info about Jwt and AbstractAuthenticationToken generics parameters for our authentication converter
static interface IAuthenticationConverter extends Converter<Jwt, AbstractAuthenticationToken> {
}
```

Then inject it into the `SecurityFilterChain` configurer:

```java
@Bean
SecurityFilterChain resourceServerFilterChain(HttpSecurity http, Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter)
        throws Exception {
    http.authorizeHttpRequests(authz -> {
            ...
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(resourceServer -> resourceServer.jwtAuthenticationConverter(authenticationConverter)))
            ...
    return http.build();
}
```

Now we can use `@WithJwt` in our unit-test

```java
@WebMvcTest(controllers = GreetController.class)
@Import(SecurityConfiguration.class)
class GreetControllerTest {
    @Autowired
    MockMvc api;

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

}
```

- `@WebMvcTest` is designed for unit-testing `@Controller` as efficiently as possible
- by default security configuration is not applied in a `@WebMvcTest`. `@Import(SecurityConfiguration.class)` forces the security conf to be loaded.
- `@WithAnonymousUser` is an annotation from spring-security-test to simulate unauthorized requests
- `@WithJwt("thom.json")` loads a JSON payload from the test resources and uses the `authenticationConverter` `@Bean` from our `SecurityConfiguration` to create an `EGastroAuthentication` and set the test security context with it.

Of course, for the test to pass, we have to define this `thom.json` in `src/test/resources`:

```json
{
  "iss": "https://localhost:8443/realms/master",
  "sub": "17245c56-34bb-4f8d-8db3-52d4747c915b",
  "realm_access": {
    "roles": [
      "default-roles-master",
      "offline_access",
      "uma_authorization"
    ]
  },
  "resource_access": {
    "account": {
      "roles": [
        "manage-account",
        "manage-account-links"
        "view-profile"
      ]
    }
  },
  "scope": "openid email profile offline_access",
  "name": "Thom Bach",
  "preferred_username": "thom",
  "given_name": "Thom",
  "family_name": "Bach",
  "email": "thom@sushibach.de",
  "manages": [
    "Sushi Bach"
  ],
  "worksAt": [
    "Sushi Bach"
  ]
}
```

## <a name="access-control-advanced"/>4. Advanced access control rules

The access control we used so far is very basic: check if a request is authorized. Most of the time, we'll need something smarter.

### <a name="access-control-rbac"/>4.1. RBAC

Role Based Access Control is a very common model for authorization. Let's see how to implement it with Spring Security.

In Keycloak, roles are roles, but it can be defined at realm and client level. So you have `realm_access.roles` and `resource_access.{client-id}.roles`.

In spring, roles are called "authorities" and implement the `GrantedAuthority` interface. It is, with username, one of the two main properties contained by the `Authentication` instance in the security context.

We saw already how to map Keycloak roles to Spring authorities with an authorities converter in the authentication converter (turn the `realm_access.roles` entries into authorities in the `EGastroAuthentication`). Let's now see how we can assert that a user is granted with a given role to access a resource.

Let's add `GET` endpoint to `/users/{realm}/{username}/employers` to our controller. This endpoint will provide a list of restaurants from a given realm which are employing a given user. Also, we'll specify that only requests authorized with the `KEYCLOAK_MAPPER` authority can get this data.

```java
@GetMapping("/users/{username}/employers")
@PreAuthorize("hasAuthority('KEYCLOAK_MAPPER')")
public List<Long> getUserEmployers(@PathVariable("username") String username) {
    // An actual implementation would probably use a DB repository (or another micro-service) to retrieve the list of restaurants from the realm
    // and filter those the user works for
    return List.of(42);
}
```

We can now add new tests to assert that this `@PreAuthorize` expression behaves as expected:

```java
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
```

With the following `src/test/resources/keycloak-mapper.json`:

```json
{
  "iss": "https://localhost:8443/realms/master",
  "sub": "ce5c348e-cde1-45ae-9a13-c33729efb6ed",
  "realm_access": {
    "roles": [
      "default-roles-master",
      "offline_access",
      "uma_authorization",
      "KEYCLOAK_MAPPER"
    ]
  },
  "scope": "openid email profile offline_access",
  "email_verified": false,
  "preferred_username": "service-account-restaurants-employees-mapper",
  "client_id": "restaurants-employees-mapper"
}
```

Note that according to our test resources, `keycloak-mapper` token is granted with `KEYCLOAK_MAPPER` "realm" role but that `thom` token isn't.

### <a name="access-control-security-expressions-with-parameters"/>4.2. Security expressions with method parameters

Sometimes, evaluating authorities is not enough to grant access to a resource and we need to check accessed entities relations.

For this sample, let's consider this business rules:

- a `Restaurant` has employees
- any authenticated user can order a `Meal` from a restaurant
- only the user who ordered a meal and restaurant employees can access this meal (read or update)

#### <a name="access-control-security-expressions-with-parameters-domain"/>4.2.1. Minimal domain

```java
@Data
static class Restaurant {

    private final Long id;

    private final String name;

    private final List<String> employees;

    private final List<Meal> meals;
}

@Data
static class Meal {

    private Long id;

    private final String orderedBy;

    private String description;
}

static record MealUpdateDto(String description) {
}

@Repository
static class RestaurantRepository implements Converter<String, Restaurant> {
    private final Map<Long, Restaurant> data = new HashMap<>();
    private long sequence = 0L;

    public RestaurantRepository() {
        final var sushibach = new Restaurant(42L, "Sushi Bach", List.of("thom"), new ArrayList<>());
        this.data.put(sushibach.getId(), sushibach);
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

@Repository
@RequiredArgsConstructor
static class MealRepository implements Converter<String, Meal> {
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

@ResponseStatus(HttpStatus.NOT_FOUND)
static class EntityNotFoundException extends RuntimeException {
    private static final long serialVersionUID = -3913937023323378085L;
}
```

#### <a name="access-control-security-expressions-with-parameters-endpoints"/>4.2.2. Secured REST endpoints

With the domain above, we can implement the security rules as follow:

```java
@PostMapping("/restaurants/{restaurantId}/meals")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Meal> createMeal(@PathVariable("restaurantId") Restaurant restaurant, @RequestBody MealUpdateDto dto, EGastroAuthentication auth)
        throws URISyntaxException {
    final var meal = new Meal(auth.getName());
    meal.setDescription(dto.description());
    restaurant.getMeals().add(meal);
    restaurantRepo.save(restaurant);

    return ResponseEntity.created(new URI("/restaurants/%s/meals/%d".formatted(restaurant.getId(), meal.getId()))).body(meal);
}

@GetMapping("/restaurants/{restaurantId}/meals/{mealId}")
@PreAuthorize("#meal.orderedBy == authentication.name || #restaurant.employees.contains(authentication.name)")
public Meal retrieveMeal(@PathVariable("restaurantId") Restaurant restaurant, @PathVariable("mealId") Meal meal) {

    return meal;
}

@PutMapping("/restaurants/{restaurantId}/meals/{mealId}")
@PreAuthorize("#meal.orderedBy == authentication.name || #restaurant.employees.contains(authentication.name)")
public ResponseEntity<Void> updateMeal(
        @PathVariable("restaurantId") Restaurant restaurant,
        @PathVariable("mealId") Meal meal,
        @RequestBody MealUpdateDto dto) {
    meal.description = dto.description();

    return ResponseEntity.accepted().build();
}
```

Note how we used the `authentication` "magic" variable as well as controller methods arguments in Spring Security SpEL expressions

#### <a name="access-control-security-expressions-with-parameters-tests"/>4.2.3. Unit tests

We'll first need a new persona for our test (someone who does not work at Sushi Bach). Let's define `src/test/resources/ch4mp.json`:

```json
{
  "iss": "https://localhost:8443/realms/master",
  "aud": "account",
  "sub": "17245c56-34bb-4f8d-8db3-52d4747c915b",
  "typ": "Bearer",
  "realm_access": {
    "roles": ["default-roles-master", "offline_access", "uma_authorization"]
  },
  "resource_access": {
    "account": {
      "roles": ["manage-account", "manage-account-links", "view-profile"]
    }
  },
  "scope": "openid email profile offline_access",
  "email_verified": true,
  "name": "Jérôme Wacongne",
  "preferred_username": "ch4mp",
  "given_name": "Jérôme",
  "family_name": "Wacongne",
  "email": "ch4mp@c4-soft.com"
}
```

And let's add some repos mocking to the tests:

```java
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
```

Now, we can test our new security expressions with:

```java
@Test
@WithJwt("ch4mp.json")
void givenUserIsCh4mp_whenCreateMealHeAtSushibach_thenCreated() throws Exception {
    api
            .perform(
                    post("/restaurants/42/meals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(new GreetController.MealUpdateDto("test"))))
            .andExpect(status().isCreated());
}

@Test
@WithJwt("thom.json")
void givenUserIsThom_whenGetSushibachMealFromSomeoneElse_thenOk() throws Exception {
    api.perform(get("/restaurants/42/meals/1")).andExpect(status().isOk());
}

@Test
@WithJwt("ch4mp.json")
void givenUserIsCh4mp_whenGetSushibachMealHeOrdered_thenOk() throws Exception {
    api.perform(get("/restaurants/42/meals/1")).andExpect(status().isOk());
}

@Test
@WithJwt("ch4mp.json")
void givenUserIsCh4mp_whenGetSushibachMealFromSomeoneElse_thenForbidden() throws Exception {
    api.perform(get("/restaurants/42/meals/2")).andExpect(status().isForbidden());
}
```

### <a name="access-control-security-expressions-factor"/>4.3. Security expressions factorization

Expressions like `#meal.orderedBy == authentication.name || #restaurant.employees.contains(authentication.name)` can become quite big and for some, repeated at many places. There are two main options for security expressions code factorization:

- the "official" way by implementing `PermissionEvaluator` and then using the `hasPermission()` in security expressions
- the "hacked" way by extending the `C4MethodSecurityExpressionRoot` to easily define a new DSL

With the second option, we may define:

```java
@Bean
MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
    return new C4MethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
}

static final class EGastroMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

    public boolean worksFor(Restaurant restaurant) {
        return restaurant.getEmployees().contains(getAuthentication().getName());
    }

    public boolean hasOrdered(Meal meal) {
        return Objects.equals(meal.getOrderedBy(), getAuthentication().getName());
    }
}
```
This requires to add a dependency on [spring-addons-oauth2](https://central.sonatype.com/search?q=spring-addons-oauth2), but `#meal.orderedBy == authentication.name || #restaurant.employees.contains(authentication.name)` can be changed to `hasOrdered(#meal) || worksFor(#restaurant)`

## <a name="dynamic-multi-tenant"/>5. Dynamic multi-tenancy (accept tokens from any realm)

So far, our resource server accepts only tokens issued by the master realm of our local Keycloak instance. According to [Spring Security documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html#\_dynamic_tenants), we should provide with our own `AuthenticationManagerResolver<HttpServletRequest>`.

Unfortunately, the authentication converter is not configurable on the authentication manager resolvers returned by the static methods on `JwtIssuerAuthenticationManagerResolver`. So let's define our own `AuthenticationManagerResolver<String>` (using the authentication converter we already have) and pass that to the `JwtIssuerAuthenticationManagerResolver` constructor.

```java
static class IssuerStartsWithAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

    private final String keycloakHost;
    private final Converter<Jwt, AbstractAuthenticationToken> authenticationConverter;
    private final Map<String, AuthenticationManager> jwtManagers = new ConcurrentHashMap<>();

    public IssuerStartsWithAuthenticationManagerResolver(String keycloakHost, Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {
        super();
        this.keycloakHost = keycloakHost.toString();
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public AuthenticationManager resolve(String issuer) {
        if (!jwtManagers.containsKey(issuer)) {
            if (!issuer.startsWith(keycloakHost)) {
                throw new UnknownIssuerException(issuer);
            }
            final var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            var provider = new JwtAuthenticationProvider(decoder);
            provider.setJwtAuthenticationConverter(authenticationConverter);
            jwtManagers.put(issuer, provider::authenticate);
        }
        return jwtManagers.get(issuer);

    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    static class UnknownIssuerException extends RuntimeException {
        private static final long serialVersionUID = 4177339081914400888L;

        public UnknownIssuerException(String issuer) {
            super("Unknown issuer: %s".formatted(issuer));
        }
    }

}

@Bean
AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
        @Value("${keycloak-host}") URI keycloakHost,
        Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {

    return new JwtIssuerAuthenticationManagerResolver(new IssuerStartsWithAuthenticationManagerResolver(keycloakHost.toString(), authenticationConverter));
}
```

All we need after that is updating the `SecurityFilterChain` configuration to set the `oauth2ResourceServer` with an `authenticationManagerResolver` instead of `jwt` with custom `jwtAuthenticationConverter`:

```java
@Bean
SecurityFilterChain resourceServerFilterChain(HttpSecurity http, AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver)
        throws Exception {
    http.authorizeHttpRequests(authz -> {
            authz
                .requestMatchers("/me").permitAll()
                .anyRequest().authenticated();
        })
        .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver))
        .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
            response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }));
    return http.build();
}
```

## <a name="client"/>6. `spring-cloud-gateway` as BFF

The requests to the authorization server we built so far need `Bearer` tokens in the `Authorization` header to be authorized. This **tokens are issued by an authorization server (Keycloak in our case) to an OAuth2 client**.

Not so long ago, it was pretty usual to configure the frontends as "public" OAuth2 clients: the authorization-code callback was to the frontend which collected the tokens from the authorization server and storing it on the end-user device. This frontend could then call Spring resource server without the need of a Spring OAuth2 client.

However, according to the [latest recommendations](https://github.com/spring-projects/spring-authorization-server/issues/297#issue-896744390), we should use only "confidential" OAuth2 clients.

Let's create a new project with:

- Gateway
- OAuth2 Client
- OAuth2 Resource Server
- Actuator
- Spring Boot DevTools
- Lombok

Please note that since the very recent `2023.0.0` (released December the 7th 2023), the default is the new servlet version of Spring Cloud Gateway (`spring-cloud-gateway-mvc`, when it was `spring-cloud-gateway`, a reactive application, before that).

### <a name="client-login"/>6.1. OAuth2 client with `authorization_code`

What we'll see in this section is how to configure a Spring application as an OAuth2 client with `authorization_code` flow (so called `oauth2Login` in Spring). This will be the foundation for configuring `spring-cloud-gateway` as a BFF for single page and mobile applications.

For this part, the `spring-boot-starter-actuator`, `spring-boot-starter-oauth2-resource-server` and `spring-cloud-starter-gateway-mvc` are actually not needed. If you comment it in the pom, just add `spring-boot-starter-web`.

An OAuth2 client with `oauth2Login` can be configured with just this properties:

```java
scheme: http
keycloak-host: https://localhost:8443
master-issuer: ${keycloak-host}/realms/master
bff-secret: change-me

server:
  ssl:
    enabled: false
  port: 7080

spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            issuer-uri: ${master-issuer}
            user-name-attribute: preferred_username
        registration:
          egastro-bff:
            provider: keycloak
            client-id: egastro-bff
            client-secret: ${bff-secret}
            authorization-grant-type: authorization_code
            scope:
            - openid
            - profile
            - email
            - offline_access

logging:
  level:
    root: INFO
    org:
      springframework:
        security: TRACE
        boot: INFO

---
spring:
  config:
    activate:
      on-profile: ssl

server:
  ssl:
    enabled: true

scheme: https
```

For historical reasons, we should not use port `8080` on a servlet OAuth2 client with SSL enabled (Spring will force redirection to 8443, which is not a port our client listens to). So, force the port to anything else than `8080` if SSL is enabled and adapt the allowed redirect URIs in Keycloak.

This is enough for login to work, but quite a few features are missing to query such a Spring application from a SPA. Let's define a minimal `SecurityConf` and improve it incrementally:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain clientSecurityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http.oauth2Login(Customizer.withDefaults());
        http.authorizeHttpRequests(ex -> ex.anyRequest().authenticated());
        return http.build();
    }

}
```

This does the same as we had before: an app with sessions, CSRF protection, `authorization_code` flow, and all routes requiring an authorized session (but a few internal path managed internally by Spring for the authorization flow to work).

#### <a name="client-logout"/>6.1.1. RP-Initiated Logout

Default logout ends the session only on this client, but the user also has a session on the authorization server. As response to `/logout` on the client, we would need to receive a redirection to Keycloak's `end_session` endpoint to close the session there too (otherwise, the next `authorization_code` flow will run silently and the user will feel like he never logged-out).

Spring has an `OidcClientInitiatedLogoutSuccessHandler` for authorization servers complying to the RP-Initiated Logout standard from OIDC. Keycloak implementing this standard, we can configure the following:

```java
http.logout(logout -> {
    logout.logoutSuccessHandler(new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository));
});
```

In the case of a single page or mobile application consuming this client, if we stick to the standard `302` response status with `Location` header to the `end_session` endpoint, the request will probably be rejected because of it's origin. To avoid that, an option is to:

- set the `/logout` response status in the `2xx` range
- parse the `Location` header in the frontend
- set the `window.location.href` (in a SPA, or just send a new request in a mobile app).

Let's configure the logout success handler a little further:

```java
http.logout(logout -> {
    final var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    logoutSuccessHandler.setRedirectStrategy((HttpServletRequest request, HttpServletResponse response, String location) -> {
        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setHeader(HttpHeaders.LOCATION, location);
    });
    logout.logoutSuccessHandler(logoutSuccessHandler);
});
```

#### <a name="client-csrf"/>6.1.2. CSRF protection for SPAs

Single page applications querying a BFF (and mobile applications sharing the same BFF) need access to the CSRF token and return it with their requests modifying the state of the server (`POST`, `PUT`, `PATCH` and `DELETE`). The usual convention is to read the token value from a `XSRF-TOKEN` cookie and to return it as `X-XSRF-TOKEN` header. This requires Spring to use a "cookie" repository for CSRF and this cookie value to be accessible from Javascript (`http-only` flag set to `false`). According to [the latest docs](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa), we should also use a specific `CsrfTokenRequestAttributeHandler` and register a web filter.

[This should be simplified soon](https://github.com/spring-projects/spring-security/issues/14149), but for now, here is what we should do:

```java
@Bean
SecurityFilterChain clientSecurityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
    http.oauth2Login(Customizer.withDefaults());
    http.csrf(csrf -> {
        csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler());
    });
    http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

    ...
    return http.build();
}

static class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}

static class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        csrfToken.getToken();

        filterChain.doFilter(request, response);
    }
}
```

#### <a name="client-response-status"/>6.1.3. Status and location of BFF responses during login

In the case of a login from mobile application, we need to send the request to the **BFF** authorization endpoint with the app internal HTTP client to ensure that a session is initiated and attached to it on the BFF, but we should follow the redirection to the **authorization server** authorization endpoint with the system browser for login forms to be rendered and to best benefit from SSO. For that, we'll have to change the status of the response from the BFF, mostly like we did for the logout response.

One difference thought: we want this to happen only when the frontend is a mobile app, not a SPA. To achieve this, we'll write a redirection handler with a default value that we can override with a custom header (if present, the requested status will be returned).

As a bonus, we might want to define the post login URI as a request header too:

```java
@RequiredArgsConstructor
static class ConfigurableStatusRedirectStrategy implements RedirectStrategy {
    static final String RESPONSE_STATUS_HEADER = "X-RESPONSE-STATUS";
    static final String RESPONSE_STATUS_LOCATION = "X-RESPONSE-LOCATION";
    private final HttpStatus defaultStatus;

    @Override
    public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
        final var requestedStatus = request.getIntHeader(RESPONSE_STATUS_HEADER);
        response.setStatus(requestedStatus > -1 ? requestedStatus : defaultStatus.value());

        final var location = Optional.ofNullable(request.getHeader(RESPONSE_STATUS_LOCATION)).orElse(url);
        response.setHeader(HttpHeaders.LOCATION, location);
    }
}
```

We can then update our filter-chain configuration as follows:

```java
http.oauth2Login(login -> {
    login.authorizationEndpoint(authorizationEndpoint -> {
        authorizationEndpoint.authorizationRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
    });
    final var successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
    successHandler.setRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
    login.successHandler(successHandler);
});
http.logout(logout -> {
    final var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    logoutSuccessHandler.setRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.ACCEPTED));
});
```

#### <a name="client-auth-code-params"/>6.1.4. Adding request params to the authorization-code request

When using social login, it can be useful to add a `kc_idp_hint` request parameter when redirecting a user to Keycloak for authentication: this will skip the Keycloak form and redirect the user to the external identity provider. For that, we should provide a custom `OAuth2AuthorizationRequestResolver`. Again, we'll write one which reads the value from a header to enable the frontend to set this hint:

```java
@Component
static class KcIdpHintAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
    static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public KcIdpHintAwareOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        final var req = delegate.resolve(request);
        Optional.ofNullable(request.getHeader(KC_IDP_HINT_HEADER)).ifPresent(hint -> req.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
        return req;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        final var req = delegate.resolve(request, clientRegistrationId);
        Optional.ofNullable(request.getHeader(KC_IDP_HINT_HEADER)).ifPresent(hint -> req.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
        return req;
    }
}
```

Then, we just configure the security filter chain to use it:

```java
@Bean
SecurityFilterChain clientSecurityFilterChain(
        HttpSecurity http,
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizationRequestResolver authorizationRequestResolver)
        throws Exception {
    http.oauth2Login(login -> {
        login.authorizationEndpoint(authorizationEndpoint -> {
            authorizationEndpoint.authorizationRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
            authorizationEndpoint.authorizationRequestResolver(authorizationRequestResolver);
        });
        final var successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setRedirectStrategy(new ConfigurableStatusRedirectStrategy(HttpStatus.FOUND));
        login.successHandler(successHandler);
    });
    ...
    return http.build();
}
```

#### <a name="client-resource-server"/>6.1.5. Mixing `oauth2Login` and `oauth2ResourceServer` in single application

As we saw already, `oauth2Login` requires sessions. But maintaining sessions consumes resources and some endpoints on our BFF won't need sessions. This is the case for instance for most public endpoints or REST resources like actuator endpoints. To avoid maintaining sessions when it is not needed, we can set a `securityMatcher` to our `clientSecurityFilterChain` and add a `resourceServerFilterChain`, with lower priority, which would act as default and process all requests which didn't match the `clientSecurityFilterChain`.

Ensure that dependencies to `spring-boot-starter-actuator` and `spring-boot-starter-oauth2-resource-server` are not commented anymore.

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
SecurityFilterChain clientSecurityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
    http.securityMatcher("/bff/**");
    ...
    return http.build();
}

@Bean
@Order(Ordered.LOWEST_PRECEDENCE)
SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http,
        @Value("${permit-all:[]}") String[] permitAll)
        throws Exception {
    http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(csrf -> csrf.disable());
    http
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtResourceServer -> {
                final var authenticationConverter = new JwtAuthenticationConverter();
                authenticationConverter.setPrincipalClaimName(StandardClaimNames.PREFERRED_USERNAME);
                authenticationConverter.setJwtGrantedAuthoritiesConverter(
                (Jwt jwt) -> {
                    final var resourceAccess = (Map<String, Object>) jwt.getClaims().getOrDefault("resource_access", Map.of());
                    final var obsClientAccess = (Map<String, Object>) resourceAccess.getOrDefault("observability", Map.of());
                    final var realmRoles = (List<String>) obsClientAccess.getOrDefault("roles", List.of());
                    return realmRoles.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
                });
                jwtResourceServer.jwtAuthenticationConverter(authenticationConverter);
            }))
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Restricted Content\"");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
            }));
    // @formatter:off
    http.authorizeHttpRequests(ex -> ex
            .requestMatchers(permitAll).permitAll()
            .requestMatchers("/actuator/**").hasAuthority("OBSERVABILITY")
            .anyRequest().authenticated());
    // @formatter:on
    return http.build();
}
```

Here are the properties that you might add:

```yaml
permit-all: >
  /error,
  /ui/**,
  /direct/**,
  /v3/api-docs/**,
  /actuator/health/readiness,
  /actuator/health/liveness

management:
  endpoint:
    health:
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: "*"
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

### <a name="client-gateway"/>6.2. `spring-cloud-gateway` configuration

First ensure that `spring-cloud-starter-gateway-mvc` is listed in your dependencies (you can remove any explicit dependency on `spring-boot-starter-web`)

The gateway configuration is done using application properties which are pretty self-explanatory:

```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          # Redirection from / to /ui/
          - id: home
            uri: ${gateway-uri}
            predicates:
              - Path=/
            filters:
              - RedirectTo=301,${gateway-uri}/ui/
          # Serve the SPA through the gateway (requires it to have the /ui baseHref)
          - id: ui
            uri: ${ui-host}
            predicates:
              - Path=/ui/**
          # Access the API with session and the TokenRelay filter
          - id: bff
            uri: ${management-console-api-uri}
            predicates:
              - Path=/bff/v1/**
            filters:
              - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin Access-Control-Request-Method Access-Control-Request-Headers
              - TokenRelay=
              - SaveSession
              - StripPrefix=2
          # Access the API as an OAuth2 client (without the TokenRelay filter)
          - id: bff
            uri: ${management-console-api-uri}
            predicates:
              - Path=/direct/v1/**
            filters:
              - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin Access-Control-Request-Method Access-Control-Request-Headers
              - StripPrefix=2
```

## <a name="spring-addons"/>7. Configuration cut-down with `spring-addons`

Using [`spring-addons-starter-oidc`](https://central.sonatype.com/search?q=spring-addons-starter-oidc), we can greatly reduce the amount of Java code for security configuration. It is an [open-source project](https://github.com/ch4mpy/spring-addons) I maintain.

### <a name="spring-addons-egastro-authentication"/>7.1. `EGastroAuthentication`
Let's first re-define the EGastroAuthentication using some based classes from spring-addons:
```java
public class EGastroAuthentication extends OAuthentication<OpenidClaimSet> {
    private static final long serialVersionUID = -1325104147048800592L;

    public EGastroAuthentication(OpenidClaimSet claims, Collection<? extends GrantedAuthority> authorities, String tokenString) {
        super(claims, authorities, tokenString);
    }

    public String getRealm() {
        final var splits = getAttributes().getIssuer().toString().split("/");
        return splits.length > 0 ? splits[splits.length - 1] : null;
    }

    public List<String> getManages() {
        return this.getAttributes().getClaimAsStringList("manages");
    }

    public List<String> getWorksAt() {
        return this.getAttributes().getClaimAsStringList("worksAt");
    }
}
```
Quite more simple, isn't it?

### <a name="spring-addons-expression-root"/>7.2. `EGastroMethodSecurityExpressionRoot`
The expression root we used so far was already using spring-addons and the DSL is by nature specific to eGastro, so no simplification to expect:
```java
final class EGastroMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

	public boolean is(String username) {
		return Objects.equals(username, getAuthentication().getName());
	}

	public boolean worksFor(Restaurant restaurant) {
		return restaurant.getEmployees().contains(getAuthentication().getName()) || restaurant.getManagers().contains(getAuthentication().getName());
		/*
		 * alternative impl:
		 *
		 * if(getAuthentication() instanceof EGastroAuthentication egauth) { return egauth.getWorksAt().contains(restaurant.getId()) ||
		 * egauth.getManages().contains(restaurant.getId()); } return false;
		 */
	}

	public boolean worksFor(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return egauth.getWorksAt().contains(restaurantId) || egauth.getManages().contains(restaurantId);
		}
		return false;
	}

	public boolean manages(Restaurant restaurant) {
		return restaurant.getManagers().contains(getAuthentication().getName());
		/*
		 * alternative impl:
		 *
		 * if(getAuthentication() instanceof EGastroAuthentication egauth) { return egauth.getManages().contains(restaurant.getId()); } return false;
		 */
	}

	public boolean manages(Long restaurantId) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return egauth.getManages().contains(restaurantId);
		}
		return false;
	}

	public boolean hasPassed(Order order) {
		return Objects.equals(order.getCustomerName(), getAuthentication().getName());
	}

	public boolean isFromMasterOr(String realm) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return Objects.equals(realm, egauth.getRealm()) || Objects.equals("master", egauth.getRealm());
		}
		return false;
	}

	public boolean isFrom(String realm) {
		if (getAuthentication() instanceof EGastroAuthentication egauth) {
			return Objects.equals(realm, egauth.getRealm());
		}
		return false;
	}
}
```

### <a name="spring-addons-authentication-manager-resolver">7.3. `IssuerStartsWithAuthenticationManagerResolver`
There dynamic multi-tenancy strategy we use here is generic enough for `spring-addons` to implement it already. So we'll use the `IssuerStartsWithAuthenticationManagerResolver` implementation from the lib!

### <a name="spring-addons-api-conf">7.4. OAuth2 REST API Configuration
All we need is defining:
- a `@Bean` to change the way authorities mapping properties are resolved (use the `keycloak-host` configuration property instead of the token `iss` claim)
- a `@Bean` to override the default authentication converter to produce eGastro `Authentication` implementation
- a `@Bean` to override the default authentication manager resolver (accept tokens issued by any realm from our Keycloak server)
- a `@Bean` to add our custom DSL to Spring Security SpEL
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	ConfigurableClaimSetAuthoritiesConverter authoritiesConverter(@Value("${keycloak-host}") URI keycloakHost, SpringAddonsOidcProperties addonsProperties) {
		final var opProperties = addonsProperties.getOpProperties(keycloakHost.toString());
		return new ConfigurableClaimSetAuthoritiesConverter(claims -> opProperties.getAuthorities());
	}

	@Bean
	JwtAbstractAuthenticationTokenConverter authenticationFactory(
			@Value("${keycloak-host}") URI keycloakHost,
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsOidcProperties addonsProperties) {
		return jwt -> {
			final var opProperties = addonsProperties.getOpProperties(keycloakHost.toString());
			final var claims = new OpenidClaimSet(jwt.getClaims(), opProperties.getUsernameClaim());
			return new EGastroAuthentication(claims, authoritiesConverter.convert(claims), jwt.getTokenValue());
		};
	}

	@Bean
	AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
			@Value("${keycloak-host}") URI keycloakHost,
			Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) {
		return new JwtIssuerAuthenticationManagerResolver(new IssuerStartsWithAuthenticationManagerResolver(keycloakHost.toString(), authenticationConverter));
	}

	@Bean
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new C4MethodSecurityExpressionHandler(EGastroMethodSecurityExpressionRoot::new);
	}
}
```
The `SecurityFilterChain` bean is provided by `spring-addons` starter and configured to use the beans we defined here instead of its defaults and the following properties:
```yaml
keycloak-host: https://localhost:8443
username-claim: preferred_username

com:
  c4-soft:
    springaddons:
      oidc:
        ops:
        - iss: ${keycloak-host}
          username-claim: ${username-claim}
          authorities:
          - path: $.realm_access.roles
        resourceserver:
          permit-all:
          - "/error"
          - "/users/me"
          - "/actuator/health/readiness"
          - "/actuator/health/liveness"
          - "/v3/api-docs/**"
```

### <a name="spring-addons-bff-conf">7.5. BFF Configuration
Apparently, the `spring-cloud-gateway-mvc` is not completely stable yet and will have to use `spring-cloud-gateway`, but be careful that it is a reactive application expecting reactive security conf (`@EnableWebFluxSecurity` instead of `@EnableWebSecurity`, expose `SecurityWebFilterChain` instead of `SecurityFilterChain`, etc.). Hopefully, spring-addons auto-detects the application type and adapts its auto-configuration.

Again, what we need to define is limited to what is very specific to our use-case: the `KcIdpHintAwareOAuth2AuthorizationRequestResolver`
```java
@Component
static class KcIdpHintAwareOAuth2AuthorizationRequestResolver implements ServerOAuth2AuthorizationRequestResolver {
	static final String KC_IDP_HINT_HEADER = "X-KC-IDP-HINT";
	static final String KC_IDP_HINT_PARAM = "kc_idp_hint";

	private final DefaultServerOAuth2AuthorizationRequestResolver delegate;

	public KcIdpHintAwareOAuth2AuthorizationRequestResolver(ReactiveClientRegistrationRepository clientRegistrationRepository) {
		this.delegate = new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
	}

	@Override
	public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
		return delegate.resolve(exchange).map(request -> {
			Optional
					.ofNullable(exchange.getRequest().getHeaders().get(KC_IDP_HINT_HEADER))
					.ifPresent(hint -> request.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
			return request;
		});
	}

	@Override
	public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String clientRegistrationId) {
		return delegate.resolve(exchange, clientRegistrationId).map(request -> {
			Optional
					.ofNullable(exchange.getRequest().getHeaders().get(KC_IDP_HINT_HEADER))
					.ifPresent(hint -> request.getAdditionalParameters().put(KC_IDP_HINT_PARAM, hint));
			return request;
		});
	}
}
```
The properties contains two sections one for the OAuth2 client filter chain and another for the resource server one:
```yaml
com:
  c4-soft:
    springaddons:
      oidc:
        # Global OAuth2 configuration
        ops:
        - iss: ${keycloak-host}
          username-claim: ${username-claim}
          authorities:
          - path: $.realm_access.roles
          - path: $.resource_access.observability.roles
        client:
          client-uri: ${gateway-uri}
          security-matchers:
          - /bff/**
          permit-all:
          - /bff/**
          csrf: cookie-accessible-from-js
          post-login-redirect-path: /ui/
          post-logout-redirect-path: /ui/
          oauth2-redirections:
            rp-initiated-logout: ACCEPTED
          cors:
          - allowed-origin-patterns: ${ui-host}
        # OAuth2 resource server configuration
        resourceserver:
          permit-all:
          - /error
          - /login-options
          - /ui/**
          - /direct/**
          - /v3/api-docs/**
          - /actuator/health/readiness
          - /actuator/health/liveness
          - /.well-known/**
          cors:
          - allowed-origin-patterns: ${ui-host}
```
We also need to update the gateway properties that we'd copy from our client project as the routes are defined slightly differently (no `mvc` between `gateway` and routes). Last, we can use default filters with the reactive gateway.
```yaml
spring:
  cloud:
    gateway:
      default-filters:
      - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      routes:
      # Redirection from / to /ui/
      - id: home
        uri: ${gateway-uri}
        predicates:
        - Path=/
        filters:
        - RedirectTo=301,${gateway-uri}/ui/
      # Serve the Angular app through the gateway
      - id: ui
        uri: ${ui-host}
        predicates:
        - Path=/ui/**
      # Access the API with BFF pattern
      - id: bff
        uri: ${management-console-api-uri}
        predicates:
        - Path=/bff/v1/**
        filters:
        - TokenRelay=
        - SaveSession
        - StripPrefix=2
      # Access the API as an OAuth2 client (without the TokenRelay filter)
      - id: bff
        uri: ${management-console-api-uri}
        predicates:
        - Path=/direct/v1/**
        filters:
        - StripPrefix=2
```
