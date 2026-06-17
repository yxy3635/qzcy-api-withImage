import { defineStore } from 'pinia'
import { authApi } from '@/api/authApi'
import { clearSavedText, loadSavedText, saveText, sessionText } from '@/utils/storage'
import type { Role, UserInfo } from '@/types'

interface AuthState {
  token: string
  userInfo: UserInfo | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    userInfo: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    role: (state): Role | '' => state.userInfo?.role || ''
  },
  actions: {
    async login(username: string, password: string) {
      const { data } = await authApi.login(username, password)
      this.token = data.data.token
      this.userInfo = data.data.user
      this.persist()
      return data.data.user
    },
    async register(username: string, email: string, password: string, code: string, inviteCode = '') {
      return authApi.register(username, email, password, code, inviteCode)
    },
    async refreshUser() {
      if (!this.token) return
      const { data } = await authApi.me()
      this.userInfo = data.data
      this.persist()
    },
    logout() {
      this.token = ''
      this.userInfo = null
      clearSavedText('imageCreater_token')
      clearSavedText('imageCreater_user')
    },
    loadFromStorage() {
      const savedSession = sessionText()
      this.token = savedSession || loadSavedText('imageCreater_token') || ''
      const raw = loadSavedText('imageCreater_user')
      this.userInfo = raw ? JSON.parse(raw) : null
    },
    persist() {
      saveText('imageCreater_token', this.token)
      if (this.userInfo) {
        saveText('imageCreater_user', JSON.stringify(this.userInfo))
      }
    }
  }
})
