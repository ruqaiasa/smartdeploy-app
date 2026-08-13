<script setup lang="ts">
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  email: '',
  password: '',
})

async function submitLogin() {
  authStore.errorMessage = null

  const normalizedEmail = form.email
    .trim()
    .toLowerCase()

  if (!normalizedEmail || !form.password) {
    authStore.errorMessage =
      'Veuillez remplir tous les champs.'

    return
  }

  try {
    await authStore.login({
      email: normalizedEmail,
      password: form.password,
    })

    const redirectPath =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : '/dashboard'

    await router.push(redirectPath)
  } catch {
    // Le message d’erreur est déjà géré dans le store Pinia.
  }
}
</script>

<template>
  <main class="login-page">
    <!-- ======================================================
                         PARTIE VISUELLE
    ======================================================= -->

    <section class="login-visual">
      <div class="brand">
        <div class="brand-icon">
          S
        </div>

        <div>
          <strong>SmartDeploy</strong>
          <span>DevOps Deployment Platform</span>
        </div>
      </div>

      <div class="visual-content">
        <p class="eyebrow">
          Plateforme DevOps centralisée
        </p>

        <h1>
          Déployez vos applications avec confiance.
        </h1>

        <p>
          Gérez vos applications, versions,
          environnements et déploiements depuis
          une interface unique.
        </p>
      </div>

      <div class="visual-footer">
        Jenkins · Docker · Kubernetes · Prometheus
      </div>
    </section>

    <!-- ======================================================
                         FORMULAIRE LOGIN
    ======================================================= -->

    <section class="login-section">
      <div class="login-card">
        <div class="mobile-brand">
          <div class="brand-icon">
            S
          </div>

          <strong>SmartDeploy</strong>
        </div>

        <div class="login-heading">
          <p class="eyebrow">
            Bienvenue
          </p>

          <h2>
            Connectez-vous
          </h2>

          <p>
            Utilisez votre compte SmartDeploy.
          </p>
        </div>

        <form @submit.prevent="submitLogin">
          <div class="form-group">
            <label for="email">
              Adresse email
            </label>

            <input
              id="email"
              v-model="form.email"
              type="email"
              autocomplete="email"
              placeholder="admin@smartdeploy.com"
              :disabled="authStore.loading"
            />
          </div>

          <div class="form-group">
            <label for="password">
              Mot de passe
            </label>

            <input
              id="password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="Votre mot de passe"
              :disabled="authStore.loading"
            />
          </div>

          <p
            v-if="authStore.errorMessage"
            class="error-message"
          >
            {{ authStore.errorMessage }}
          </p>

          <button
            type="submit"
            class="login-button"
            :disabled="authStore.loading"
          >
            {{
              authStore.loading
                ? 'Connexion...'
                : 'Se connecter'
            }}
          </button>
        </form>

        <p class="security-message">
          Connexion sécurisée avec JWT.
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns:
    minmax(420px, 1fr)
    minmax(420px, 1fr);

  background: #ffffff;
}

/* ==========================================================
 *                    PARTIE VISUELLE
 * ==========================================================
 */

.login-visual {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  justify-content: space-between;

  padding: 42px 54px;

  background:
    radial-gradient(
      circle at 10% 15%,
      rgb(59 130 246 / 28%),
      transparent 32%
    ),
    linear-gradient(
      145deg,
      #0f172a,
      #172554
    );

  color: #ffffff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 13px;
}

.brand-icon {
  display: grid;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  place-items: center;

  border-radius: 13px;
  background: #2563eb;
  color: #ffffff;

  font-size: 22px;
  font-weight: 800;
}

.brand div:last-child {
  display: flex;
  flex-direction: column;
}

.brand strong {
  font-size: 17px;
}

.brand span {
  margin-top: 4px;

  color: #94a3b8;
  font-size: 11px;
}

.visual-content {
  max-width: 610px;
}

.eyebrow {
  margin: 0 0 13px;

  color: #60a5fa;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.visual-content h1 {
  max-width: 580px;
  margin: 0;

  font-size: clamp(38px, 5vw, 62px);
  line-height: 1.08;
}

.visual-content > p:last-child {
  max-width: 560px;
  margin: 25px 0 0;

  color: #cbd5e1;
  font-size: 17px;
  line-height: 1.7;
}

.visual-footer {
  color: #94a3b8;
  font-size: 12px;
}

/* ==========================================================
 *                    PARTIE FORMULAIRE
 * ==========================================================
 */

.login-section {
  display: grid;
  min-height: 100vh;
  place-items: center;

  padding: 40px;
}

.login-card {
  width: min(100%, 430px);
}

.mobile-brand {
  display: none;
}

.login-heading h2 {
  margin: 0;

  color: #0f172a;
  font-size: 32px;
}

.login-heading > p:last-child {
  margin: 10px 0 0;

  color: #64748b;
}

form {
  display: flex;
  flex-direction: column;
  gap: 20px;

  margin-top: 35px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

label {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

input {
  min-height: 49px;
  padding: 0 15px;

  border: 1px solid #cbd5e1;
  border-radius: 10px;
  outline: none;

  color: #0f172a;
  background: #ffffff;

  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

input::placeholder {
  color: #94a3b8;
}

input:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px rgb(37 99 235 / 10%);
}

input:disabled {
  cursor: not-allowed;
  background: #f8fafc;
  opacity: 0.75;
}

/* ==========================================================
 *                       ERREUR
 * ==========================================================
 */

.error-message {
  margin: 0;
  padding: 11px 13px;

  border: 1px solid #fecaca;
  border-radius: 9px;
  background: #fef2f2;
  color: #b91c1c;

  font-size: 12px;
  line-height: 1.5;
}

/* ==========================================================
 *                       BOUTON
 * ==========================================================
 */

.login-button {
  min-height: 49px;

  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;

  font-weight: 800;

  transition:
    background 0.2s ease,
    transform 0.15s ease;
}

.login-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.login-button:active:not(:disabled) {
  transform: translateY(1px);
}

.login-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.security-message {
  margin: 25px 0 0;

  color: #94a3b8;
  font-size: 11px;
  text-align: center;
}

/* ==========================================================
 *                      RESPONSIVE
 * ==========================================================
 */

@media (max-width: 900px) {
  .login-page {
    display: block;
  }

  .login-visual {
    display: none;
  }

  .login-section {
    padding: 28px 20px;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 12px;

    margin-bottom: 45px;
  }
}
</style>