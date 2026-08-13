<script setup lang="ts">
import axios from 'axios'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { register } from '@/api/auth.api'

const router = useRouter()

const form = reactive({
  firstName: '',
  lastName: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const loading = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

async function submitRegister() {
  errorMessage.value = null
  successMessage.value = null

  if (
    !form.firstName.trim() ||
    !form.lastName.trim() ||
    !form.email.trim() ||
    !form.password ||
    !form.confirmPassword
  ) {
    errorMessage.value =
      'Veuillez remplir tous les champs.'

    return
  }

  if (form.password !== form.confirmPassword) {
    errorMessage.value =
      'Les mots de passe ne correspondent pas.'

    return
  }

  if (form.password.length < 8) {
    errorMessage.value =
      'Le mot de passe doit contenir au moins 8 caractères.'

    return
  }

  loading.value = true

  try {
    await register({
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      email: form.email.trim().toLowerCase(),
      password: form.password,
    })

    successMessage.value =
      'Compte créé avec succès. Redirection vers la connexion...'

    setTimeout(() => {
      router.push({
        path: '/login',
        query: {
          registered: 'true',
          email: form.email.trim().toLowerCase(),
        },
      })
    }, 1200)
  } catch (error) {
    if (axios.isAxiosError(error)) {
      errorMessage.value =
        error.response?.data?.message ??
        'Impossible de créer le compte.'
    } else {
      errorMessage.value =
        'Une erreur inattendue est survenue.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <section class="register-visual">
      <div class="brand">
        <div class="brand-icon">S</div>

        <div>
          <strong>SmartDeploy</strong>
          <span>DevOps Deployment Platform</span>
        </div>
      </div>

      <div class="visual-content">
        <p class="eyebrow">
          Rejoindre SmartDeploy
        </p>

        <h1>
          Centralisez et sécurisez vos déploiements.
        </h1>

        <p>
          Créez votre compte pour gérer les applications,
          les versions, les environnements et les pipelines
          de déploiement.
        </p>
      </div>

      <div class="visual-footer">
        Jenkins · Docker · Kubernetes · Prometheus
      </div>
    </section>

    <section class="register-section">
      <div class="register-card">
        <div class="register-heading">
          <p class="eyebrow">Nouveau compte</p>
          <h2>Créer un compte</h2>

          <p>
            Renseignez vos informations personnelles.
          </p>
        </div>

        <form @submit.prevent="submitRegister">
          <div class="name-grid">
            <div class="form-group">
              <label for="firstName">Prénom</label>

              <input
                id="firstName"
                v-model="form.firstName"
                type="text"
                autocomplete="given-name"
                placeholder="Ruqaia"
              />
            </div>

            <div class="form-group">
              <label for="lastName">Nom</label>

              <input
                id="lastName"
                v-model="form.lastName"
                type="text"
                autocomplete="family-name"
                placeholder="Sabta"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="email">Adresse email</label>

            <input
              id="email"
              v-model="form.email"
              type="email"
              autocomplete="email"
              placeholder="ruqaia@smartdeploy.com"
            />
          </div>

          <div class="form-group">
            <label for="password">Mot de passe</label>

            <input
              id="password"
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="Au moins 8 caractères"
            />
          </div>

          <div class="form-group">
            <label for="confirmPassword">
              Confirmation du mot de passe
            </label>

            <input
              id="confirmPassword"
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="Répétez le mot de passe"
            />
          </div>

          <p
            v-if="errorMessage"
            class="message error-message"
          >
            {{ errorMessage }}
          </p>

          <p
            v-if="successMessage"
            class="message success-message"
          >
            {{ successMessage }}
          </p>

          <button
            type="submit"
            class="register-button"
            :disabled="loading"
          >
            {{
              loading
                ? 'Création en cours...'
                : 'Créer mon compte'
            }}
          </button>
        </form>

        <p class="login-link">
          Vous avez déjà un compte ?

          <RouterLink to="/login">
            Se connecter
          </RouterLink>
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns:
    minmax(420px, 1fr)
    minmax(500px, 1fr);

  background: #ffffff;
}

.register-visual {
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
    linear-gradient(145deg, #0f172a, #172554);

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
  place-items: center;

  border-radius: 13px;
  background: #2563eb;

  font-size: 22px;
  font-weight: 800;
}

.brand > div:last-child {
  display: flex;
  flex-direction: column;
}

.brand span {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 11px;
}

.visual-content {
  max-width: 600px;
}

.eyebrow {
  margin: 0 0 12px;

  color: #60a5fa;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.visual-content h1 {
  margin: 0;

  font-size: clamp(38px, 5vw, 60px);
  line-height: 1.08;
}

.visual-content > p:last-child {
  margin: 24px 0 0;

  color: #cbd5e1;
  font-size: 17px;
  line-height: 1.7;
}

.visual-footer {
  color: #94a3b8;
  font-size: 12px;
}

.register-section {
  display: grid;
  min-height: 100vh;
  place-items: center;

  padding: 40px;
}

.register-card {
  width: min(100%, 500px);
}

.register-heading h2 {
  margin: 0;

  color: #0f172a;
  font-size: 32px;
}

.register-heading > p:last-child {
  margin: 10px 0 0;
  color: #64748b;
}

form {
  display: flex;
  flex-direction: column;
  gap: 18px;

  margin-top: 30px;
}

.name-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

label {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

input {
  min-height: 48px;
  padding: 0 15px;

  border: 1px solid #cbd5e1;
  border-radius: 10px;
  outline: none;

  background: #ffffff;
  color: #0f172a;

  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

input:focus {
  border-color: #2563eb;
  box-shadow:
    0 0 0 4px rgb(37 99 235 / 10%);
}

.message {
  margin: 0;
  padding: 11px 13px;

  border-radius: 9px;
  font-size: 12px;
}

.error-message {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.success-message {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.register-button {
  min-height: 49px;

  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;

  font-weight: 800;
}

.register-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.register-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.login-link {
  margin: 24px 0 0;

  color: #64748b;
  font-size: 13px;
  text-align: center;
}

.login-link a {
  color: #2563eb;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 900px) {
  .register-page {
    display: block;
  }

  .register-visual {
    display: none;
  }

  .register-section {
    padding: 28px 20px;
  }
}

@media (max-width: 520px) {
  .name-grid {
    grid-template-columns: 1fr;
  }
}
</style>