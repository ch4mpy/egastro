<script setup lang="ts">
import { inject } from 'vue';
import { type UserService } from '../user.service'

const user = inject('UserService') as UserService;

</script>

<template>
  <div class="account">
    <h1>{{ user.current.value.name }}</h1>
    
  </div>
</template>

<style>
@media (min-width: 1024px) {
  .account {
    min-height: 100vh;
    display: flex;
    align-items: center;
  }
}
</style>
