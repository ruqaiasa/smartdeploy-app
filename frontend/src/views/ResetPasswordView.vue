<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { resetPassword, validateResetToken } from '@/api/auth.api'

const route = useRoute()
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))

const state = ref<'checking' | 'invalid' | 'form' | 'done'>('checking')
const password = ref('')
const confirmation = ref('')
const loading = ref(false)
const error = ref('')
const done = ref('')

/* Mêmes règles que le serveur, affichées au fil de la saisie. */
const rules = computed(() => [
  { label: 'Au moins 8 caractères', ok: password.value.length >= 8 },
  { label: 'Au moins une lettre', ok: /[A-Za-zÀ-ÿ]/.test(password.value) },
  { label: 'Au moins un chiffre', ok: /\d/.test(password.value) },
  { label: 'Les deux saisies sont identiques', ok: password.value.length > 0 && password.value === confirmation.value },
])
const ready = computed(() => rules.value.every(rule => rule.ok))

onMounted(async () => {
  if (!token.value) {
    state.value = 'invalid'
    return
  }
  try {
    state.value = (await validateResetToken(token.value)) ? 'form' : 'invalid'
  } catch {
    state.value = 'invalid'
  }
})

async function submit() {
  error.value = ''
  if (!ready.value) return
  loading.value = true
  try {
    done.value = await resetPassword(token.value, password.value)
    state.value = 'done'
  } catch (exception: unknown) {
    const response = (exception as { response?: { data?: { message?: string } } }).response
    error.value = response?.data?.message ?? 'Le mot de passe n’a pas pu être modifié.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="pw-page">
    <section class="pw-card" aria-labelledby="rp-title">
      <p class="pw-brand">SmartDeploy</p>
      <h1 id="rp-title">Nouveau mot de passe</h1>

      <p v-if="state === 'checking'" class="pw-lead">Vérification du lien…</p>

      <div v-else-if="state === 'invalid'" class="pw-warning" role="alert">
        <strong>Lien invalide ou expiré</strong>
        <span>Ce lien n’est plus utilisable : il a expiré (30 minutes), a déjà servi, ou a été remplacé par une demande plus récente.</span>
        <RouterLink to="/forgot-password" class="pw-link">Faire une nouvelle demande</RouterLink>
      </div>

      <form v-else-if="state === 'form'" class="pw-form" @submit.prevent="submit">
        <label for="rp-password">Nouveau mot de passe</label>
        <input id="rp-password" v-model="password" type="password" autocomplete="new-password" :disabled="loading" />
        <label for="rp-confirm">Confirmation</label>
        <input id="rp-confirm" v-model="confirmation" type="password" autocomplete="new-password" :disabled="loading" />
        <ul class="pw-rules">
          <li v-for="rule in rules" :key="rule.label" :class="{ 'is-ok': rule.ok }">
            <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true">
              <path v-if="rule.ok" d="M5 10.5l3.2 3.2L15 7" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
              <circle v-else cx="10" cy="10" r="4" fill="none" stroke="currentColor" stroke-width="1.8" />
            </svg>
            {{ rule.label }}
          </li>
        </ul>
        <p v-if="error" class="pw-error" role="alert">{{ error }}</p>
        <button type="submit" class="pw-button" :disabled="loading || !ready">
          {{ loading ? 'Enregistrement…' : 'Enregistrer le mot de passe' }}
        </button>
      </form>

      <div v-else class="pw-success" role="status">
        <strong>Mot de passe modifié</strong>
        <span>{{ done }}</span>
        <span class="pw-muted">Par sécurité, toutes vos sessions ouvertes ont été fermées.</span>
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
.pw-lead { margin: 0; color: #475569; font-size: 14.5px; }
.pw-form { display: flex; flex-direction: column; gap: 8px; }
.pw-form label { font-size: 13.5px; font-weight: 600; color: #334155; }
.pw-form input { padding: 11px 12px; border: 1px solid #cbd5e1; border-radius: 10px; font: inherit; font-size: 14.5px; }
.pw-form input:focus { outline: 2px solid #0e9f8e; outline-offset: 1px; border-color: transparent; }
.pw-rules { display: flex; flex-direction: column; gap: 4px; margin: 4px 0 0; padding: 0; list-style: none; font-size: 13px; color: #94a3b8; }
.pw-rules li { display: flex; align-items: center; gap: 6px; }
.pw-rules li.is-ok { color: #0f766e; }
.pw-button { margin-top: 6px; padding: 11px 14px; border: 0; border-radius: 10px; background: #0e9f8e; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.pw-button:disabled { opacity: .55; cursor: not-allowed; }
.pw-error { margin: 0; padding: 9px 11px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
.pw-warning { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: 10px; background: #fffbeb; border: 1px solid #fde68a; color: #78350f; font-size: 14px; line-height: 1.5; }
.pw-success { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: 10px; background: #f0fdfa; border: 1px solid #99f6e4; color: #134e4a; font-size: 14px; line-height: 1.5; }
.pw-muted { color: #64748b; font-size: 13px; }
.pw-link { align-self: flex-start; color: #0e9f8e; font-size: 14px; font-weight: 600; text-decoration: none; }
.pw-link:hover { text-decoration: underline; }
</style>
