<script setup lang="ts">
import { inject, onMounted, ref, type Ref } from 'vue';
import { RouterView } from 'vue-router';
import { UserService, type LoginOptionDto } from './user.service';
import { useCookies } from "vue3-cookies";

const { cookies } = useCookies();

const user = inject('UserService') as UserService;

const loginOptions: Ref<LoginOptionDto[]> = ref([])

function login() {
  if (loginOptions.value.length === 1) {
    user.login(loginOptions.value[0].href)
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
  <header>
    <div class="header">
      <span style="margin: 0 auto;">eGastro</span>
      <router-link to="/me">
        <button v-if="user.current.value.isAuthenticated && $route.path !== '/me'">Account</button>
      </router-link>
      <button v-if="user.current.value.isAuthenticated && $route.path === '/me'" @click="logout(cookies.get('XSRF-TOKEN'))">Logout</button>
      <button v-if="!user.current.value.isAuthenticated" @click="login">Login</button>
    </div>
  </header>

  <RouterView />
</template>

<style scoped>
.header {
  display: flex;
  height: 2em;
  width: 100%;
}
</style>
