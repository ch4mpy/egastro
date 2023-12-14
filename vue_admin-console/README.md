# OIDC Training by Jérôme Wacongne - Vue frontend

The aim here is to setup login and logout for the admin console with Vue 3 framework.

This application will be accessible by users from eGastro.de, as well as managers and employees from restaurants.

We'd like users to have a custom login theme for their restaurant, but we don't want to make it easily readable from all users the list of restaurants subscribing to eGastro services. For that purpose, we'll use an input for the restaurant name and make the login button clickable only when the restaurant is listed in the know OAuth2 clients declared in Keycloak.

## 1. User classes
### 1.1. User class
Here is the representation we'll use for users in the Vue app:
```typescript
export class User {
  static readonly ANONYMOUS = new User('', '', [], [], [])

  constructor(
    readonly name: string,
    readonly realm: string,
    readonly roles: string[],
    readonly manages: number[],
    readonly worksFor: number[]
  ) {}

  get isAuthenticated(): boolean {
    return !!this.name
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
    label: string,
    href: string
}
``` 

### 1.3. UserService
Now, the service responsible for fetching login options, performing login & logout, and refreshing current user info:
```typescript
const bff = 'https://192.168.1.182:7080'

export class UserService {
  readonly current = ref(User.ANONYMOUS)
  private refreshIntervalId?: number

  constructor() {
    this.refresh()
  }

  async refresh(): Promise<User> {
    if(this.refreshIntervalId) {
        clearInterval(this.refreshIntervalId)
    }
    const response = await fetch(`${bff}/bff/v1/users/me`)
    const body = await response.json()
    this.current.value = body.name ? new User(body.name, body.realm, body.roles || [], body.manages || [], body.worksFor || []) : User.ANONYMOUS
    if (body.name) {
      const now = Date.now()
      const delay = (1000 * body.exp - now) * 0.8
      if (delay > 2000) {
        this.refreshIntervalId = setInterval(this.refresh, delay)
      }
    }
    return this.current.value
  }

  login(loginUri: string) {
    window.location.href = loginUri
  }

  async logout(xsrfToken: string) {
    const response = await fetch(`${bff}/logout`, { method: 'POST', headers: { 'X-XSRF-TOKEN': xsrfToken } })
    const location = response.headers.get('Location');
    if (location) {
        window.location.href = location;
    }
  }

  async loginOptions(): Promise<Array<LoginOptionDto>>{
    const response = await fetch(`${bff}/login-options`)
    return await response.json()
  }
}
```
Please note that Vue 3 does not handles CSRF protection transparently (like Angular and React do for instance), and that we have to position a `X-XSRF-TOKEN` header for the `POST` request used to logout. The value of the token is read from a cookie and provided by the caller (`App.vue` in our case).

## 2. Vue Single File Component
Here is the single-file component we'll use:
```vue
<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
import { RouterView } from 'vue-router';
import { useCookies } from "vue3-cookies";
import { UserService, type LoginOptionDto } from './user.service';

// Required to read the CSRF token from XSRF-TOKEN cookie
const { cookies } = useCookies();

// Valid login options, loaded by the UserService called in onMounted
const loginOptions: Ref<LoginOptionDto[]> = ref([])
// bound to input next to login button
const restaurant = ref('')
// computed value to set login button state
const isLoginDisabled = computed(() => {
  return !loginOptions.value.filter(opt => opt.label === restaurant.value).length;
})

// inject the singleton defined in main.js
const user = inject('UserService') as UserService;

function login() {
  // find the href corresponding to the OAuth2 client as typed in the input
  const href = loginOptions.value.filter(opt => opt.label === restaurant.value).map(loginOpt => loginOpt.href) || [];
  if (href.length) {
    user.login(href[0])
  }
}

function logout(xsrfToken: string) {
  user.logout(xsrfToken)
}

onMounted(async () => {
  loginOptions.value = await user.loginOptions();
})
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
      <span v-if="!user.current.value.isAuthenticated">
        <input placeholder="restaurant" v-model="restaurant" @keyup.enter="login" />
        <button @click="login" :disabled="isLoginDisabled">Login</button>
      </span>
    </div>
    <div>
      <RouterView />
    </div>
  </div>
</template>
```
Now, if if we type one of the clientIds registered in the BFF configuration (`egastro`, `sushibach` and `burger-house`), then we are redirected to a login form with the right theme.