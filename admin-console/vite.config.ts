import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/ui',
  server: {
    host: "0.0.0.0",
    port: 4200,
    https: {
      cert: 'C:/Users/ch4mp/.ssh/bravo-ch4mp_self_signed.pem',
      key: 'C:/Users/ch4mp/.ssh/bravo-ch4mp_req_key.pem'
    },
    open: true,
  }
})