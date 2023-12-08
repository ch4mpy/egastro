# Local Keycloak Setup
As a prerequisite, you should have a [self-signed certificate](https://github.com/ch4mpy/self-signed-certificate-generation) available.

As an option, you may have a [PostgreSQL instance](https://www.postgresql.org/download/) running with
- a `keycloak` "login" (`keycloak` as name, a password set in `Definition` tab and `Can login` enabled in `Privileges` tab)
- a `keycloak` database with `keycloak` as owner

## 1. Server
### 1.1. Install the server
Download Keycloak [Distribution powered by Quarkus](https://www.keycloak.org/downloads) and unpack it.

Edit `conf/keycloak.conf` (update the keystore settings and comment the DB part if you do not have a loclal Postgresql instance):
```
# Basic settings for running in production. Change accordingly before deploying the server.

# Database

# The database vendor.
db=postgres

# The username of the database user.
db-username=keycloak

# The password of the database user.
db-password=chnge-me

# The full database JDBC URL. If not provided, a default URL is set based on the selected database vendor.
db-url=jdbc:postgresql://localhost/keycloak

# Observability

# If the server should expose healthcheck endpoints.
#health-enabled=true

# If the server should expose metrics endpoints.
#metrics-enabled=true

# HTTP

# The file path to a server certificate or certificate chain in PEM format.
#https-certificate-file=${kc.home.dir}conf/server.crt.pem

# The file path to a private key in PEM format.
#https-certificate-key-file=${kc.home.dir}conf/server.key.pem

# The proxy address forwarding mode if the server is behind a reverse proxy.
#proxy=reencrypt

# Do not attach route to cookies and rely on the session affinity capabilities from reverse proxy
#spi-sticky-session-encoder-infinispan-should-attach-route=false

# Hostname for the Keycloak server.
#hostname=myhostname
hostname=localhost

http-enabled=true
http-port=8442
https-key-store-file=C:/Users/ch4mp/.ssh/bravo-ch4mp_self_signed.jks
https-key-store-password=change-me
https-port=8443
```

Run the server with `bash bin/kc.sh start-dev` or `bin\kc.bat start-dev`

Open [https://localhost:8443/realms/master](https://localhost:8443/realms/master) and create the `admin` account.

Define an admin account and connect

### 1.2. Master realm setup
Under `Real settings` -> `General`, enable `User-managed access`.

Under `Real settings` -> `Login`, enable `User registraion`, `Forgot password`, `Remeber me`, `Login with email`, `Verify email`

## 2. Realm roles
- `KEYCLOAK_MAPPER`
- `EGASTRO_REALM_MANAGER`
- `EGASTRO_CLIENT`

## 3. Clients creation
**`Client authentication` should be enabled for all clients**. This is what makes clients *"private"* (with a secret).

**`Direct access grants` should always be disabled**. This is Keycloak name for the `password` flow which was long deprecated and should be used under no condition.

`Valid redirect URIs` are to be defined only for client configured with `authorization_code` flow (`Standard flow` in Keycloak UI). To be very strict when using only Spring OAuth2 clients, we can use `{client-scheme://client-host:client-port}/login/oauth2/code/{client-registration-id}`, but on dev machines, setting both `https://localhost:7080/*` and `http://localhost:7080/*` is just fine (if the local network has a DNS, use the name of the machine instead of `localhost`, this will enable to use Keycloak from other devices during tests).

using `+` as value for `Valid post logout redirect URIs` and `Web origins` is just fine on dev machines.

### 3.1. egastro-bff
- `Client authentication` enabled
- `Authentication flow` select only `Standard flow`
- you may set your local (BFF `https://localhost:7080`) as `Root URL`
- set `Valid redirect URIs`, `Valid post logout redirect URIs` and `Web origins`

### 3.2. restaurants-employees-mapper
- `Client authentication` enabled
- `Authentication flow` select only `Service accounts roles`
- in `Service accounts roles`, click `Assign role` to grant `KEYCLOAK_MAPPER`


### 3.3. egastro-admin-client
- `Client authentication` enabled
- `Authentication flow` select only `Service accounts roles`
- in `Service accounts roles`, click `Assign role` to grant `query-realms` and `create-realm`

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
- `maven-shade-plugin` with `shade` goal during `package` phase (see pom.xml for sample)

### 5.2. Using Spring's RestClient
The mapper needs to send to requests:
- a `POST` to the token endpoint to get an access token for itself
- a `GET` to the restaurants API to get the values to add to the token for the user currently logging in

In `RestaurantsEmployeesClient`:
- initiate the `tokenClient` property
- complete the `getClientAccessToken` method

### 5.3. Packaging and deploying
From the `egastro/backend` folder, run:
- `bash mvnw install -pl dtos` 
- `bash mvnw package -pl egastro-keycloak-mapper` to build the shaded jar

Copy the output (`egastro/backend/egastro-keycloak-mapper/target/egastro-keycloak-mapper-0.0.1-SNAPSHOT.jar`) to `keycloak-21.1.1/providers` and restart the server.

### 5.4. Configuring the mapper for the `egastro-bff` client
To add the new private claims delivered to the `egastro-bff` client when users log-in with `authorization_code` flow, in the console:
- open the `egastro-bff` client details
- browse to the `Clients scopes` tab
- click `egastro-bff-dedicated`
- click `Add mapper` and then `By configuration`
- browse to `User employments mapper`
- check the URIs and set the client secret with the value taken from the `Credentials` tab of `restaurants-employees-mapper` client