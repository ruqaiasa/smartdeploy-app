<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { createArticle, deleteArticle, markHelpful, searchKnowledge, updateArticle } from '@/api/knowledge.api'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeArticle } from '@/types/portal'

/* Aide : la base de connaissances, construite à partir des problèmes déjà résolus. */
const auth = useAuthStore()
const isTeam = computed(() => auth.hasRole('DEVOPS', 'ADMIN'))
const q = ref('')
const articles = ref<KnowledgeArticle[]>([])
const helped = ref<Set<number>>(new Set())
const editing = ref<KnowledgeArticle | null>(null)
const creating = ref(false)
const form = reactive({ title: '', symptoms: '', cause: '', solution: '', signature: '' })
const error = ref('')
let timer: ReturnType<typeof setTimeout> | null = null

async function load() { try { articles.value = await searchKnowledge(q.value.trim()) } catch { articles.value = [] } }
onMounted(load)
watch(q, () => { if (timer) clearTimeout(timer); timer = setTimeout(load, 300) })

async function helpful(a: KnowledgeArticle) {
  if (helped.value.has(a.id)) return
  const updated = await markHelpful(a.id).catch(() => null)
  if (updated) a.helpfulCount = updated.helpfulCount
  helped.value.add(a.id)
}
function openEditor(a: KnowledgeArticle | null) {
  editing.value = a
  creating.value = a === null
  Object.assign(form, { title: a?.title ?? '', symptoms: a?.symptoms ?? '', cause: a?.cause ?? '', solution: a?.solution ?? '', signature: a?.signature ?? '' })
  error.value = ''
}
function closeEditor() { editing.value = null; creating.value = false }
async function save() {
  try {
    if (editing.value) await updateArticle(editing.value.id, { ...form })
    else await createArticle({ ...form })
    closeEditor()
    await load()
  } catch (exception) { error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'La fiche n’a pas pu être enregistrée.' }
}
async function remove(a: KnowledgeArticle) {
  if (!window.confirm(`Supprimer la fiche « ${a.title} » ?`)) return
  await deleteArticle(a.id).catch(() => undefined)
  await load()
}
</script>

<template>
  <section class="kb">
    <header class="kb-head">
      <div>
        <h1>Aide</h1>
        <p>Problèmes déjà résolus.</p>
      </div>
      <button v-if="isTeam" type="button" class="kb-btn kb-primary" @click="openEditor(null)">+ Fiche</button>
    </header>
    <input v-model="q" type="search" class="kb-search" placeholder="Rechercher" aria-label="Rechercher" />

    <form v-if="editing || creating" class="kb-editor" @submit.prevent="save">
      <label>Titre<input v-model="form.title" required maxlength="200" /></label>
      <label>Symptômes<textarea v-model="form.symptoms" rows="2" maxlength="2000" /></label>
      <label>Cause<input v-model="form.cause" maxlength="2000" /></label>
      <label>Solution<textarea v-model="form.solution" rows="4" required maxlength="4000" /></label>
      <label>Signature (facultative)<input v-model="form.signature" maxlength="60" placeholder="par exemple : COMPILATION" /></label>
      <p v-if="error" class="kb-error">{{ error }}</p>
      <footer><button type="button" class="kb-btn" @click="closeEditor">Annuler</button><button type="submit" class="kb-btn kb-primary">Enregistrer</button></footer>
    </form>

    <p v-if="!articles.length" class="kb-empty">{{ q ? 'Aucun résultat.' : 'Aucune fiche.' }}</p>
    <article v-for="a in articles" :key="a.id" class="kb-card">
      <header>
        <h2>{{ a.title }}</h2>
        <span v-if="a.score" class="kb-score">{{ a.score }} %</span>
      </header>
      <p v-if="a.symptoms"><strong>Symptômes :</strong> {{ a.symptoms }}</p>
      <p v-if="a.cause"><strong>Cause :</strong> {{ a.cause }}</p>
      <p class="kb-solution"><strong>Solution :</strong> {{ a.solution }}</p>
      <footer>
        <span class="kb-muted">A aidé {{ a.helpfulCount }} fois<template v-if="a.sourceClaimId"> · issue de la réclamation #{{ a.sourceClaimId }}</template></span>
        <div>
          <button type="button" class="kb-link" :disabled="helped.has(a.id)" @click="helpful(a)">{{ helped.has(a.id) ? 'Merci' : 'Utile' }}</button>
          <template v-if="isTeam">
            <button type="button" class="kb-link" @click="openEditor(a)">Modifier</button>
            <button type="button" class="kb-link kb-danger" @click="remove(a)">Supprimer</button>
          </template>
        </div>
      </footer>
    </article>
  </section>
</template>

<style scoped>
.kb { display: flex; flex-direction: column; gap: 12px; max-width: 980px; }
.kb-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.kb-head h1 { margin: 0; font-size: 24px; }
.kb-head p { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.kb-search { padding: 11px 14px; border: 1px solid #e5e7eb; border-radius: 10px; font: inherit; font-size: 15px; }
.kb-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.kb-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.kb-editor { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px; border: 1px solid #c7d2fe; border-radius: 12px; background: #f8faff; }
.kb-editor label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; }
.kb-editor input, .kb-editor textarea { padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; font-weight: 400; }
.kb-editor footer { display: flex; justify-content: flex-end; gap: 8px; }
.kb-empty { margin: 0; padding: 22px; border: 1px dashed #e5e7eb; border-radius: 12px; color: #6b7280; text-align: center; }
.kb-card { display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; font-size: 14px; }
.kb-card header { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.kb-card h2 { margin: 0; font-size: 16px; }
.kb-card p { margin: 0; }
.kb-solution { padding: 8px 10px; border-radius: 8px; background: #f0fdf4; }
.kb-score { color: #166534; font-size: 12.5px; font-weight: 700; }
.kb-card footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.kb-muted { color: #6b7280; font-size: 12.5px; }
.kb-link { margin-left: 10px; padding: 0; border: 0; background: none; color: #1e40af; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
.kb-link:disabled { color: #166534; cursor: default; }
.kb-danger { color: #b91c1c; }
.kb-error { margin: 0; color: #b91c1c; font-size: 13px; }
</style>
