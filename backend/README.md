# OIDC Training by Jérôme Wacongne - Backend
This labs aim at getting confident to:
- configure Spring OAuth2 clients
- configure Spring OAuth2 resource servers
- implement & test access control rules with Spring Security

## 1. Accept tokens issued by the master realm

### 1.1. Resource server configugation with just official starters
#### 1.1.1. Test drive
Create a new Spring Boot project with:
- maven
- JDK 21
- Spring Web
- OAuth2 Resource Server
- Spring Boot DevTools

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

#### 1.1.2. Resource server explicit configuration
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

As we can see in the answer in Postman, there are aspects of this configuration that we would probably like to change:
- using the `preferred_username` claim as username (instead of `sub`)
- using the `realm_access.roles` claim as source for authorities

Both are done with by configuring the authentication converter:
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

### 1.1.3. Customizing the `Authentication`
At eGastro, we'd like to know the realm in which the resource owner was authenticated. We'd also like to know which restaurant he manages and which employ him:
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
		public List<String> getManages() {
			return (List<String>) jwt.getClaims().getOrDefault("manages", List.of());
		}

		@SuppressWarnings("unchecked")
		public List<String> getWorksAt() {
			return (List<String>) jwt.getClaims().getOrDefault("worksAt", List.of());
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
				"Hello %s!, you are authenticated in %s, are granted with %s, manage %s and work at %s"
						.formatted(auth.getName(), auth.getRealm(), auth.getAuthorities(), auth.getManages(), auth.getWorksAt()));
	}

	static record GreetingDto(String message) {
	}
}
```

## 2. Implement role based access control

## 3. Unit-test access control

## 4. Enhanced Authentication with domain specific data

## 5. Advanced access control rules

## 6. Dynamic multi-tenancy (accept tokens from any realm)
