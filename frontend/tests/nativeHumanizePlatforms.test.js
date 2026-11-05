import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import {
  DAYA_PLATFORM,
  DEEP_HUMANIZE_TYPE,
  DOUBLE_PLATFORM_OPTIONS,
  NATIVE_HUMANIZE_PLATFORMS,
  documentPlatformChoices,
  resolveDocumentSubmission,
  resolveTextSubmission,
  textPlatformChoices
} from '../src/utils/nativeHumanizePlatforms.js'

const EXPECTED_ORDER = ['GENERAL', 'CNKI', 'WEIPU', 'GEZIDA', 'PAPERPASS', 'PAPERYY', 'ZHUQUE', 'WANFANG', 'TURNITIN']

test('native 降AI lists the backend platform codes and keeps 大雅 off that list', () => {
  assert.deepEqual(NATIVE_HUMANIZE_PLATFORMS.map(item => item.value), EXPECTED_ORDER)
  assert.equal(NATIVE_HUMANIZE_PLATFORMS.some(item => item.value === 'DAYA'), false)
  assert.equal(DAYA_PLATFORM.value, 'DAYA')
  assert.equal(DAYA_PLATFORM.route, 'external')
  assert.equal(NATIVE_HUMANIZE_PLATFORMS.find(item => item.value === 'TURNITIN').optionLabel, 'Turnitin（英文）')
})

test('platform codes, names, and depth split stay aligned with NativeHumanizeProfiles', () => {
  const java = readFileSync(new URL('../../backend/src/main/java/com/dropai/rewrite/service/impl/NativeHumanizeProfiles.java', import.meta.url), 'utf8')
  const kinds = parseKindSwitch(switchBody(java, 'public static Kind kind(String platform)'))
  const names = parseDisplaySwitch(switchBody(java, 'public static String displayName(String platform)'))

  assert.deepEqual(Object.keys(kinds).sort(), EXPECTED_ORDER.filter(code => code !== 'GENERAL').sort())
  for (const platform of NATIVE_HUMANIZE_PLATFORMS) {
    if (platform.value === 'GENERAL') {
      assert.equal(platform.label, '通用')
      assert.equal(platform.kind, 'GENERAL')
      continue
    }
    assert.equal(platform.kind, kinds[platform.value], platform.value)
    assert.equal(platform.label, names[platform.value], platform.value)
  }
  assert.match(java, /case "DAYA" -> GENERAL/)
  assert.equal(names.DAYA, undefined)
})

test('降AI document submit sends native codes, depth only where supported, and leaves 大雅 external', () => {
  assert.deepEqual(documentPlatformChoices('rewrite'), [])
  assert.deepEqual(documentPlatformChoices('humanize').map(item => item.value), [...EXPECTED_ORDER, 'DAYA'])
  assert.deepEqual(documentPlatformChoices('double').map(item => item.value), DOUBLE_PLATFORM_OPTIONS.map(item => item.value))
  assert.deepEqual(DOUBLE_PLATFORM_OPTIONS.map(item => item.value), ['GENERAL', 'DAYA'])

  assert.deepEqual(resolveDocumentSubmission({ mode: 'rewrite', platform: 'CNKI', intensity: 'deep' }), {
    route: 'native', mode: 'rewrite', platform: 'GENERAL'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'CNKI', intensity: 'ordinary' }), {
    route: 'native', mode: 'humanize', platform: 'CNKI'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'ZHUQUE', intensity: 'deep' }), {
    route: 'native', mode: DEEP_HUMANIZE_TYPE, platform: 'ZHUQUE'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'WEIPU', intensity: 'deep' }), {
    route: 'native', mode: DEEP_HUMANIZE_TYPE, platform: 'WEIPU'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'PAPERYY', intensity: 'ordinary' }), {
    route: 'native', mode: 'humanize', platform: 'PAPERYY'
  })
  for (const platform of ['GEZIDA', 'PAPERPASS', 'WANFANG', 'TURNITIN', 'GENERAL']) {
    assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform, intensity: 'deep' }), {
      route: 'native', mode: 'humanize', platform
    })
  }
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'DAYA', intensity: 'deep' }), {
    route: 'external', mode: 'humanize', platform: 'DAYA'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'double', platform: 'DAYA', intensity: 'deep' }), {
    route: 'external', mode: 'double', platform: 'DAYA'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'double', platform: 'CNKI', intensity: 'deep' }), {
    route: 'native', mode: 'double', platform: 'GENERAL'
  })
  assert.deepEqual(resolveDocumentSubmission({ mode: 'humanize', platform: 'NOT_A_PLATFORM', intensity: 'deep' }), {
    route: 'native', mode: 'humanize', platform: 'GENERAL'
  })
})

test('降AI page submits the resolved platform instead of a hardcoded general code', () => {
  const vue = readFileSync(new URL('../src/views/Rewrite/index.vue', import.meta.url), 'utf8')
  assert.match(vue, /uploadDocument\(selectedDocument\.value, submission\.mode, submission\.platform, documentPrecheck\.requestId\)/)
  assert.match(vue, /uploadExternalDocument\(selectedDocument\.value, submission\.mode, submission\.platform, documentPrecheck\.requestId\)/)
  assert.match(vue, /rewriteType: submission\.rewriteType/)
  assert.match(vue, /platform: submission\.platform/)
  assert.equal(vue.includes("platform: 'GENERAL'"), false)
  assert.match(vue, /optgroup label="大雅降AI"/)
  assert.match(vue, /optgroup label="原生降AI"/)
})

test('片段降AI sends the same platform field and does not invent a text 大雅 path', () => {
  assert.deepEqual(textPlatformChoices('humanize').map(item => item.value), EXPECTED_ORDER)
  assert.deepEqual(textPlatformChoices('rewrite'), [])
  assert.deepEqual(textPlatformChoices('double'), [])
  assert.deepEqual(resolveTextSubmission({ mode: 'rewrite', platform: 'CNKI', intensity: 'deep' }), {
    rewriteType: 'rewrite', platform: 'GENERAL'
  })
  assert.deepEqual(resolveTextSubmission({ mode: 'double', platform: 'DAYA', intensity: 'deep' }), {
    rewriteType: 'double', platform: 'GENERAL'
  })
  assert.deepEqual(resolveTextSubmission({ mode: 'humanize', platform: 'PAPERPASS', intensity: 'deep' }), {
    rewriteType: 'humanize', platform: 'PAPERPASS'
  })
  assert.deepEqual(resolveTextSubmission({ mode: 'humanize', platform: 'CNKI', intensity: 'deep' }), {
    rewriteType: DEEP_HUMANIZE_TYPE, platform: 'CNKI'
  })
  assert.deepEqual(resolveTextSubmission({ mode: 'humanize', platform: 'DAYA', intensity: 'ordinary' }), {
    rewriteType: 'humanize', platform: 'GENERAL'
  })
})

function switchBody(source, signature) {
  const start = source.indexOf(signature)
  assert.notEqual(start, -1, signature)
  const open = source.indexOf('{', start)
  let depth = 0
  for (let index = open; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) return source.slice(open, index + 1)
    }
  }
  throw new Error(`unclosed ${signature}`)
}

function parseKindSwitch(body) {
  const kinds = {}
  for (const match of body.matchAll(/case\s+((?:"[^"]+"\s*,\s*)*"[^"]+")\s*->\s*Kind\.([A-Z_]+);/g)) {
    const kind = match[2]
    for (const code of match[1].match(/"([^"]+)"/g).map(item => item.slice(1, -1))) {
      kinds[code] = kind
    }
  }
  return kinds
}

function parseDisplaySwitch(body) {
  const names = {}
  for (const match of body.matchAll(/case\s+"([^"]+)"\s*->\s*"([^"]+)";/g)) {
    names[match[1]] = match[2]
  }
  return names
}
