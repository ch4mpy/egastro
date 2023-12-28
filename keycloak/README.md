# OIDC Training by Jérôme Wacongne: Local Keycloak Setup
As a prerequisite, you should have:
- a gateway running on port 7080 and routing `/auth/**` to port 8443 (a `spring-cloud-gateway(-mvc)` instance, a K8s ingress, ...)
- a [self-signed certificate](https://github.com/ch4mpy/self-signed-certificate-generation) available.

As an option, you may have a [PostgreSQL instance](https://www.postgresql.org/download/) running with
- a `keycloak` "login" (`keycloak` as name, a password set in `Definition` tab and `Can login` enabled in `Privileges` tab)
- a `keycloak` database with `keycloak` as owner

## 1. Server
### 1.1. Install the server
#### 1.1.1. Standalone
Download Keycloak [Distribution powered by Quarkus](https://www.keycloak.org/downloads) and unpack it.

Edit `conf/keycloak.conf` (update the keystore settings and comment the DB part if you do not have a loclal Postgresql instance):
```conf
# Database (uncomment and adapt to use something else than the default H2 DB)
# See https://www.postgresql.org/download/ for a robust & free DB
#db=postgres
#db-username=keycloak
#db-password=change-me
#db-url=jdbc:postgresql://localhost/keycloak

# Hostname and path used to set issuer-uri and build redirection URIs
# When using a "real" mobile frontend (Android or iOS phone), you'll have to use an IP or a hostname resolved by your network DNS instead of "localhost"
hostname-url=https://localhost:7080/auth
hostname-admin-url=https://localhost:7080/auth
http-relative-path=/auth

http-enabled=true
http-port=8442
https-key-store-file=C:/Users/ch4mp/.ssh/bravo-ch4mp_self_signed.jks
# value to be replaced with the value of SERVER_SSL_KEY_STORE_PASSWORD
https-key-store-password=change-me
https-port=8443
```

Run the server with `bash bin/kc.sh start-dev --spi-theme-static-max-age=-1 --spi-theme-cache-themes=false --spi-theme-cache-templates=false` or `bin\kc.bat start-dev --spi-theme-static-max-age=-1 --spi-theme-cache-themes=false --spi-theme-cache-templates=false`. You might probably create a `keycloak.bat` (or `keycloak.sh`) to ease the startup from your desktop or anywhere you find convenient.  

Open [https://localhost:8443/realms/master](https://localhost:8443/realms/master) and create the `admin` account.

Define an admin account and connect.

#### 1.1.2. In a Docker Container
As an alternative, you may run Keycloak in a docker container (requires [Docker desktop](https://www.docker.com/products/docker-desktop/)):
- edit the `docker-compose.yaml`
- run `docker compose up`

### 1.2. Themes & "plugins"
You might add or modify themes by deploying resources to Keycloak `themes` folder. Some very basic sample are provided here. See [official doc](https://www.keycloak.org/docs/latest/server_development/index.html#_themes) for more details.

Also, we'll use 3 "plugins" deployed in Keycloak `providers` folder:
- `apple-social-identity-provider`: adds "Login with Apple" feature
- `egastro-keycloak-mapper`: adds a private claim to tokens (contains the "grants" on restaurants for the current user, as returned by our resource server)
- `keycloak-magic-link`: adds the ability to configure login flows with "magic link" (in addition to or instead of login + password)

In case where you are using Docker, the themes and providers are added from the local file system to the image by the `volumes` directive in the `docker-compose.yaml` file.

### 1.3. Realms setup
#### 1.3.1. `master` realm
Under `Real settings` -> `General`, enable `User-managed access`.

Under `Real settings` -> `Login`, enable `User registration`, `Forgot password`, `Remember me`, `Login with email`, `Verify email`

Under `Real settings` -> `Email`, configure a SMTP server. For Gmail:
- `Host`: `smtp.gmail.com`
- `Port`: `465`
- `Encryption`: `Enable SSL`
- `Authentication`: `Enabled` with your email as username and, if multi-factor authentication authentication is enabled (it should be on a "professional" account), a password from [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)

#### 1.3.2. `egastro` realm
Create a realm named `egastro` with the same settings as `master` (do not forget the email part).

Go to `Authentication` -> `Flows` and create a new flow named `Browser magic link`:
- `Add step` -> `Cookie` and set it as `Alternative`
- `Add step` -> `Kerberos` and leave it `Disabled`
- `Add step` -> `Identity Provider Redirector` and set it as `Alternative`
- `Add sub-flow` -> `Magic Link Forms` and set it as `Alternative`
- on the right of the `Magic Link Forms` sub-flow, `+` -> `Add step` -> `Magic Link` and set it as `Required`
- in the settings of the `Magic Link` step in the `Magic Link Forms` sub-flow, only keep `Force create user` enabled

Note the new flow UUID in the browser nav-bar and update the value for the `browser` entry in `authenticationFlowBindingOverrides` of the `ClientRepresentation` constructor (BFF module). This will set this "Magic Link" flow as default for the newly created clients using Vue admin-console frontend.

## 2. Roles in Keycloak
### 2.1. `master` realm
We only need to declare `KEYCLOAK_MAPPER` under `Realm roles`. This role will be granted only to the "service account" (OAuth2 client authenticating with `client_credentials`) used by the mapper responsible for fetching user's "grants" from the resource server and adding it to tokens.

### 2.1. `egastro` realm
We'll use two different `Realm roles` in security expressions to control user access on the resource server:
- `EGASTRO_CLIENT`
- `EGASTRO_MANAGER`

## 3. Clients creation
**`Client authentication` should be enabled for all clients**. This is what makes clients *"private"* (with a secret).

**`Direct access grants` should always be disabled**. This is Keycloak name for the `password` flow which was long deprecated and should be used under no condition.

`Valid redirect URIs` are to be defined only for client configured with `authorization_code` flow (`Standard flow` in Keycloak UI). To be very strict when using only Spring OAuth2 clients, we can use `{client-scheme://client-host:client-port}/login/oauth2/code/{client-registration-id}`, but on dev machines, setting `*` as path is just fine.

If the local network has a DNS, do not forget to set an entry with the name of the machine, this will prove to be very useful when debugging Flutter application on real mobile devices.

using `+` as value for `Valid post logout redirect URIs` and `Web origins` is just fine on dev machines.

### 3.1. `master` realm
#### 3.1.1. restaurants-employees-mapper
- `Client authentication` enabled
- `Authentication flow` select only `Service accounts roles`
- in `Service accounts roles`, click `Assign role` to grant `KEYCLOAK_MAPPER`

### 3.2. `egastro` realm
#### 3.2.1. egastro
- `Client authentication` enabled
- `Authentication flow` select only `Standard flow`
- you may set all the network interfaces for your local BFF as `Root URL` (`https://localhost:7080`, `https://{hostname}:7080`, `https://127.0.0.1:7080`, `https://10.0.2.2:7080`, `https://{static-ip-address}:7080`, as well as the `http` equivalents)
- set `Valid redirect URIs`, `Valid post logout redirect URIs` and `Web origins`


#### 3.2.2. egastro-admin-client
- `Client authentication` enabled
- `Authentication flow` select only `Service accounts roles`
- in `Service accounts roles`, click `Assign role`, in the first combo, select `Filter by clients`, the set `client` as filter and select:
  * `egastro-realmcreate-client`
  * `egastro-realmmanage-clients`
  * `egastro-realmquery-clients`
  * `egastro-realmview-clients`

The this "service account" will be used by a `@Service` on the resource server to dynamically declare new OAuth2 clients in Keycloak and add registrations to the BFF.

An other option would be to grant this client roles to a few "admin" users and to use the access token from the registration with `authorization_code` grant type. But, as it requires to set more users with Keycloak internal roles, we'll prefer to use `@PreAuthorize("hasAuthority('EGASTRO_MANAGER')")` as security expression and a registration with `client_credentials` to populate the authorization header of requests sent from the Spring backend with a REST client.

#### 3.2.3. admin-console

#### 3.2.4. additional test clients
We have a choice for the clients used by the BFF to login users from B2C frontends: use the admin-console Vue application or declare it both in Keycloak and BFF application properties. Here are the OAuth2 clients that we'll use in frontends:
- `sushibach`
- `burger-house`

## 4. Identity providers
The process is pretty simple: declare a client with `client_credentials` on the OpenID Provider you want to "Login with" and then report the `client-id` and `client-secret` into Keycloak administration console.

### 4.1. Google
Browse to [https://console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials) to `+ CREATE CREDENTIALS` -> `OAuth client ID`

As `Authorized JavaScript origins`, add your Keycloak instance URI (with port: `https://localhost:8443`).

As `Authorized redirect URIs`, set the authorized origin with `/auth/realms/quiz/broker/google/endpoint` as path.

Report the `Client ID` and `Client secret` in Keycloak's `Identity providers` -> `google` section of the realm(s) you whish to add `Login with Google`

Once the identity provider created, add `openid email profile` to `Advanced settings` -> `Scopes`

## 5. Mapper

### 5.1. Required Elements
- `src/main/resources/META-INF/jboss-deployment-structure.xml`:
```xml
<jboss-deployment-structure>
    <deployment>
        <dependencies>
            <module name="org.keycloak.keycloak-services" />
        </dependencies>
    </deployment>
</jboss-deployment-structure>
```
- `src/main/resources/META-INF/services/org.keycloak.protocol.ProtocolMapper` (contains the fully qualified name for the class implementing `OIDCAccessTokenMapper`, `OIDCIDTokenMapper` and `UserInfoTokenMapper`):
```
de.egastro.restaurants_employees.keycloak_mapper.RestaurantsEmployeesKeycloakMapper
```
- `RestaurantsEmployeesKeycloakMapper` implementation: source code template is provided.
- dependencies on `keycloak-server-spi`, `keycloak-server-spi-private` and `keycloak-services` for your Keycloak version (see pom.xml for sample)
- `maven-shade-plugin` with `shade` goal during `package` phase (see pom.xml for a sample)

### 5.2. Using Spring's RestClient
The mapper needs to send to requests:
- a `POST` to the token endpoint to get an access token for itself
- a `GET` to the restaurants API to get the values to add as private claim to the token for the user currently logging in

In `RestaurantsEmployeesClient`:
- initiate the `tokenClient` property
- complete the `getClientAccessToken` method

### 5.3. Packaging and deploying
From the `egastro/backend` folder, run `bash mvnw install` to build the all backend, and so the mapper shaded jar

Copy the output (`egastro/backend/egastro-keycloak-mapper/target/egastro-keycloak-mapper-0.0.1-SNAPSHOT.jar`) to `keycloak-21.1.1/providers` and restart the server.

### 5.4. Configuring the mapper for the `egastro-bff` client
To add the new private claims delivered to the `egastro-bff` client when users log-in with `authorization_code` flow, in the console:
- open the `egastro-bff` client details
- browse to the `Clients scopes` tab
- click `egastro-bff-dedicated`
- click `Add mapper` and then `By configuration`
- browse to `User employments mapper`
- check the URIs and set the client secret with the value taken from the `Credentials` tab of `restaurants-employees-mapper` client