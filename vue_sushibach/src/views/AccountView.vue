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
</style>
