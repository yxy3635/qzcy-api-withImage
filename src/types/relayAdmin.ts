/** 中转站管理台各编辑抽屉共用的草稿类型；草稿实体由 AdminRelay.vue 创建并传入抽屉直接绑定。 */

export interface ProviderDraft {
  id: number | null
  name: string
  apiBaseUrl: string
  keyValue: string
  apiKeyMasked: string
  channelRule: string
  priority: number
  weight: number
  status: string
  enabled: boolean
}

export interface ChannelModelDraft {
  modelId: number
  upstreamModel: string
  enabled: boolean
}

export interface ChannelDraft {
  name: string
  provider: string
  channelRule: string
  apiBaseUrl: string
  keyValue: string
  groupNames: string
  remark: string
  priority: number
  weight: number
  rpmLimit: number
  tpmLimit: number
  maxConcurrency: number
  priceMultiplier: number
  enabled: boolean
  scheduleStrategy: string
  providers: ProviderDraft[]
  models: ChannelModelDraft[]
}

export interface ModelDraft {
  model: string
  displayName: string
  modelType: string
  inputPrice: number
  outputPrice: number
  cachedInputPrice: number
  cacheCreationPrice: number
  requestPrice: number
  fixedRequestBilling: boolean
  longContextThreshold: number
  longContextBillingMode: 'price' | 'multiplier'
  longContextMultiplier: number | null
  longContextInputPrice: number | null
  longContextOutputPrice: number | null
  longContextCachedInputPrice: number | null
  longContextCacheCreationPrice: number | null
  status: string
  enabled: boolean
  sortOrder: number
}

export interface GroupDraft {
  code: string
  name: string
  ratio: number
  enabled: boolean
  modelIds: number[]
}
