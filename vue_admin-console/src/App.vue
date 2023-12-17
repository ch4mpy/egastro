<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref } from 'vue';
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

function login() {
  const href = loginOptions.value.filter(opt => opt.label === oauth2ClientRegistration).map(loginOpt => loginOpt.href) || [];
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
      <button v-if="!user.current.value.isAuthenticated" @click="login">Login</button>
    </div>
    <div>
      <RouterView />
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
</style>
