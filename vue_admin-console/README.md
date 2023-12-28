# OIDC Training by Jérôme Wacongne - Vue frontend

The aim here is to setup login and logout for the admin console with Vue 3 framework.

This application will be accessible by users from eGastro.de, as well as managers and employees from restaurants.

We'd like users to have a custom login theme for their restaurant, but we don't want to make it easily readable from all users the list of restaurants subscribing to eGastro services. For that purpose, we'll use an input for the restaurant name and make the login button clickable only when the restaurant is listed in the know OAuth2 clients declared in Keycloak.

## 1. User classes
### 1.1. User class
Here is the representation we'll use for users in the Vue app:
```typescript
export class User {
  static readonly ANONYMOUS = new User('', '', '', '', [])

  constructor(
    readonly username: string,
    readonly subject: string,
    readonly email: string,
    readonly realm: string,
    readonly roles: string[]
  ) {}

  get isAuthenticated(): boolean {
    return !!this.subject
  }

  hasAnyRole(...roles: string[]): boolean {
    for (const r of roles) {
      if (this.roles.includes(r)) {
        return true
      }
    }
    return false
  }
}
```

### 1.2. LoginOptionDto
Here is the model for the payload returned by the `/login-options` endpoint on the BFF:
```typescript
export interface LoginOptionDto {
  label: string
  href: string
}
``` 

### 1.3. UserService
Now, the service responsible for fetching login options, performing login & logout, and refreshing current user info:
```typescript
const backend = 'https://192.168.1.182:7080'

export interface LoginOptionDto {
  label: string
  href: string
}

export class UserService {
  readonly current = ref(User.ANONYMOUS)
  private refreshIntervalId?: number

  constructor() {
    this.refresh()
  }

  async refresh(): Promise<User> {
    if (this.refreshIntervalId) {
      clearInterval(this.refreshIntervalId)
    }
    const response = await fetch(`${backend}/bff/v1/users/me`)
    const body = await response.json()
    this.current.value = body.user?.subject
      ? new User(
          body.user.username,
          body.user.subject,
          body.user.email || '',
          body.user.realm || '',
          body.user.roles || []
        )
      : User.ANONYMOUS
    if (body.user?.subject) {
      const now = Date.now()
      const delay = (1000 * body.exp - now) * 0.8
      if (delay > 2000) {
        this.refreshIntervalId = setInterval(this.refresh, delay)
      }
    }
    return this.current.value
  }

  login(loginUri: string, isSameTab: boolean) {
    if (isSameTab) {
      window.location.href = loginUri
    } else {
      window.open(
        loginUri,
        'Login',
        `toolbar=no, location=no, directories=no, status=no, menubar=no, scrollbars=no, resizable=no, width=800, height=600`
      )
    }
  }

  async logout(xsrfToken: string) {
    const response = await fetch(`${backend}/logout`, {
      method: 'POST',
      headers: {
        'X-XSRF-TOKEN': xsrfToken,
        'X-POST-LOGOUT-SUCCESS-URI': `${backend}/admin-console`
      }
    })
    const location = response.headers.get('Location')
    if (location) {
      window.location.href = location
    }
  }

  async loginOptions(): Promise<Array<LoginOptionDto>> {
    const response = await fetch(`${backend}/login-options`)
    return await response.json()
  }
}
```
Please note that:
- Vue 3 does not handle CSRF protection transparently (like Angular and React do for instance), and that we have to position a `X-XSRF-TOKEN` header for the `POST` request used to logout. The value of the token is read from a cookie and provided by the caller (`App.vue` in our case).
- we use a custom `X-POST-LOGOUT-SUCCESS-URI` header to set the expected URI after logout from BFF and then Keycloak. This header is defined in `SpringAddonsOidcClientProperties` and used by `SpringAddonsServerLogoutSuccessHandler`
- `login(loginUri: string, isSameTab: boolean)`, which redirects the current tab or opens a new browser window, won't be used by our implementation relying on iframe in current page

## 2. Vue Single File Component
### 2.1. `App.vue`
Here is the single-file component we'll use:
```vue
<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue';
import { RouterView } from 'vue-router';
import { useCookies } from "vue3-cookies";
import { UserService, type LoginOptionDto } from './user.service';

// Required to read the CSRF token from XSRF-TOKEN cookie
const { cookies } = useCookies();

// Valid login options, loaded by the UserService called in onMounted
const loginOptions: Ref<LoginOptionDto[]> = ref([])
// the registrationID on the BFF
const oauth2ClientRegistration = 'admin-console'

// inject the singleton defined in main.js
const user = inject('UserService') as UserService;

// stores the URI used to initiate an authorization-code flow on the BFF
const bffAuthorizationInitiationUri = ref()

const iframeSrc = ref()
const iframe = ref()
const isLoginModalDisplayed = ref(false)

onMounted(async () => {
  // Fetch login options from the BFF
  loginOptions.value = await user.loginOptions();

  // Select the login option for current frontend client registration
  const href = loginOptions.value.filter(opt => opt.label === oauth2ClientRegistration).map(loginOpt => loginOpt.href) || [];
  if (href.length) {
    const loginInitUri = `${href[0]}?post_login_success_uri=/admin-console`;
    bffAuthorizationInitiationUri.value = loginInitUri
  }
  // Initial login iframe state is always "hidden"
  isLoginModalDisplayed.value = false

  // Force user service refresh each time the login iframe content changes
  iframe.value.onload = () => {
    user.refresh()
  }
})

async function login() {
  // When login button is clicked, follow the authorization-code redirects again to ensure that the session state is fresh
  iframeSrc.value = bffAuthorizationInitiationUri.value;
  // Display the iframe
  isLoginModalDisplayed.value = true
}

function logout(xsrfToken: string) {
  user.logout(xsrfToken)
}
</script>

<template>
  <div>
    <div class="header">
      <span style="margin: 0 auto;">eGastro.de Administration Console</span>
      <router-link to="/me">
        <button v-if="user.current.value.isAuthenticated && $route.path !== '/me'">Account</button>
      </router-link>
      <button v-if="user.current.value.isAuthenticated && $route.path === '/me'"
        @click="logout(cookies.get('XSRF-TOKEN'))">Logout</button>
      <button v-if="!user.current.value.isAuthenticated" @click="login">Login</button>
    </div>

    <div>
      <RouterView />
    </div>
  </div>

  <!-- Template for the the login iframe -->
  <div class="modal-overlay" v-show="isLoginModalDisplayed && !user.current.value.isAuthenticated"
    @click.self="isLoginModalDisplayed = false">
    <div class="modal">
      <iframe :src="iframeSrc" frameborder="0" ref="iframe"></iframe>
      <button class="close-button" @click="isLoginModalDisplayed = false">Discard</button>
    </div>
  </div>
</template>

<style scoped>
.header {
  border: 1em;
  display: flex;
  height: 2em;
  width: 100%;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.modal {
  background-color: #fff;
  padding: 20px;
  border-radius: 5px;
  position: relative;
  width: 100%;
  max-width: 800px;
}

.modal iframe {
  width: 100%;
  height: 400px;
  border: none;
}
</style>
```
When we click "Login", a "modal" iframe is displayed with the login form from Keycloak.

When we click "Logout", the user service redirects the current tab to the BFF (to close the session the user has there), which in turn redirects to Keycloak (to close the other session the user has on the authorization server), which redirects again to the post-login URI provided when logout was initiated.

### 2.2. `RestaurantsListView.vue`
Here is a view using the session and CSRF token to call the REST API through the BFF:
```typescript
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useCookies } from 'vue3-cookies';

interface RelyingPartyDto {
  id: string
  registration: string
  name: string
}

// Required to read the CSRF token from XSRF-TOKEN cookie
const { cookies } = useCookies();

const bff = 'https://192.168.1.182:7080'
const relyingParties = ref([] as RelyingPartyDto[])
const toAdd = ref('')

onMounted(async () => {
  return await refreshRelyingParties();
})

async function refreshRelyingParties() {
  const response = await fetch(`${bff}/client-registrations`)
  const body = await response.json()
  relyingParties.value = body
}

async function remove(rp: string) {
  const response = await fetch(`${bff}/client-registrations/${rp}`, {
    method: 'delete',
    headers: { 'X-XSRF-TOKEN': cookies.get('XSRF-TOKEN') },
  })
  if (response.status >= 200 && response.status < 300) {
    relyingParties.value = relyingParties.value.filter(relyingParty => relyingParty.id !== rp)
  }
}

async function create() {
  const response = await fetch(`${bff}/client-registrations`, {
    method: 'post',
    headers: { 'X-XSRF-TOKEN': cookies.get('XSRF-TOKEN'), 'Content-Type': 'application/json' },
    body: JSON.stringify({
      "registrationId": toAdd.value,
      "clientId": toAdd.value,
    }),
  })
  if (response.status >= 200 && response.status < 300) {
    toAdd.value = ''
    await refreshRelyingParties()
  }
}

</script>

<template>
  <main>
    <h1>Restaurants</h1>
  </main>
  <div v-for="rp in relyingParties" :key="rp.registration">
    <span>{{ rp }}</span><button @click="remove(rp.id)" :disabled="!rp.id">Delete</button>
  </div>
  <div>
    <input placeholder="restaurant" v-model="toAdd" />
    <button @click="create">Add</button>
  </div>
</template>

<style></style>
```