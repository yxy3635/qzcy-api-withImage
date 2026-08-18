import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/authStore'
import { tokenExpired } from '@/utils/jwt'
import HomePage from '@/views/HomePage.vue'
import RelayPage from '@/views/RelayPage.vue'
import LoginPage from '@/views/LoginPage.vue'
import RegisterPage from '@/views/RegisterPage.vue'
import UserDashboard from '@/views/user/DashboardPage.vue'
import GeneratePage from '@/views/user/GeneratePage.vue'
import HistoryPage from '@/views/user/HistoryPage.vue'
import PaymentPage from '@/views/user/PaymentPage.vue'
import ProfilePage from '@/views/user/ProfilePage.vue'
import ReferralPage from '@/views/user/ReferralPage.vue'
import AdminDashboard from '@/views/admin/AdminDashboard.vue'
import AdminAnnouncements from '@/views/admin/AdminAnnouncements.vue'
import AdminUsers from '@/views/admin/AdminUsers.vue'
import AdminUserUsage from '@/views/admin/AdminUserUsage.vue'
import AdminPricing from '@/views/admin/AdminPricing.vue'
import AdminMail from '@/views/admin/AdminMail.vue'
import AdminPayment from '@/views/admin/AdminPayment.vue'
import AdminPaymentCoupons from '@/views/admin/AdminPaymentCoupons.vue'
import AdminReferral from '@/views/admin/AdminReferral.vue'
import AdminLogs from '@/views/admin/AdminLogs.vue'
import AdminRelay from '@/views/admin/AdminRelay.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomePage },
    { path: '/docs', component: () => import('@/views/ApiDocsPage.vue') },
    { path: '/relay', component: RelayPage, meta: { requiresAuth: true } },
    { path: '/login', component: LoginPage },
    { path: '/register', component: RegisterPage },
    { path: '/create', component: GeneratePage, meta: { requiresAuth: true } },
    { path: '/user/dashboard', component: UserDashboard, meta: { requiresAuth: true } },
    { path: '/user/generate', component: GeneratePage, meta: { requiresAuth: true } },
    { path: '/user/history', component: HistoryPage, meta: { requiresAuth: true } },
    { path: '/user/payment', component: PaymentPage, meta: { requiresAuth: true } },
    { path: '/user/referral', component: ReferralPage, meta: { requiresAuth: true } },
    { path: '/user/profile', component: ProfilePage, meta: { requiresAuth: true } },
    { path: '/admin/dashboard', component: AdminDashboard, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/announcements', component: AdminAnnouncements, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/users', component: AdminUsers, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/user-usage', component: AdminUserUsage, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/pricing', component: AdminPricing, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/relay', component: AdminRelay, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/payment', component: AdminPayment, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/payment/coupons', component: AdminPaymentCoupons, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/referral', component: AdminReferral, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/mail', component: AdminMail, meta: { requiresAuth: true, admin: true } },
    { path: '/admin/logs', component: AdminLogs, meta: { requiresAuth: true, admin: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (auth.token && tokenExpired(auth.token)) {
    auth.logout()
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && auth.role !== 'ADMIN') {
    return '/user/dashboard'
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isAuthenticated) {
    return auth.role === 'ADMIN' ? '/admin/dashboard' : '/create'
  }
})

export default router
