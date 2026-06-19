import * as THREE from 'three'
import { addBox, addCylinder, addLine, createMechanicalPrimitive, semanticCategory } from './MechanicalPrimitiveLibrary.js'
import { createNonStandardShape, nonStandardCategory } from './NonStandardShapeGenerator.js'
import { addConstraintGuides, applyTransform, buildAssemblyTransforms } from './AssemblyConstraintVisualizer.js'

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
  const scale = Math.max(lengthMm / 3.4, widthMm / 1.9, heightMm / 1.1, 1)
  return {
    length: Math.max(lengthMm / scale, 2.2),
    width: Math.max(widthMm / scale, 1.25),
    height: Math.max(heightMm / scale, 0.8)
  }
}

function canvasLabel(text, colors = {}) {
  const canvas = document.createElement('canvas')
  canvas.width = 384
  canvas.height = 96
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = colors.bg || 'rgba(15,23,42,.78)'
  if (ctx.roundRect) {
    ctx.beginPath()
    ctx.roundRect(4, 10, 376, 70, 18)
    ctx.fill()
  } else {
    ctx.fillRect(4, 10, 376, 70)
  }
  ctx.strokeStyle = colors.border || 'rgba(96,165,250,.75)'
  ctx.lineWidth = 3
  if (ctx.roundRect) {
    ctx.beginPath()
    ctx.roundRect(4, 10, 376, 70, 18)
    ctx.stroke()
  }
  ctx.fillStyle = colors.text || '#e0f2fe'
  ctx.font = 'bold 30px Microsoft YaHei, Arial'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 192, 46)
  return new THREE.CanvasTexture(canvas)
}

function addLabel(group, text, position, scale = 0.52) {
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: canvasLabel(text), transparent: true }))
  sprite.name = `结构标注-${text}`
  sprite.position.set(position.x, position.y, position.z)
  sprite.scale.set(scale * 1.9, scale * 0.48, 1)
  group.add(sprite)
  return sprite
}

function addGroundShadow(group, dims) {
  const plate = new THREE.Mesh(
    new THREE.PlaneGeometry(dims.length * 1.28, dims.width * 1.42),
    new THREE.MeshStandardMaterial({ color: 0x0f172a, roughness: 0.8, metalness: 0.08, transparent: true, opacity: 0.42 })
  )
  plate.rotation.x = -Math.PI / 2
  plate.position.y = -dims.height * 0.5
  plate.name = '装配基准平面'
  plate.receiveShadow = true
  group.add(plate)
}

function drawRobotFallback(group, dims) {
  const parts = [
    { name: '机架', partType: 'non_standard', size: { x: dims.length * 0.72, y: dims.height * 0.2, z: dims.width * 0.58 }, position: { x: 0, y: 0, z: 0 } },
    { name: '左履带机构', partType: 'standard', category: 'track', size: { x: dims.length * 0.78, y: dims.height * 0.28, z: dims.width * 0.16 }, position: { x: 0, y: -dims.height * 0.16, z: -dims.width * 0.44 } },
    { name: '右履带机构', partType: 'standard', category: 'track', size: { x: dims.length * 0.78, y: dims.height * 0.28, z: dims.width * 0.16 }, position: { x: 0, y: -dims.height * 0.16, z: dims.width * 0.44 } },
    { name: '磁吸附模块前', partType: 'non_standard', size: { x: dims.length * 0.24, y: dims.height * 0.12, z: dims.width * 0.22 }, position: { x: -dims.length * 0.22, y: -dims.height * 0.36, z: 0 } },
    { name: '磁吸附模块后', partType: 'non_standard', size: { x: dims.length * 0.24, y: dims.height * 0.12, z: dims.width * 0.22 }, position: { x: dims.length * 0.22, y: -dims.height * 0.36, z: 0 } },
    { name: '圆盘清扫刷', partType: 'non_standard', size: { x: dims.length * 0.16, y: dims.height * 0.34, z: dims.height * 0.34 }, position: { x: -dims.length * 0.52, y: -dims.height * 0.02, z: 0 } },
    { name: '检测传感器安装架', partType: 'non_standard', size: { x: dims.length * 0.28, y: dims.height * 0.28, z: dims.width * 0.28 }, position: { x: -dims.length * 0.38, y: dims.height * 0.22, z: 0 } },
    { name: '驱动电机', partType: 'standard', category: 'motor', size: { x: dims.length * 0.18, y: dims.height * 0.22, z: dims.height * 0.22 }, position: { x: dims.length * 0.28, y: dims.height * 0.05, z: -dims.width * 0.23 } },
    { name: '减速器', partType: 'standard', category: 'reducer', size: { x: dims.length * 0.18, y: dims.height * 0.22, z: dims.width * 0.2 }, position: { x: dims.length * 0.14, y: dims.height * 0.03, z: -dims.width * 0.23 } },
    { name: '防护外壳', partType: 'non_standard', size: { x: dims.length * 0.48, y: dims.height * 0.26, z: dims.width * 0.45 }, position: { x: dims.length * 0.06, y: dims.height * 0.23, z: 0 } }
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
  if (isStandardPart(part)) return createMechanicalPrimitive(part, size)
  const nonStandard = nonStandardCategory(part)
  if (nonStandard !== 'generic') return createNonStandardShape(part, size)
  if (semanticCategory(part)) return createMechanicalPrimitive(part, size)
  return createNonStandardShape(part, size)
}

function buildFromAssembly(group, project, dims) {
  const transforms = buildAssemblyTransforms(project, dims)
  if (!transforms.length) return false

  transforms.forEach(transform => {
    const shape = createPartShape(transform.part, transform.size)
    applyTransform(shape, transform)
    group.add(shape)
  })
  return true
}

function addSystemLabels(group, project, dims) {
  const text = `${project.projectTitle || ''}${project.title || ''}${project.equipmentName || ''}`
  const isCrawlerRobot = /爬壁|履带|磁吸|油罐|机器人/.test(text)
  if (isCrawlerRobot) {
    addLabel(group, '履带系统', { x: 0, y: -dims.height * 0.02, z: -dims.width * 0.72 })
    addLabel(group, '驱动系统', { x: dims.length * 0.34, y: dims.height * 0.34, z: -dims.width * 0.36 }, 0.46)
    addLabel(group, '机架系统', { x: 0, y: dims.height * 0.48, z: 0 }, 0.46)
    addLabel(group, '磁吸系统', { x: 0, y: -dims.height * 0.55, z: 0 }, 0.46)
    addLabel(group, '清扫系统', { x: -dims.length * 0.66, y: dims.height * 0.12, z: 0 }, 0.46)
    addLabel(group, '检测系统', { x: -dims.length * 0.4, y: dims.height * 0.52, z: dims.width * 0.26 }, 0.46)
  }
}

function addReadableDimensionHints(group, dims) {
  addLine(group, '整机长度参考线', [[-dims.length * 0.55, -dims.height * 0.48, -dims.width * 0.66], [dims.length * 0.55, -dims.height * 0.48, -dims.width * 0.66]], 0x93c5fd)
  addLine(group, '整机宽度参考线', [[dims.length * 0.58, -dims.height * 0.48, -dims.width * 0.5], [dims.length * 0.58, -dims.height * 0.48, dims.width * 0.5]], 0x93c5fd)
  addCylinder(group, '前端清扫刷安装轴', dims.height * 0.035, dims.width * 0.28, [-dims.length * 0.52, -dims.height * 0.02, 0], 0xe5e7eb, 'z')
}

function isCrawlerRobotProject(project = {}) {
  const text = `${project.projectTitle || ''}${project.title || ''}${project.equipmentName || ''}${project.designType || ''}${JSON.stringify(project.structureTree || '')}`
  return /爬壁|履带|磁吸|油罐|机器人|清扫|检测/.test(text)
}

function buildGenericMechanicalAssembly(group, dims) {
  addBox(group, '通用机架', [dims.length * 0.72, dims.height * 0.18, dims.width * 0.48], [0, 0, 0], 0x334155)
  const motor = createMechanicalPrimitive({ name: '驱动电机', category: 'motor', partType: 'standard' }, { x: dims.length * 0.2, y: dims.height * 0.24, z: dims.height * 0.24 })
  motor.position.set(dims.length * 0.2, dims.height * 0.14, -dims.width * 0.22)
  group.add(motor)
  const reducer = createMechanicalPrimitive({ name: '减速器', category: 'reducer', partType: 'standard' }, { x: dims.length * 0.22, y: dims.height * 0.24, z: dims.width * 0.18 })
  reducer.position.set(0, dims.height * 0.1, -dims.width * 0.22)
  group.add(reducer)
  const coupling = createMechanicalPrimitive({ name: '联轴器', category: 'coupling', partType: 'standard' }, { x: dims.length * 0.16, y: dims.height * 0.18, z: dims.height * 0.18 })
  coupling.position.set(dims.length * 0.1, dims.height * 0.1, -dims.width * 0.22)
  group.add(coupling)
}

export function buildParametricMechanicalModel(project = {}) {
  const group = new THREE.Group()
  group.name = '机械设备装配模型'
  const dims = sceneDims(project)
  addGroundShadow(group, dims)
  addConstraintGuides(group, dims)

  const builtFromAssembly = buildFromAssembly(group, project, dims)
  if (!builtFromAssembly) {
    if (isCrawlerRobotProject(project)) drawRobotFallback(group, dims)
    else buildGenericMechanicalAssembly(group, dims)
  }

  addReadableDimensionHints(group, dims)
  addSystemLabels(group, project, dims)
  group.userData = {
    source: builtFromAssembly ? 'assembly_tree_with_constraints' : 'mechanical_fallback',
    hasScatteredLayout: false,
    usesMechanicalPrimitiveLibrary: true
  }
  return group
}
