import * as THREE from 'three'

const KW = {
  settling: /\u91cd\u529b\u6c89\u964d|\u6c89\u964d\u5ba4|\u9664\u5c18|settling/i,
  crawler: /\u722c\u58c1|\u5c65\u5e26|\u6cb9\u7f50|\u673a\u5668\u4eba|\u5438\u9644|crawler|track/i,
  conveyor: /\u8f93\u9001\u673a|\u8f93\u9001\u5e26|\u6eda\u7b52|conveyor/i,
  manipulator: /\u673a\u68b0\u624b|\u673a\u68b0\u81c2|\u5939\u722a|manipulator/i
}

function modelText(project = {}) {
  return [
    project.projectTitle,
    project.title,
    project.equipmentName,
    project.designType,
    project.equipmentType,
    project.mainStructures,
    project.structureTree,
    project.components,
    project.resolvedParts
  ].map(item => {
    try {
      return typeof item === 'string' ? item : JSON.stringify(item || '')
    } catch {
      return ''
    }
  }).join(' ')
}

export function modelDeviceType(project = {}) {
  const text = modelText(project)
  if (KW.settling.test(text)) return 'settling_chamber'
  if (KW.crawler.test(text)) return 'crawler_robot'
  if (KW.conveyor.test(text)) return 'conveyor'
  if (KW.manipulator.test(text)) return 'manipulator'
  return 'generic'
}

const CORE_RULES = {
  settling_chamber: [
    ['\u7bb1\u4f53\u4e3b\u4f53', /\u7bb1\u4f53\u4e3b\u4f53|\u6c89\u964d\u5ba4\u7bb1\u4f53|\u4e3b\u7bb1\u4f53|chamber body|shell body/i],
    ['\u8fdb\u6c14\u53e3', /\u8fdb\u6c14\u53e3|\u8fdb\u98ce\u53e3|\u5165\u53e3|inlet/i],
    ['\u51fa\u6c14\u53e3', /\u51fa\u6c14\u53e3|\u51fa\u98ce\u53e3|\u51fa\u53e3|outlet/i],
    ['\u6269\u6563\u6bb5', /\u6269\u6563\u6bb5|\u6269\u6563|diffuser/i],
    ['\u7070\u6597', /\u7070\u6597|\u96c6\u7070\u6597|hopper/i],
    ['\u5378\u7070\u53e3', /\u5378\u7070\u53e3|\u6392\u7070\u53e3|ash outlet|discharge/i],
    ['\u652f\u6491\u67b6', /\u652f\u6491\u67b6|\u652f\u6491\u817f|\u652f\u817f|support/i],
    ['\u68c0\u4fee\u95e8', /\u68c0\u4fee\u95e8|access door|inspection/i],
    ['\u52a0\u5f3a\u7b4b', /\u52a0\u5f3a\u7b4b|\u52a0\u5f3a\u808b|rib/i]
  ],
  crawler_robot: [
    ['\u673a\u67b6', /\u673a\u67b6|\u8f66\u67b6|frame/i],
    ['\u5de6\u5c65\u5e26', /\u5de6.*\u5c65\u5e26|left.*track/i],
    ['\u53f3\u5c65\u5e26', /\u53f3.*\u5c65\u5e26|right.*track/i],
    ['\u9a71\u52a8\u8f6e', /\u9a71\u52a8\u8f6e|drive wheel/i],
    ['\u4ece\u52a8\u8f6e', /\u4ece\u52a8\u8f6e|idler|follower/i],
    ['\u6e05\u626b\u5237', /\u6e05\u626b|\u5237|brush/i],
    ['\u68c0\u6d4b\u67b6', /\u68c0\u6d4b|\u4f20\u611f|sensor/i],
    ['\u5916\u58f3', /\u5916\u58f3|\u9632\u62a4|cover/i]
  ]
}

function collectObjects(group) {
  const objects = []
  group.traverse(item => {
    if ((item.isMesh || item.isGroup) && item.name && !/grid|shadow|reference|ground|\u57fa\u51c6|\u53c2\u8003\u7ebf|\u5e73\u9762/i.test(item.name)) {
      objects.push(item)
    }
  })
  return objects
}

function nameText(objects) {
  return objects.map(item => `${item.name || ''} ${JSON.stringify(item.userData || {})}`).join(' ')
}

function countMeshes(group) {
  let count = 0
  group.traverse(item => {
    if (item.isMesh) count += 1
  })
  return count
}

function checkFloating(group) {
  const wholeBox = new THREE.Box3().setFromObject(group)
  if (wholeBox.isEmpty()) return { floatingParts: [], scatteredParts: [] }
  const wholeSize = wholeBox.getSize(new THREE.Vector3())
  const center = wholeBox.getCenter(new THREE.Vector3())
  const maxSize = Math.max(wholeSize.x, wholeSize.y, wholeSize.z, 0.1)
  const floatingParts = []
  const scatteredParts = []
  for (const child of group.children) {
    if (!child.visible || !child.name || /grid|shadow|reference|ground|\u57fa\u51c6|\u53c2\u8003\u7ebf|\u5e73\u9762/i.test(child.name)) continue
    const box = new THREE.Box3().setFromObject(child)
    if (box.isEmpty()) continue
    const childCenter = box.getCenter(new THREE.Vector3())
    const dist = childCenter.distanceTo(center)
    if (dist > maxSize * 1.5) scatteredParts.push(child.name)
    const attached = child.userData?.mountTo || child.userData?.assemblyConstraint || child.userData?.repairedAttachment
    if (!attached && dist > maxSize * 0.72) floatingParts.push(child.name)
  }
  return { floatingParts, scatteredParts }
}

function checkSymmetry(deviceType, text) {
  const missing = []
  if (deviceType === 'crawler_robot') {
    if (!/\u5de6.*\u5c65\u5e26|left.*track/i.test(text)) missing.push('\u5de6\u5c65\u5e26')
    if (!/\u53f3.*\u5c65\u5e26|right.*track/i.test(text)) missing.push('\u53f3\u5c65\u5e26')
  }
  if (deviceType === 'settling_chamber') {
    const supportCount = (text.match(/\u652f\u6491\u817f|support leg|\u652f\u817f/gi) || []).length
    const ribCount = (text.match(/\u52a0\u5f3a\u7b4b|\u52a0\u5f3a\u808b|rib/gi) || []).length
    if (supportCount > 0 && supportCount < 4) missing.push('\u56db\u89d2\u652f\u6491\u817f')
    if (ribCount > 0 && ribCount < 4) missing.push('\u6210\u7ec4\u52a0\u5f3a\u7b4b')
    if (!/\u8fdb\u6c14\u53e3|inlet/i.test(text)) missing.push('\u8fdb\u6c14\u7aef\u7ed3\u6784')
    if (!/\u51fa\u6c14\u53e3|outlet/i.test(text)) missing.push('\u51fa\u6c14\u7aef\u7ed3\u6784')
  }
  return missing
}

export function evaluateModelQuality(project = {}, group, context = {}) {
  const deviceType = modelDeviceType(project)
  const objects = collectObjects(group)
  const text = nameText(objects)
  const required = CORE_RULES[deviceType] || []
  const missingParts = required.filter(([, pattern]) => !pattern.test(text)).map(([label]) => label)
  const presentCoreCount = Math.max(0, required.length - missingParts.length)
  const meshCount = countMeshes(group)
  const { floatingParts, scatteredParts } = checkFloating(group)
  const symmetryMissing = checkSymmetry(deviceType, text)
  const designDepth = `${project.designDepth || 'graduation'}`.toLowerCase()
  const graduation = !/engineering|\u5de5\u7a0b/.test(designDepth)
  const strictPartCount = graduation && required.length > 0 && deviceType !== 'generic'
  const partCountFailed = strictPartCount && meshCount < 30
  const coreCountFailed = graduation && required.length > 0 && presentCoreCount < Math.min(8, required.length)
  const featureBasedParts = Number(context.featureBasedParts || group.userData?.featureBasedParts || 0)
  const detailInsufficient = required.length > 0 && featureBasedParts < 2 && meshCount < 36

  const issues = []
  if (missingParts.length) issues.push('\u6838\u5fc3\u7ed3\u6784\u7f3a\u5931')
  if (symmetryMissing.length) issues.push('\u5bf9\u79f0\u7ed3\u6784\u4e0d\u5b8c\u6574')
  if (floatingParts.length || scatteredParts.length) issues.push('\u5b58\u5728\u60ac\u6d6e\u6216\u6563\u843d\u96f6\u4ef6')
  if (partCountFailed) issues.push('\u6bd5\u4e1a\u8bbe\u8ba1\u7248\u96f6\u4ef6\u6570\u91cf\u4e0d\u8db3')
  if (coreCountFailed) issues.push('\u5173\u952e\u7ed3\u6784\u6570\u91cf\u4e0d\u8db3')
  if (detailInsufficient) issues.push('\u6a21\u578b\u7ec6\u8282\u4e0d\u8db3\uff0c\u8fdb\u5165\u4e8c\u6b21\u7ec6\u5316\u751f\u6210')

  const penalties =
    missingParts.length * 12 +
    symmetryMissing.length * 8 +
    floatingParts.length * 10 +
    scatteredParts.length * 16 +
    (partCountFailed ? 15 : 0) +
    (coreCountFailed ? 12 : 0) +
    (detailInsufficient ? 10 : 0)
  const qualityScore = Math.max(0, Math.min(100, 100 - penalties))
  const success = issues.length === 0 && qualityScore >= 75
  return {
    success,
    code: success ? 'MODEL_QUALITY_PASSED' : 'MODEL_QUALITY_FAILED',
    message: success ? '\u6a21\u578b\u5b8c\u6574\u6027\u6821\u9a8c\u901a\u8fc7' : '\u6a21\u578b\u5b8c\u6574\u6027\u6821\u9a8c\u672a\u901a\u8fc7\uff0c\u6b63\u5728\u81ea\u52a8\u8865\u5168...',
    deviceType,
    missingParts,
    symmetryMissing,
    floatingParts: [...floatingParts, ...scatteredParts],
    qualityScore,
    partCount: meshCount,
    coreStructureCount: presentCoreCount,
    requiredCoreCount: required.length,
    issues
  }
}
