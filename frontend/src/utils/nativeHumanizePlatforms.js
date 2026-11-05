/**
 * Native 降AI platform codes.
 *
 * Kept aligned with backend `NativeHumanizeProfiles` on the native-humanize
 * platform branch: same codes, display names, and ordinary/deep vs dedicated split.
 * DAYA is not a native code. 大雅 stays on the external document route.
 */

export const DEEP_HUMANIZE_TYPE = '深度降低AI写作痕迹'
export const ORDINARY_HUMANIZE_TYPE = 'humanize'

export const NATIVE_HUMANIZE_PLATFORMS = [
  {
    value: 'GENERAL',
    label: '通用',
    description: '不绑定单一检测平台，沿用现有降 AI 原则与积分规则。',
    kind: 'GENERAL',
    route: 'native'
  },
  {
    value: 'CNKI',
    label: '知网',
    description: '按知网口径改写，可选普通或深度。',
    kind: 'DEPTH_PAIR',
    route: 'native'
  },
  {
    value: 'WEIPU',
    label: '维普',
    description: '按维普口径改写，可选普通或深度。',
    kind: 'DEPTH_PAIR',
    route: 'native'
  },
  {
    value: 'GEZIDA',
    label: '格子达',
    description: '使用格子达降 AI 专用档，不区分普通与深度。',
    kind: 'DEDICATED',
    route: 'native'
  },
  {
    value: 'PAPERPASS',
    label: 'PaperPass',
    description: '使用 PaperPass 降 AI 专用档，不区分普通与深度。',
    kind: 'DEDICATED',
    route: 'native'
  },
  {
    value: 'PAPERYY',
    label: 'PaperYY',
    description: '按 PaperYY 口径改写，可选普通或深度。',
    kind: 'DEPTH_PAIR',
    route: 'native'
  },
  {
    value: 'ZHUQUE',
    label: '朱雀',
    description: '按朱雀口径改写，可选普通或深度。',
    kind: 'DEPTH_PAIR',
    route: 'native'
  },
  {
    value: 'WANFANG',
    label: '万方',
    description: '使用万方降 AI 专用档，不区分普通与深度。',
    kind: 'DEDICATED',
    route: 'native'
  },
  {
    value: 'TURNITIN',
    label: 'Turnitin',
    optionLabel: 'Turnitin（英文）',
    description: '英文稿专用，使用 Turnitin 降 AI 专用档。',
    kind: 'DEDICATED',
    route: 'native'
  }
]

/** Existing 大雅 document entry. Do not send this code through the native upload. */
export const DAYA_PLATFORM = {
  value: 'DAYA',
  label: '大雅',
  description: 'Doki按大雅报告片段口径重组正文，并专项打碎第一、第二、第三等列举结构。',
  kind: 'EXTERNAL',
  route: 'external'
}

/**
 * 双降 still shares the old two-choice control. New native codes stay off this list
 * so 双降 keeps submitting GENERAL, or DAYA on the external route.
 */
export const DOUBLE_PLATFORM_OPTIONS = [
  {
    value: 'GENERAL',
    label: 'dropai原生降ai（可处理维普知网）',
    description: '默认沿用现有降 AI 原则、积分规则与文档处理链。',
    kind: 'GENERAL',
    route: 'native'
  },
  DAYA_PLATFORM
]

export function supportsNativeDepth(platform) {
  return NATIVE_HUMANIZE_PLATFORMS.some(item => item.value === platform && item.kind === 'DEPTH_PAIR')
}

export function canonicalNativePlatform(platform) {
  const match = NATIVE_HUMANIZE_PLATFORMS.find(item => item.value === platform)
  return match ? match.value : 'GENERAL'
}

export function documentPlatformChoices(mode) {
  if (mode === 'humanize') return [...NATIVE_HUMANIZE_PLATFORMS, DAYA_PLATFORM]
  if (mode === 'double') return DOUBLE_PLATFORM_OPTIONS
  return []
}

export function textPlatformChoices(mode) {
  return mode === 'humanize' ? NATIVE_HUMANIZE_PLATFORMS : []
}

export function coercePlatform(current, options, fallback = 'GENERAL') {
  if (!options.length) return current
  return options.some(item => item.value === current) ? current : fallback
}

export function resolveDocumentSubmission({ mode, platform, intensity }) {
  if (mode === 'rewrite') {
    return { route: 'native', mode: 'rewrite', platform: 'GENERAL' }
  }
  if (platform === 'DAYA' && (mode === 'humanize' || mode === 'double')) {
    return { route: 'external', mode, platform: 'DAYA' }
  }
  if (mode === 'humanize') {
    const code = canonicalNativePlatform(platform)
    return {
      route: 'native',
      mode: supportsNativeDepth(code) && intensity === 'deep' ? DEEP_HUMANIZE_TYPE : ORDINARY_HUMANIZE_TYPE,
      platform: code
    }
  }
  return { route: 'native', mode: 'double', platform: 'GENERAL' }
}

export function resolveTextSubmission({ mode, platform, intensity }) {
  if (mode !== 'humanize') {
    return {
      rewriteType: mode === 'double' ? 'double' : 'rewrite',
      platform: 'GENERAL'
    }
  }
  const code = canonicalNativePlatform(platform)
  return {
    rewriteType: supportsNativeDepth(code) && intensity === 'deep' ? DEEP_HUMANIZE_TYPE : ORDINARY_HUMANIZE_TYPE,
    platform: code
  }
}
