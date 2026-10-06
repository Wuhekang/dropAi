export const documentPlatforms = [
  { value: 'GENERAL', label: 'dropai原生降ai（可处理维普知网）', description: '默认沿用现有降 AI 原则、积分规则与文档处理链。' },
  { value: 'DAYA', label: '大雅', description: 'Doki按大雅报告片段口径重组正文，并专项打碎第一、第二、第三等列举结构。' },
  { value: 'GEZIDA', label: '格子达专项', description: '直接降 AI：按段落功能重组表达，保留事实与格式；不叠加降重，按降 AI 计费。效果需格子达复测。' }
]

export function documentRoute(mode, platform) {
  if (mode === 'rewrite') return { mode, platform: 'GENERAL', external: false }
  if (platform === 'GEZIDA') return { mode: 'humanize', platform, external: false }
  return { mode, platform: platform === 'DAYA' ? 'DAYA' : 'GENERAL', external: platform === 'DAYA' }
}

export function selectDocumentPlatform(mode, platform) {
  return { mode: platform === 'GEZIDA' ? 'humanize' : mode, platform }
}

export function selectDocumentMode(mode, platform) {
  return { mode, platform: mode !== 'humanize' && platform === 'GEZIDA' ? 'GENERAL' : platform }
}
