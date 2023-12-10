# OIDC Training by Jérôme Wacongne
OAuth2 training labs for eGastro GmbH

The solution we build in this training is rather advanced:
- **dynamic multi-tenancy**: it's about the same as if users are in different realms or Keycloak servers. For eGastro, each restaurant or franchise is isolated in its own realm and it's impossible for the resource server to have a list of all the issuers it should trust when it starts: when a new restaurant is created, so is a realm, and it is not acceptable to restart resource server(s) each time a restaurant is added.
- **advanced access control**: Role-Based Access Control is not enough for eGastro business. In addition to evaluating authorities (roles), we'll have to check relation between users and domain entities: grant more access on a given restaurant resources to users who manage or work in it, allow users to edit only orders they passed, etc.
- **Keycloak specific code**: we have an adherence to Keycloak proprietary interfaces in two different ways:
  * use a "mapper" to add some private claims to tokens (things related to access control and specific to eGastro domain). This is Keycloak specific code which would need to be adapted if switching to another OpenID Provider (most alternative OP proposes similar feature, but all implement it their own way).
  * use "admin" API to manipulate Keycloak resources: when a restaurant is declared in eGastro, create a realm for it in Keycloak (and also a "manager" user).

## 1. Theory
The slides are available [as PDF](https://raw.githubusercontent.com/ch4mpy/egastro/main/OpenID_eGastro.pdf). You might also refer to the [OAuth2 essentials](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials#oauth_essentials) section of my main repo which is updated on a regular basis.

You can test your knowledge from with this [Quiz application](https://quiz.c4-soft.com/ui/quizzes).

## 2. Keycloak
For Keycloak initial setup, clients & roles definitions, and private claims mapper, see [keycloak.md](https://github.com/ch4mpy/egastro/blob/main/keycloak.md)

## 3. Backend
For BFF configuration and REST APIs access-control, see [backend/README.md](https://github.com/ch4mpy/egastro/blob/main/backend/README.md)

## 4. Vue.js frontend
To connect a Vue.js application to a BFF, see [frontend/vue.md](https://github.com/ch4mpy/egastro/blob/main/admin-console/README.md)

## 5. Flutter frontend
To connect an Android Flutter application to a BFF, see [frontend/flutter.md](https://github.com/ch4mpy/egastro/blob/main/custommer-app/README.md)

## 6. Keycloak "admin"
For Keycloak "admin" API usage, see [backend/README.md](https://github.com/ch4mpy/egastro/blob/main/backend/keycloak-admin-api.md)
