import * as THREE from 'three'
import { addBox, addCylinder, addLine, createMechanicalPrimitive, semanticCategory } from './MechanicalPrimitiveLibrary.js'
import { createNonStandardShape, nonStandardCategory } from './NonStandardShapeGenerator.js'
import { drawParametricStandardPartGeometry, hasParametricGeometry } from './ParametricStandardPartGeometryGenerator.js'
import { addConstraintGuides, applyTransform, buildAssemblyTransforms } from './AssemblyConstraintVisualizer.js'
import { evaluateModelQuality, modelDeviceType } from './ModelQualityGate.js'
import { repairModel } from './ModelRepairAgent.js'

function readParam(project, patterns, fallback) {
  const params = []
  ;[project.parameters, project.explicitParameters, project.derivedParameters, project.suggestedParameters].forEach(list => {
    if (Array.isArray(list)) params.push(...list)
  })
  const found = params.find(p => patterns.some(pattern => pattern.test(`${p.name || ''}${p.label || ''}`)))
  const value = Number(found?.value)
  return Number.isFinite(value) ? value : fallback
}

function sceneDims(project = {}) {
  const lengthMm = readParam(project, [/总长/, /整机长/, /长度/], Number(project.totalLength) || Number(project.length) || 800)
  const widthMm = readParam(project, [/总宽/, /整机宽/, /宽度/], Number(project.totalWidth) || Number(project.width) || 600)
  const heightMm = readParam(project, [/总高/, /整机高/, /高度/], Number(project.totalHeight) || Number(project.height) || 300)
  const scale = Math.max(lengthMm / 3.0, widthMm / 1.55, heightMm / 0.85, 1)
  return {
    length: Math.max(lengthMm / scale, 2.2),
    width: Math.max(widthMm / scale, 1.15),
    height: Math.max(heightMm / scale, 0.7)
  }
}

function addGroundShadow(group, dims) {
  const plate = new THREE.Mesh(
    new THREE.PlaneGeometry(dims.length * 1.2, dims.width * 1.35),
    new THREE.MeshStandardMaterial({ color: 0x0f172a, roughness: 0.8, metalness: 0.08, transparent: true, opacity: 0.36 })
  )
  plate.rotation.x = -Math.PI / 2
  plate.position.y = -dims.height * 0.48
  plate.name = '装配基准平面'
  plate.receiveShadow = true
  group.add(plate)
}

function drawRobotFallback(group, dims) {
  const parts = [
    { name: '机架', partType: 'non_standard', size: { x: dims.length * 0.66, y: dims.height * 0.16, z: dims.width * 0.5 }, position: { x: 0, y: 0, z: 0 } },
    { name: '左履带机构', partType: 'standard', category: 'track', size: { x: dims.length * 0.72, y: dims.height * 0.24, z: dims.width * 0.14 }, position: { x: 0, y: -dims.height * 0.18, z: -dims.width * 0.38 } },
    { name: '右履带机构', partType: 'standard', category: 'track', size: { x: dims.length * 0.72, y: dims.height * 0.24, z: dims.width * 0.14 }, position: { x: 0, y: -dims.height * 0.18, z: dims.width * 0.38 } },
    { name: '磁吸附模块前', partType: 'non_standard', size: { x: dims.length * 0.22, y: dims.height * 0.08, z: dims.width * 0.16 }, position: { x: -dims.length * 0.18, y: -dims.height * 0.34, z: 0 } },
    { name: '磁吸附模块后', partType: 'non_standard', size: { x: dims.length * 0.22, y: dims.height * 0.08, z: dims.width * 0.16 }, position: { x: dims.length * 0.18, y: -dims.height * 0.34, z: 0 } },
    { name: '圆盘清扫刷', partType: 'non_standard', size: { x: dims.length * 0.14, y: dims.height * 0.28, z: dims.height * 0.28 }, position: { x: -dims.length * 0.42, y: -dims.height * 0.04, z: 0 } },
    { name: '检测传感器安装架', partType: 'non_standard', size: { x: dims.length * 0.22, y: dims.height * 0.2, z: dims.width * 0.18 }, position: { x: -dims.length * 0.32, y: dims.height * 0.18, z: 0 } },
    { name: '驱动电机', partType: 'standard', category: 'motor', size: { x: dims.length * 0.16, y: dims.height * 0.16, z: dims.height * 0.16 }, position: { x: dims.length * 0.24, y: dims.height * 0.02, z: -dims.width * 0.22 } },
    { name: '减速器', partType: 'standard', category: 'reducer', size: { x: dims.length * 0.15, y: dims.height * 0.18, z: dims.width * 0.16 }, position: { x: dims.length * 0.12, y: dims.height * 0.02, z: -dims.width * 0.22 } },
    { name: '防护外壳', partType: 'non_standard', size: { x: dims.length * 0.42, y: dims.height * 0.2, z: dims.width * 0.36 }, position: { x: 0, y: dims.height * 0.2, z: 0 } }
  ]
  parts.forEach(part => {
    const shape = part.partType === 'standard' ? createMechanicalPrimitive(part, part.size) : createNonStandardShape(part, part.size)
    shape.position.set(part.position.x, part.position.y, part.position.z)
    group.add(shape)
  })
}

function isStandardPart(part = {}) {
  if (`${part.partType || ''}`.toLowerCase() === 'standard') return true
  if (`${part.source || ''}`.includes('standard')) return true
  return Boolean(semanticCategory(part))
}

function createPartShape(part, size) {
  if (hasParametricGeometry(part) || Array.isArray(part.featureTree) && part.featureTree.length > 2) {
    const group = new THREE.Group()
    group.name = part.partName || part.name || '特征参数化零件'
    const created = drawParametricStandardPartGeometry(group, part, [size.x, size.y, size.z], [0, 0, 0])
    group.userData = {
      ...part,
      modelingMethod: 'feature_based_parametric',
      featureTree: part.featureTree || [],
      parametricGeometry: true,
      featureMeshCount: countMeshes(group)
    }
    if (created || group.children.length) return group
  }
  if (isStandardPart(part)) return createMechanicalPrimitive(part, size)
  const nonStandard = nonStandardCategory(part)
  if (nonStandard !== 'generic') return createNonStandardShape(part, size)
  if (semanticCategory(part)) return createMechanicalPrimitive(part, size)
  return createNonStandardShape(part, size)
}

function countMeshes(group) {
  let count = 0
  group.traverse(item => {
    if (item.isMesh) count += 1
  })
  return count
}

function inputComponentCount(project = {}) {
  if (Array.isArray(project.components) && project.components.length) return project.components.length
  if (Array.isArray(project.resolvedParts) && project.resolvedParts.length) return project.resolvedParts.length
  return 0
}

function buildFromAssembly(group, project, dims) {
  const transforms = buildAssemblyTransforms(project, dims)
  if (!transforms.length) return { built: false, renderableComponents: 0, featureBasedParts: 0 }

  let featureBasedParts = 0
  transforms.forEach(transform => {
    const shape = createPartShape(transform.part, transform.size)
    if (shape.userData?.parametricGeometry || Array.isArray(transform.part.featureTree) && transform.part.featureTree.length > 2) {
      featureBasedParts += 1
    }
    applyTransform(shape, transform)
    group.add(shape)
  })
  return { built: true, renderableComponents: transforms.length, featureBasedParts }
}

function addDimensionHints(group, dims) {
  addLine(group, '整机长度参考线', [[-dims.length * 0.48, -dims.height * 0.46, -dims.width * 0.58], [dims.length * 0.48, -dims.height * 0.46, -dims.width * 0.58]], 0x93c5fd)
  addLine(group, '整机宽度参考线', [[dims.length * 0.52, -dims.height * 0.46, -dims.width * 0.42], [dims.length * 0.52, -dims.height * 0.46, dims.width * 0.42]], 0x93c5fd)
  addCylinder(group, '前端清扫刷安装轴', dims.height * 0.03, dims.width * 0.22, [-dims.length * 0.42, -dims.height * 0.04, 0], 0xe5e7eb, 'z')
}

function isCrawlerRobotProject(project = {}) {
  const text = `${project.projectTitle || ''}${project.title || ''}${project.equipmentName || ''}${project.designType || ''}${JSON.stringify(project.structureTree || '')}`
  return /爬壁|履带|磁吸|油罐|机器人|清扫|检测/.test(text)
}

function isSettlingChamberProject(project = {}) {
  return modelDeviceType(project) === 'settling_chamber'
}

function buildGenericMechanicalAssembly(group, dims) {
  addBox(group, '机架', [dims.length * 0.66, dims.height * 0.16, dims.width * 0.42], [0, 0, 0], 0x334155)
  const motor = createMechanicalPrimitive({ name: '驱动电机', category: 'motor', partType: 'standard' }, { x: dims.length * 0.18, y: dims.height * 0.18, z: dims.height * 0.18 })
  motor.position.set(dims.length * 0.18, dims.height * 0.12, -dims.width * 0.18)
  group.add(motor)
  const reducer = createMechanicalPrimitive({ name: '减速器', category: 'reducer', partType: 'standard' }, { x: dims.length * 0.2, y: dims.height * 0.2, z: dims.width * 0.16 })
  reducer.position.set(0, dims.height * 0.1, -dims.width * 0.18)
  group.add(reducer)
}

function buildRawMechanicalModel(project = {}) {
  const group = new THREE.Group()
  group.name = '机械设备装配模型'
  const dims = sceneDims(project)
  addGroundShadow(group, dims)
  addConstraintGuides(group, dims)

  const assemblyComponents = inputComponentCount(project)
  const assemblyResult = buildFromAssembly(group, project, dims)
  if (!assemblyResult.built) {
    if (isCrawlerRobotProject(project)) drawRobotFallback(group, dims)
    else if (isSettlingChamberProject(project)) {
      const repaired = repairModel(project, group, { deviceType: 'settling_chamber' })
      group.add(repaired)
      assemblyResult.featureBasedParts = Math.max(assemblyResult.featureBasedParts || 0, repaired.userData?.featureBasedParts || 0)
    }
    else buildGenericMechanicalAssembly(group, dims)
  }

  addDimensionHints(group, dims)
  let meshCount = countMeshes(group)
  if (meshCount <= 1) {
    console.error('[DropAI 3D] mesh count is 0, restoring visible fallback model', {
      assemblyComponents,
      renderableComponents: assemblyResult.renderableComponents,
      meshCount
    })
    drawRobotFallback(group, dims)
    meshCount = countMeshes(group)
  }
  console.info('[DropAI 3D] render diagnostics', {
    assemblyComponents,
    renderableComponents: assemblyResult.renderableComponents,
    featureBasedParts: assemblyResult.featureBasedParts || 0,
    meshCount,
    source: assemblyResult.built ? 'AssemblyTree/Components' : 'FallbackModel'
  })
  group.userData = {
    source: assemblyResult.built ? 'assembly_tree_with_constraints' : 'mechanical_fallback',
    assemblyComponents,
    renderableComponents: assemblyResult.renderableComponents,
    meshCount,
    hasScatteredLayout: false,
    usesMechanicalPrimitiveLibrary: true,
    modelingMethod: 'feature_based_parametric',
    featureBasedParts: assemblyResult.featureBasedParts || 0
  }
  return group
}

function errorPlaceholder(quality) {
  const group = new THREE.Group()
  group.name = '模型质量未通过占位'
  group.userData = {
    source: 'quality_failed_placeholder',
    quality,
    qualityFailed: true,
    code: 'MODEL_QUALITY_FAILED'
  }
  return group
}

export function buildParametricMechanicalModel(project = {}) {
  let group = buildRawMechanicalModel(project)
  let quality = evaluateModelQuality(project, group, group.userData)
  let repaired = false
  let attempts = 0
  while (!quality.success && attempts < 2) {
    attempts += 1
    console.warn('[DropAI 3D] ModelQualityGate failed; invoking ModelRepairAgent', quality)
    group = repairModel(project, group, quality)
    repaired = true
    quality = evaluateModelQuality(project, group, group.userData)
  }
  if (!quality.success) {
    console.error('[DropAI 3D] MODEL_QUALITY_FAILED', quality)
    return errorPlaceholder(quality)
  }
  group.userData = {
    ...group.userData,
    quality,
    qualityPassed: true,
    repairAttempts: attempts,
    repairedByModelRepairAgent: repaired
  }
  return group
}
