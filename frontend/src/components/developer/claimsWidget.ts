import { reactive } from 'vue'

/* Bulle des réclamations du développeur : ouverte depuis n’importe quelle page (par exemple « Signaler »). */
export const claimsWidget = reactive({
  open: false,
  prefill: null as { applicationId: number | null; deploymentId: number | null } | null,
})

export function openClaimsWidget(prefill?: { applicationId: number | null; deploymentId: number | null }) {
  claimsWidget.prefill = prefill ?? null
  claimsWidget.open = true
}
