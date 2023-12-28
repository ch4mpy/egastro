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