import { ref, watch } from 'vue'

const sidebarStorageKey = 'imageCreater_sidebar_collapsed'
const sidebarCollapsed = ref(
  typeof window !== 'undefined' && window.localStorage.getItem(sidebarStorageKey) === 'true'
)

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === sidebarStorageKey) {
      sidebarCollapsed.value = event.newValue === 'true'
    }
  })
}

watch(sidebarCollapsed, (value) => {
  window.localStorage.setItem(sidebarStorageKey, String(value))
})

export function useSidebarPreference() {
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { sidebarCollapsed, toggleSidebar }
}
