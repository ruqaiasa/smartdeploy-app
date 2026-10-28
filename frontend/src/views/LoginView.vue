<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')

const errorMessage = ref<string | null>(null)
const loading = ref(false)

const language = ref<'FR' | 'EN'>('FR')

async function handleLogin() {
  errorMessage.value = null

  if (!email.value.trim()) {
    errorMessage.value =
      'Veuillez saisir votre adresse e-mail.'
    return
  }

  if (!password.value) {
    errorMessage.value =
      'Veuillez saisir votre mot de passe.'
    return
  }

  loading.value = true

  try {
    await authStore.login({
      email: email.value.trim(),
      password: password.value,
    })

    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : '/dashboard'

    await router.push(redirect)
  } catch (error) {
    console.error(
      'Erreur lors de la connexion',
      error,
    )

    errorMessage.value =
      'E-mail ou mot de passe incorrect.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">

    <!-- ======================================================
         LEFT SIDE
         ====================================================== -->

    <section class="visual-panel">

      <div class="visual-content">

        <!-- BRAND -->

        <div class="brand">
          <div class="brand-logo">
            SD
          </div>

          <span class="brand-name">
            SmartDeploy
          </span>
        </div>

        <!-- HERO -->

        <div class="hero-content">

          <p class="eyebrow">
            DEVOPS AUTOMATION PLATFORM
          </p>

          <h1>
            Deploy.
            <br />
            Observe.
            <br />
            <span>Control.</span>
          </h1>

          <p class="hero-description">
            Une plateforme centralisée pour automatiser,
            superviser et sécuriser le cycle de déploiement
            de vos applications.
          </p>

          <!-- FEATURES -->

          <div class="features">

            <div class="feature">
              <div class="feature-icon">
                ✓
              </div>

              <div>
                <strong>
                  CI/CD automatisé
                </strong>

                <span>
                  Pipelines Jenkins et déploiements continus
                </span>
              </div>
            </div>

            <div class="feature">
              <div class="feature-icon">
                ✓
              </div>

              <div>
                <strong>
                  Orchestration Kubernetes
                </strong>

                <span>
                  Gestion centralisée des environnements
                </span>
              </div>
            </div>

            <div class="feature">
              <div class="feature-icon">
                ✓
              </div>

              <div>
                <strong>
                  Monitoring & alerting
                </strong>

                <span>
                  Métriques et alertes en temps réel
                </span>
              </div>
            </div>

          </div>

        </div>

        <!-- LEFT FOOTER -->

        <div class="visual-footer">
          <span class="status-dot"></span>

          Infrastructure opérationnelle
        </div>

      </div>

      <!-- DECORATION -->

      <div class="grid-decoration"></div>
      <div class="glow glow-one"></div>
      <div class="glow glow-two"></div>

    </section>

    <!-- ======================================================
         RIGHT SIDE
         ====================================================== -->

    <section class="form-panel">

      <!-- ====================================================
           TOP BAR
           ==================================================== -->

      <header class="top-bar">

        <!-- LANGUAGE -->

        <div class="language-selector">

          <span class="language-globe">
            🌐
          </span>

          <select
            v-model="language"
            aria-label="Choisir la langue"
            title="Choisir la langue"
          >
            <option value="FR">
              Français
            </option>

            <option value="EN">
              English
            </option>
          </select>

        </div>

        <!-- REGISTER -->

        <div class="register-top">

          <span class="register-question">
            Pas encore de compte ?
          </span>

          <RouterLink
            to="/register"
            class="register-button"
          >
            Créer un compte
          </RouterLink>

        </div>

      </header>

      <!-- ====================================================
           LOGIN CONTENT
           ==================================================== -->

      <div class="form-wrapper">

        <!-- MOBILE BRAND -->

        <div class="mobile-brand">

          <div class="brand-logo">
            SD
          </div>

          <span>
            SmartDeploy
          </span>

        </div>

        <!-- HEADING -->

        <div class="form-heading">

          <p class="form-eyebrow">
            BIENVENUE
          </p>

          <h2>
            Se connecter
          </h2>

          <p>
            Connectez-vous pour accéder à votre espace
            de déploiement SmartDeploy.
          </p>

        </div>

        <!-- FORM -->

        <form
          class="login-form"
          @submit.prevent="handleLogin"
        >

          <!-- EMAIL -->

          <div class="form-group">

            <label for="email">
              Adresse e-mail
            </label>

            <div class="input-wrapper">

              <span class="input-icon">
                @
              </span>

              <input
                id="email"
                v-model="email"
                type="email"
                autocomplete="email"
                placeholder="vous@entreprise.com"
                :disabled="loading"
              />

            </div>

          </div>

          <!-- PASSWORD -->

          <div class="form-group">

            <label for="password">
              Mot de passe
            </label>

            <div class="input-wrapper">

              <span class="input-icon password-icon">
                •
              </span>

              <input
                id="password"
                v-model="password"
                type="password"
                autocomplete="current-password"
                placeholder="Votre mot de passe"
                :disabled="loading"
              />

            </div>

          </div>

          <!-- ERROR -->

          <div
            v-if="errorMessage"
            class="error-message"
          >
            <span class="error-icon">
              !
            </span>

            {{ errorMessage }}
          </div>

          <!-- LOGIN BUTTON -->

          <button
            type="submit"
            class="login-button"
            :disabled="loading"
          >

            <span>
              {{
                loading
                  ? 'Connexion en cours...'
                  : 'Se connecter'
              }}
            </span>

            <span
              v-if="!loading"
              class="arrow"
            >
              →
            </span>

          </button>

        </form>

        <!-- MOBILE REGISTER -->

        <div class="mobile-register">

          <span>
            Pas encore de compte ?
          </span>

          <RouterLink to="/register">
            Créer un compte
          </RouterLink>

        </div>

      </div>

      <!-- FOOTER -->

      <footer class="form-footer">

        <span>
          SmartDeploy
        </span>

        <span class="footer-separator">
          •
        </span>

        <span>
          Plateforme DevOps
        </span>

      </footer>

    </section>

  </main>
</template>

<style scoped>
/* ============================================================
   PAGE
   ============================================================ */

.auth-page {
  display: grid;

  grid-template-columns:
    minmax(420px, 46%)
    minmax(0, 54%);

  min-height: 100vh;

  background: #ffffff;

  font-family:
    Inter,
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
}

/* ============================================================
   LEFT PANEL
   ============================================================ */

.visual-panel {
  position: relative;

  min-height: 100vh;

  overflow: hidden;

  background:
    linear-gradient(
      145deg,
      #07111f 0%,
      #0b172a 45%,
      #101c32 100%
    );

  color: white;
}

.visual-content {
  position: relative;
  z-index: 3;

  display: flex;
  flex-direction: column;

  min-height: 100vh;

  box-sizing: border-box;

  padding:
    42px
    clamp(42px, 6vw, 90px);
}

/* ============================================================
   BRAND
   ============================================================ */

.brand {
  display: flex;
  align-items: center;

  gap: 12px;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 42px;
  height: 42px;

  flex-shrink: 0;

  border: 1px solid
    rgba(255, 255, 255, 0.14);

  border-radius: 11px;

  background:
    linear-gradient(
      135deg,
      #2563eb,
      #3b82f6
    );

  box-shadow:
    0 10px 30px
    rgba(37, 99, 235, 0.25);

  color: #ffffff;

  font-size: 13px;
  font-weight: 800;

  letter-spacing: 0.5px;
}

.brand-name {
  color: #f8fafc;

  font-size: 18px;
  font-weight: 700;

  letter-spacing: -0.3px;
}

/* ============================================================
   HERO
   ============================================================ */

.hero-content {
  width: 100%;
  max-width: 520px;

  margin: auto 0;

  padding: 65px 0;
}

.eyebrow {
  margin: 0 0 20px;

  color: #60a5fa;

  font-size: 11px;
  font-weight: 700;

  letter-spacing: 2.4px;
}

.hero-content h1 {
  margin: 0;

  color: #f8fafc;

  font-size:
    clamp(45px, 5vw, 70px);

  font-weight: 700;

  line-height: 0.98;

  letter-spacing: -3px;
}

.hero-content h1 span {
  color: #3b82f6;
}

.hero-description {
  max-width: 460px;

  margin: 28px 0 0;

  color: #94a3b8;

  font-size: 15px;

  line-height: 1.75;
}

/* ============================================================
   FEATURES
   ============================================================ */

.features {
  display: flex;
  flex-direction: column;

  gap: 17px;

  margin-top: 38px;
}

.feature {
  display: flex;
  align-items: center;

  gap: 13px;
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 28px;
  height: 28px;

  flex-shrink: 0;

  border: 1px solid
    rgba(59, 130, 246, 0.28);

  border-radius: 8px;

  background:
    rgba(37, 99, 235, 0.11);

  color: #60a5fa;

  font-size: 12px;
  font-weight: 800;
}

.feature > div:last-child {
  display: flex;
  flex-direction: column;

  gap: 2px;
}

.feature strong {
  color: #e2e8f0;

  font-size: 13px;
  font-weight: 600;
}

.feature span {
  color: #64748b;

  font-size: 11px;
}

/* ============================================================
   LEFT FOOTER
   ============================================================ */

.visual-footer {
  display: flex;
  align-items: center;

  gap: 8px;

  color: #64748b;

  font-size: 11px;
}

.status-dot {
  width: 7px;
  height: 7px;

  border-radius: 50%;

  background: #22c55e;

  box-shadow:
    0 0 0 4px
    rgba(34, 197, 94, 0.08);
}

/* ============================================================
   LEFT DECORATION
   ============================================================ */

.grid-decoration {
  position: absolute;

  inset: 0;

  z-index: 1;

  opacity: 0.11;

  background-image:
    linear-gradient(
      rgba(148, 163, 184, 0.22) 1px,
      transparent 1px
    ),
    linear-gradient(
      90deg,
      rgba(148, 163, 184, 0.22) 1px,
      transparent 1px
    );

  background-size: 48px 48px;

  pointer-events: none;
}

.glow {
  position: absolute;

  z-index: 2;

  border-radius: 50%;

  pointer-events: none;
}

.glow-one {
  right: -180px;
  bottom: 80px;

  width: 430px;
  height: 430px;

  background:
    radial-gradient(
      circle,
      rgba(37, 99, 235, 0.15),
      transparent 68%
    );
}

.glow-two {
  top: -150px;
  left: -170px;

  width: 400px;
  height: 400px;

  background:
    radial-gradient(
      circle,
      rgba(14, 165, 233, 0.08),
      transparent 68%
    );
}

/* ============================================================
   RIGHT PANEL
   ============================================================ */

.form-panel {
  position: relative;

  display: flex;
  flex-direction: column;

  min-height: 100vh;

  box-sizing: border-box;

  background: #ffffff;
}

/* ============================================================
   TOP BAR
   ============================================================ */

.top-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;

  gap: 22px;

  min-height: 88px;

  padding: 0 52px;

  box-sizing: border-box;

  border-bottom: 1px solid #eef2f7;

  background:
    rgba(255, 255, 255, 0.97);

  box-shadow:
    0 1px 0
    rgba(15, 23, 42, 0.015);
}

/* ============================================================
   LANGUAGE SELECTOR
   ============================================================ */

.language-selector {
  position: relative;

  display: flex;
  align-items: center;

  min-width: 134px;
  height: 42px;

  padding: 0 10px;

  box-sizing: border-box;

  border: 1px solid #e2e8f0;
  border-radius: 10px;

  background: #ffffff;

  box-shadow:
    0 1px 2px
    rgba(15, 23, 42, 0.03);

  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.15s ease;
}

.language-selector:hover {
  border-color: #cbd5e1;

  background: #f8fafc;

  box-shadow:
    0 4px 12px
    rgba(15, 23, 42, 0.06);

  transform: translateY(-1px);
}

.language-selector:focus-within {
  border-color: #93c5fd;

  background: #ffffff;

  box-shadow:
    0 0 0 3px
    rgba(37, 99, 235, 0.08);
}

.language-globe {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 27px;
  height: 27px;

  flex-shrink: 0;

  margin-right: 6px;

  border-radius: 7px;

  background:
    linear-gradient(
      135deg,
      #eff6ff,
      #dbeafe
    );

  font-size: 13px;

  line-height: 1;

  pointer-events: none;
}

.language-selector select {
  flex: 1;

  min-width: 0;
  height: 100%;

  padding: 0 2px;

  border: none;
  outline: none;

  background: transparent;

  color: #334155;

  cursor: pointer;

  font-family: inherit;
  font-size: 12px;
  font-weight: 600;

  letter-spacing: -0.1px;
}

.language-selector select:focus {
  outline: none;
}

.language-selector option {
  background: #ffffff;

  color: #0f172a;

  font-size: 13px;
  font-weight: 500;
}

/* ============================================================
   REGISTER AREA
   ============================================================ */

.register-top {
  position: relative;

  display: flex;
  align-items: center;

  gap: 14px;

  padding-left: 23px;
}

.register-top::before {
  content: "";

  position: absolute;

  left: 0;
  top: 50%;

  width: 1px;
  height: 27px;

  transform: translateY(-50%);

  background:
    linear-gradient(
      to bottom,
      transparent,
      #dbe3ed,
      transparent
    );
}

.register-question {
  color: #64748b;

  font-size: 12px;
  font-weight: 450;

  white-space: nowrap;
}

/* ============================================================
   REGISTER BUTTON
   ============================================================ */

.register-button {
  position: relative;

  display: inline-flex;
  align-items: center;
  justify-content: center;

  min-height: 42px;

  padding: 0 18px;

  box-sizing: border-box;

  overflow: hidden;

  border: 1px solid #dbe4ef;
  border-radius: 10px;

  background:
    linear-gradient(
      180deg,
      #ffffff 0%,
      #f8fafc 100%
    );

  color: #1e293b;

  font-size: 12px;
  font-weight: 650;

  text-decoration: none;

  white-space: nowrap;

  box-shadow:
    0 1px 2px
    rgba(15, 23, 42, 0.03),
    0 3px 8px
    rgba(15, 23, 42, 0.025);

  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.15s ease;
}

.register-button:hover {
  border-color: #93c5fd;

  background:
    linear-gradient(
      180deg,
      #ffffff 0%,
      #eff6ff 100%
    );

  color: #2563eb;

  box-shadow:
    0 5px 14px
    rgba(37, 99, 235, 0.09);

  transform: translateY(-1px);
}

.register-button:active {
  transform: translateY(0);

  box-shadow:
    0 2px 6px
    rgba(15, 23, 42, 0.06);
}

/* ============================================================
   FORM WRAPPER
   ============================================================ */

.form-wrapper {
  width: min(100% - 48px, 430px);

  margin: auto;

  padding: 50px 0 70px;
}

.mobile-brand {
  display: none;
}

/* ============================================================
   FORM HEADING
   ============================================================ */

.form-heading {
  margin-bottom: 34px;
}

.form-eyebrow {
  margin: 0 0 9px;

  color: #2563eb;

  font-size: 10px;
  font-weight: 700;

  letter-spacing: 1.7px;
}

.form-heading h2 {
  margin: 0;

  color: #0f172a;

  font-size: 30px;
  font-weight: 700;

  letter-spacing: -0.8px;
}

.form-heading > p:last-child {
  max-width: 390px;

  margin: 10px 0 0;

  color: #64748b;

  font-size: 13px;

  line-height: 1.6;
}

/* ============================================================
   FORM
   ============================================================ */

.login-form {
  display: flex;
  flex-direction: column;

  gap: 21px;
}

.form-group {
  display: flex;
  flex-direction: column;

  gap: 8px;
}

.form-group label {
  color: #334155;

  font-size: 12px;
  font-weight: 600;
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;

  top: 50%;
  left: 13px;

  transform: translateY(-50%);

  color: #94a3b8;

  font-size: 12px;
  font-weight: 600;

  pointer-events: none;
}

.password-icon {
  font-size: 18px;
}

.input-wrapper input {
  width: 100%;
  height: 45px;

  box-sizing: border-box;

  padding:
    0
    13px
    0
    38px;

  border: 1px solid #dbe2ea;
  border-radius: 8px;

  outline: none;

  background: #ffffff;

  color: #0f172a;

  font-family: inherit;
  font-size: 13px;

  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}

.input-wrapper input::placeholder {
  color: #a8b2c1;
}

.input-wrapper input:hover:not(:disabled) {
  border-color: #b8c4d2;
}

.input-wrapper input:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 3px
    rgba(37, 99, 235, 0.08);
}

.input-wrapper input:disabled {
  cursor: not-allowed;

  background: #f8fafc;

  color: #94a3b8;
}

/* ============================================================
   ERROR
   ============================================================ */

.error-message {
  display: flex;
  align-items: center;

  gap: 8px;

  padding: 10px 12px;

  border: 1px solid #fecaca;
  border-radius: 8px;

  background: #fef2f2;

  color: #b91c1c;

  font-size: 12px;
}

.error-icon {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 18px;
  height: 18px;

  flex-shrink: 0;

  border-radius: 50%;

  background: #fee2e2;

  font-size: 10px;
  font-weight: 700;
}

/* ============================================================
   LOGIN BUTTON
   ============================================================ */

.login-button {
  display: flex;
  align-items: center;
  justify-content: center;

  gap: 12px;

  width: 100%;
  height: 46px;

  margin-top: 3px;

  border: none;
  border-radius: 8px;

  background: #2563eb;

  color: #ffffff;

  cursor: pointer;

  font-family: inherit;
  font-size: 13px;
  font-weight: 600;

  box-shadow:
    0 5px 14px
    rgba(37, 99, 235, 0.18);

  transition:
    background 0.2s,
    transform 0.1s,
    box-shadow 0.2s;
}

.login-button:hover:not(:disabled) {
  background: #1d4ed8;

  box-shadow:
    0 7px 18px
    rgba(37, 99, 235, 0.23);
}

.login-button:active:not(:disabled) {
  transform: translateY(1px);
}

.login-button:disabled {
  cursor: not-allowed;

  opacity: 0.65;

  box-shadow: none;
}

.arrow {
  font-size: 16px;

  transition: transform 0.2s;
}

.login-button:hover:not(:disabled) .arrow {
  transform: translateX(3px);
}

/* ============================================================
   MOBILE REGISTER
   ============================================================ */

.mobile-register {
  display: none;
}

/* ============================================================
   FOOTER
   ============================================================ */

.form-footer {
  display: flex;
  align-items: center;
  justify-content: center;

  gap: 8px;

  min-height: 55px;

  padding: 0 24px;

  box-sizing: border-box;

  border-top: 1px solid #f8fafc;

  color: #94a3b8;

  font-size: 10px;
}

.footer-separator {
  color: #cbd5e1;
}

/* ============================================================
   RESPONSIVE
   ============================================================ */

@media (max-width: 1050px) {
  .auth-page {
    grid-template-columns:
      minmax(350px, 42%)
      minmax(0, 58%);
  }

  .visual-content {
    padding:
      36px
      42px;
  }

  .hero-content h1 {
    font-size: 48px;
  }

  .top-bar {
    gap: 18px;

    padding:
      0
      30px;
  }

  .register-top {
    gap: 10px;

    padding-left: 18px;
  }
}

@media (max-width: 900px) {
  .register-question {
    display: none;
  }
}

@media (max-width: 820px) {
  .auth-page {
    display: block;
  }

  .visual-panel {
    display: none;
  }

  .form-panel {
    min-height: 100vh;
  }

  .top-bar {
    justify-content: flex-end;

    min-height: 72px;

    padding:
      0
      22px;
  }

  .register-top {
    display: none;
  }

  .language-selector {
    min-width: 128px;
    height: 40px;
  }

  .mobile-brand {
    display: flex;
    align-items: center;

    gap: 10px;

    margin-bottom: 45px;

    color: #0f172a;

    font-size: 17px;
    font-weight: 700;
  }

  .mobile-brand .brand-logo {
    width: 38px;
    height: 38px;
  }

  .mobile-register {
    display: flex;
    align-items: center;
    justify-content: center;

    gap: 5px;

    margin-top: 25px;

    color: #64748b;

    font-size: 12px;
  }

  .mobile-register a {
    color: #2563eb;

    font-weight: 600;

    text-decoration: none;
  }

  .mobile-register a:hover {
    text-decoration: underline;
  }

  .form-wrapper {
    width:
      min(
        calc(100% - 40px),
        430px
      );

    padding:
      35px
      0
      60px;
  }
}

@media (max-width: 480px) {
  .top-bar {
    padding:
      0
      20px;
  }

  .language-selector {
    min-width: 124px;
  }

  .form-heading h2 {
    font-size: 27px;
  }

  .form-wrapper {
    width:
      calc(100% - 32px);
  }
}
</style>