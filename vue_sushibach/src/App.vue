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
const oauth2ClientRegistration = 'sushibach'

// inject the singleton defined in main.js
const user = inject('UserService') as UserService;

const iframeSrc = ref("")

async function login() {
  const href = loginOptions.value.filter(opt => opt.label === oauth2ClientRegistration).map(loginOpt => loginOpt.href) || [];
  if (href.length) {
    iframeSrc.value = href[0];
    /*
    const preAuthorizationResponse = await fetch(href[0], {
      method: 'GET',
      headers: {
      }
    });
    const authorizationHref = preAuthorizationResponse.headers.get('location') ?? ''

    iframeSrc.value = authorizationHref;
    */
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
      <span style="margin: 0 auto;">Sushi Bach</span>
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
  <div class="modal-overlay" v-if="iframeSrc" @click.self="iframeSrc = ''">
    <div class="modal">
      <iframe :src="iframeSrc" frameborder="0"></iframe>
      <button class="close-button" @click="iframeSrc = ''">Discard</button>
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
