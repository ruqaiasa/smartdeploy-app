<script setup lang="ts">
import { ref } from 'vue'
import { requestPasswordReset } from '@/api/auth.api'

const email = ref('')
const loading = ref(false)
const sent = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  if (!email.value.trim()) {
    error.value = 'Saisissez votre adresse e-mail.'
    return
  }
  loading.value = true
  try {
    sent.value = await requestPasswordReset(email.value.trim())
  } catch {
    error.value = 'La demande n’a pas pu être envoyée. Réessayez dans un instant.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="pw-page">
    <section class="pw-card" aria-labelledby="pw-title">
      <p class="pw-brand">SmartDeploy</p>
      <h1 id="pw-title">Mot de passe oublié</h1>

      <template v-if="!sent">
        <p class="pw-lead">Saisissez l’adresse de votre compte : vous recevrez un lien pour choisir un nouveau mot de passe.</p>
        <form class="pw-form" @submit.prevent="submit">
          <label for="pw-email">Adresse e-mail</label>
          <input id="pw-email" v-model="email" type="email" autocomplete="email" placeholder="nom@exemple.com" :disabled="loading" />
          <p v-if="error" class="pw-error" role="alert">{{ error }}</p>
          <button type="submit" class="pw-button" :disabled="loading">{{ loading ? 'Envoi…' : 'Envoyer le lien' }}</button>
        </form>
      </template>

      <div v-else class="pw-success" role="status">
        <strong>Vérifiez votre messagerie</strong>
        <span>{{ sent }}</span>
        <span class="pw-muted">Le lien est valable 30 minutes et ne peut servir qu’une fois.</span>
      </div>

      <RouterLink to="/login" class="pw-link">Retour à la connexion</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.pw-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; background: #f3f6f9; }
.pw-card { width: min(440px, 100%); padding: 32px; border-radius: 16px; background: #fff; box-shadow: 0 10px 30px rgba(15, 27, 45, .08); display: flex; flex-direction: column; gap: 14px; }
.pw-brand { margin: 0; font-size: 13px; font-weight: 700; letter-spacing: .04em; color: #0e9f8e; text-transform: uppercase; }
.pw-card h1 { margin: 0; font-size: 24px; color: #0f1b2d; }
.pw-lead { margin: 0; color: #475569; font-size: 14.5px; line-height: 1.6; }
.pw-form { display: flex; flex-direction: column; gap: 8px; }
.pw-form label { font-size: 13.5px; font-weight: 600; color: #334155; }
.pw-form input { padding: 11px 12px; border: 1px solid #cbd5e1; border-radius: 10px; font: inherit; font-size: 14.5px; }
.pw-form input:focus { outline: 2px solid #0e9f8e; outline-offset: 1px; border-color: transparent; }
.pw-button { margin-top: 6px; padding: 11px 14px; border: 0; border-radius: 10px; background: #0e9f8e; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.pw-button:disabled { opacity: .6; cursor: not-allowed; }
.pw-error { margin: 0; padding: 9px 11px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
.pw-success { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: 10px; background: #f0fdfa; border: 1px solid #99f6e4; color: #134e4a; font-size: 14px; line-height: 1.5; }
.pw-muted { color: #64748b; font-size: 13px; }
.pw-link { align-self: flex-start; color: #0e9f8e; font-size: 14px; font-weight: 600; text-decoration: none; }
.pw-link:hover { text-decoration: underline; }
</style>
