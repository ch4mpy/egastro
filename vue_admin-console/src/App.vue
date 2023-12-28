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
