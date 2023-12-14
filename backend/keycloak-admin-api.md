# OIDC Training by Jérôme Wacongne: Keycloak admin API
Keycloak exposes [a REST API](https://www.keycloak.org/docs-api/23.0.1/rest-api/index.html) manipulate about anything on the server. Of course these requests will have authorized.

Two options to set the Authorization header:
- client_credentials: "service-account" for your resource server => the resource server gets a new access token to act in its own name
- forwarding the access token of the user who originates the request
```java
@Component
@RequiredArgsConstructor
public class ClientBearerRequestInterceptor implements RequestInterceptor {
	private final OAuth2AuthorizedClientRepository authorizedClientRepo;

	@Override
	public void apply(RequestTemplate template) {
		final var auth = SecurityContextHolder.getContext().getAuthentication();
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            //inside an app which is primarily a client (with oauth2Login)
			if (auth instanceof OAuth2AuthenticationToken oauth) {
				final var authorizedClient =
						authorizedClientRepo.loadAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), auth, servletRequestAttributes.getRequest());
				if (authorizedClient != null) {
					template.header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(authorizedClient.getAccessToken().getTokenValue()));
				}
			}
            //inside an app which is primarily an oauth2ResourceServer
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(jwtAuth.getToken().getTokenValue()));
            }
            if (auth instanceof EGastroAuthentication jwtAuth) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(jwtAuth.getToken().getTokenValue()));
            }
		}
	}
}
```


the Spring REST clients
- RestTemplate => deprecated, don't use it
- WebClient => only if you're  reactive fan
- RestClient => recent synchronized equivalent for WebClient
- @FeignClient => declarative client from Spring Cloud

