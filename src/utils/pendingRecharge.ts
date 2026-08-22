const pendingRechargeKey = 'imageCreater_pending_recharge_order'

export function rememberPendingRecharge(orderId: unknown) {
  const normalized = Number(orderId)
  if (Number.isInteger(normalized) && normalized > 0) {
    window.localStorage.setItem(pendingRechargeKey, String(normalized))
  }
}

export function pendingRechargeOrderId() {
  const normalized = Number(window.localStorage.getItem(pendingRechargeKey) || 0)
  return Number.isInteger(normalized) && normalized > 0 ? normalized : null
}

export function clearPendingRecharge(orderId?: unknown) {
  const current = pendingRechargeOrderId()
  if (orderId === undefined || current === Number(orderId)) {
    window.localStorage.removeItem(pendingRechargeKey)
  }
}

export function isBackForwardNavigation() {
  const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
  return navigation?.type === 'back_forward'
}
