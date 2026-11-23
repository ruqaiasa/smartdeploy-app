<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import httpClient from '@/api/httpClient'

/* RG-U3 : confirmation de l’adresse par le lien reçu. */
const route = useRoute()
const state = ref<'loading' | 'ok' | 'error'>('loading')
const message = ref('')
const email = ref('')
const resent = ref(false)

onMounted(async () => {
  const token = String(route.query.token ?? '')
  try {
    const { data } = await httpClient.post<{ email: string }>('/api/users/verify-email', { token })
    email.value = data.email
    state.value = 'ok'
  } catch (exception) {
    message.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Lien invalide.'
    state.value = 'error'
  }
})
async function resend() {
  if (!email.value.trim()) return
  await httpClient.post('/api/users/resend-verification', { email: email.value.trim() }).catch(() => undefined)
  resent.value = true
}
</script>

<template>
  <main class="ve">
    <section class="ve-card">
      <p class="ve-brand">SmartDeploy</p>
      <template v-if="state === 'loading'"><h1>Vérification…</h1></template>
      <template v-else-if="state === 'ok'">
        <h1>Adresse confirmée</h1>
        <p><strong>{{ email }}</strong> est vérifiée. Un administrateur va valider votre compte ; vous recevrez un courriel.</p>
        <RouterLink to="/login" class="ve-btn">Retour à la connexion</RouterLink>
      </template>
      <template v-else>
        <h1>Lien non valide</h1>
        <p>{{ message }}</p>
        <form v-if="!resent" class="ve-form" @submit.prevent="resend">
          <input v-model="email" type="email" placeholder="Votre adresse" required />
          <button type="submit" class="ve-btn">Recevoir un nouveau lien</button>
        </form>
        <p v-else class="ve-ok">Si un compte attend une confirmation, un nouveau lien vient d’être envoyé.</p>
      </template>
    </section>
  </main>
</template>

<style scoped>
.ve { display: grid; min-height: 100vh; place-items: center; padding: 24px; background: linear-gradient(135deg, #eef2ff, #f5f3ff); }
.ve-card { display: flex; flex-direction: column; gap: 12px; width: min(440px, 100%); padding: 28px; border-radius: 18px; background: #fff; box-shadow: 0 20px 50px rgba(15, 23, 42, .12); text-align: center; }
.ve-brand { margin: 0; color: #4f46e5; font-weight: 800; letter-spacing: .04em; }
.ve-card h1 { margin: 0; font-size: 22px; }
.ve-card p { margin: 0; color: #374151; }
.ve-btn { padding: 10px 14px; border: 0; border-radius: 10px; background: #4f46e5; color: #fff; font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
.ve-form { display: flex; flex-direction: column; gap: 8px; }
.ve-form input { padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 10px; font: inherit; }
.ve-ok { color: #15803d !important; }
</style>
