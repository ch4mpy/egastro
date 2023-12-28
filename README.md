# OIDC Training by Jérôme Wacongne
OAuth2 training labs for eGastro GmbH

The solution we build in this training is rather advanced:
- **dynamic OAuth2 clients**: for eGastro, each restaurant or franchise has its own OAuth2 client in Keycloak and registration on the BFF. As we don't want to restart the BFF each time a restaurant is added, the standard Spring Boot way of declaring client registrations in application properties is not enough. We'll build a custom client registration repository (with JPA persistence instead of `application.yaml`) and work with Keycloak admin API to add / remove clients each time a registration is added / removed on the BFF.
- **advanced access control**: Role-Based Access Control is not enough for eGastro business. In addition to evaluating authorities (roles), we'll have to check relation between users and domain entities: grant more access on a given restaurant resources to users who manage or work in it, allow users to edit only orders they passed, etc.
- **Keycloak specific code**: we have an adherence to Keycloak proprietary interfaces in two different ways:
  * use a "mapper" to add some private claims to tokens (things related to access control and specific to eGastro domain). This is Keycloak specific code which would need to be adapted if switching to another OpenID Provider (most alternative OP proposes similar feature, but all implement it their own way).
  * use "admin" API to manipulate Keycloak resources: when a restaurant is declared in eGastro, create a realm for it in Keycloak (and also a "manager" user).
- **Login forms in iframe / WebView**: we need to comply with iframe security policies to display authorization server login forms inside our Vue and Flutter frontends. For that, we'll use an ingress so that, from the frontend perspective, all requests have the same origin.
- **Advanced login flows**: to provide with the best possible user experience, we'll see how to extend Keycloak standard account creation and login forms: use a plugin to send a "Magic Link" by email and transparently create an account and login a user when this link is clicked

## 1. Prerequisites
Trainees should have the following ready:
- Git. [Github Desktop](https://desktop.github.com/) is perfect
- JDK 21 or above. [Graalvm](https://www.graalvm.org/downloads/) either standalone or with [SdkMan!](https://sdkman.io/)
- an IDE like VScode, Eclipse, IntelliJ, Android Studio, XCode with plugins for the frameworks we'll use:
  * [Spring Tools 4](https://spring.io/tools/)
  * Vue
  * Flutter

The following is recommended even if not strictly mandatory:
- unless you have a DNS on your local network, with an entry for each developer machine, it is strongly recommended that you use a "static" local IP for mobile devices debugging
- [self signed SSL certificates](https://github.com/ch4mpy/self-signed-certificate-generation) registered in your OS and JREs `cacerts` files ("fix" your IP address before generating the certificate to include this IP in it)

## 2. Theory
The slides are available [as ODP](https://raw.githubusercontent.com/ch4mpy/egastro/main/OpenID_eGastro.odp) and [as PDF](https://raw.githubusercontent.com/ch4mpy/egastro/main/OpenID_eGastro.pdf). You might also refer to the [OAuth2 essentials](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials#oauth_essentials) section of my main repo which is updated on a regular basis.

You can test your knowledge from with this [Quiz application](https://quiz.c4-soft.com/ui/quizzes).

## 3. Keycloak
The `keycloak` directory contains sample resources for themes, providers (plugins) and instructions to set up a Keycloak instance as "standalone" or as a Docker container. For more details about initial setup, clients & roles definitions, and private claims mapper, see [keycloak/README.md](https://github.com/ch4mpy/egastro/blob/main/keycloak/README.md)

## 4. Backend
For the global ingress, BFF and REST API, see [backend/README.md](https://github.com/ch4mpy/egastro/blob/main/backend/README.md)

## 5. Vue.js frontends
To connect a Vue.js application to a BFF, see
- [vue_admin-console/README.md](https://github.com/ch4mpy/egastro/blob/main/vue_admin-console/README.md)
- [vue_burger-house/README.md](https://github.com/ch4mpy/egastro/blob/main/vue_burger-house/README.md)
- [vue_sushibach/README.md](https://github.com/ch4mpy/egastro/blob/main/vue_sushibach/README.md)

## 6. Flutter frontend
To connect an Android Flutter application to a BFF, see [flutter_sushibach/README.md](https://github.com/ch4mpy/egastro/blob/main/flutter_sushibach/README.md)

## 7. Keycloak "admin" API
For Keycloak "admin" API usage, see [backend/README.md](https://github.com/ch4mpy/egastro/blob/main/backend/keycloak-admin-api.md) and:
- the BFF (with `WebClient`) for OAuth2 clients management (registrations in the BFF and entities in Keycloak)
- the REST API (with `@FeignClient`): see usage of `KeycloakUserService` in `RestaurantsController` and `UsersController`
