import './assets/main.css'
import './assets/console-layout.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './store/authStore'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useAuthStore().loadFromStorage()
app.use(router)
app.mount('#app')
