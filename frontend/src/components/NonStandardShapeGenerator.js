import * as THREE from 'three'
import { addBox, addCylinder, addLine, addTorus, material } from './MechanicalPrimitiveLibrary.js'

function textOf(part = {}) {
  return `${part.partName || ''} ${part.name || ''} ${part.category || ''} ${part.type || ''}`.toLowerCase()
}

function addHolePattern(group, prefix, size, y) {
  const xs = [-0.32, 0, 0.32].map(v => v * size.x)
  const zs = [-0.32, 0.32].map(v => v * size.z)
  xs.forEach((x, ix) => {
    zs.forEach((z, iz) => addCylinder(group, `${prefix}安装孔${ix + 1}-${iz + 1}`, 0.025, 0.014, [x, y, z], 0x0f172a, 'z', 24))
  })
}

function createFrame(size) {
  const group = new THREE.Group()
  addBox(group, '左侧板', [size.x, size.y * 0.26, 0.05], [0, 0, -size.z * 0.44], 0x334155)
  addBox(group, '右侧板', [size.x, size.y * 0.26, 0.05], [0, 0, size.z * 0.44], 0x334155)
  addBox(group, '前横梁', [0.08, size.y * 0.22, size.z * 0.9], [-size.x * 0.42, 0, 0], 0x475569)
  addBox(group, '后横梁', [0.08, size.y * 0.22, size.z * 0.9], [size.x * 0.42, 0, 0], 0x475569)
  addBox(group, '中间横梁', [0.08, size.y * 0.18, size.z * 0.76], [0, 0, 0], 0x64748b)
  addBox(group, '上安装板', [size.x * 0.55, 0.035, size.z * 0.5], [0, size.y * 0.2, 0], 0x64748b)
  ;[-0.28, 0.28].forEach((x, i) => addBox(group, `斜向加强筋${i + 1}`, [size.x * 0.62, 0.035, 0.035], [x * size.x, size.y * 0.05, 0], 0x94a3b8))
  addHolePattern(group, '机架', size, size.y * 0.23)
  return group
}

function createShell(size) {
  const group = new THREE.Group()
  addBox(group, '防护外壳罩体', [size.x, size.y * 0.62, size.z], [0, 0, 0], 0x0ea5e9, 0.72)
  addBox(group, '检修盖板', [size.x * 0.36, 0.03, size.z * 0.46], [size.x * 0.08, size.y * 0.34, 0], 0x38bdf8, 0.86)
  addBox(group, '前端折边', [0.035, size.y * 0.54, size.z], [-size.x * 0.5, 0, 0], 0x0284c7, 0.82)
  addBox(group, '后端折边', [0.035, size.y * 0.54, size.z], [size.x * 0.5, 0, 0], 0x0284c7, 0.82)
  addHolePattern(group, '外壳', size, size.y * 0.36)
  return group
}

function createMountingBracket(size) {
  const group = new THREE.Group()
  addBox(group, '安装立板', [size.x * 0.78, size.y * 0.76, 0.045], [0, 0, -size.z * 0.2], 0x64748b)
  addBox(group, '安装底板', [size.x * 0.82, 0.045, size.z * 0.58], [0, -size.y * 0.38, size.z * 0.08], 0x475569)
  addBox(group, '左加强肋', [0.045, size.y * 0.56, size.z * 0.38], [-size.x * 0.28, -size.y * 0.1, 0], 0x94a3b8)
  addBox(group, '右加强肋', [0.045, size.y * 0.56, size.z * 0.38], [size.x * 0.28, -size.y * 0.1, 0], 0x94a3b8)
  addCylinder(group, '调节长孔左', 0.026, 0.018, [-size.x * 0.18, size.y * 0.06, -size.z * 0.225], 0x0f172a, 'z', 24)
  addCylinder(group, '调节长孔右', 0.026, 0.018, [size.x * 0.18, size.y * 0.06, -size.z * 0.225], 0x0f172a, 'z', 24)
  return group
}

function createMagnetModule(size) {
  const group = new THREE.Group()
  addBox(group, '磁吸安装板', [size.x, size.y * 0.18, size.z], [0, 0, 0], 0x475569)
  for (let i = 0; i < 4; i += 1) {
    addBox(group, `永磁体${i + 1}`, [size.x * 0.18, size.y * 0.22, size.z * 0.62], [(-0.36 + i * 0.24) * size.x, -size.y * 0.18, 0], 0x7c3aed)
  }
  addBox(group, '防护盖', [size.x * 0.92, size.y * 0.08, size.z * 0.86], [0, size.y * 0.18, 0], 0xa78bfa, 0.82)
  addHolePattern(group, '磁吸座', size, size.y * 0.25)
  return group
}

function createDetectionModule(size) {
  const group = new THREE.Group()
  addBox(group, '传感器横梁', [size.x, size.y * 0.12, size.z * 0.16], [0, size.y * 0.24, 0], 0xf59e0b)
  addBox(group, '左导轨', [size.x * 0.85, size.y * 0.08, 0.025], [0, size.y * 0.06, -size.z * 0.22], 0xeab308)
  addBox(group, '右导轨', [size.x * 0.85, size.y * 0.08, 0.025], [0, size.y * 0.06, size.z * 0.22], 0xeab308)
  addBox(group, '滑块', [size.x * 0.18, size.y * 0.18, size.z * 0.36], [0, size.y * 0.1, 0], 0xfbbf24)
  addBox(group, '检测探头', [size.x * 0.16, size.y * 0.22, size.z * 0.16], [-size.x * 0.44, -size.y * 0.12, 0], 0x111827)
  addLine(group, '检测支架中心线', [[-size.x * 0.5, size.y * 0.02, 0], [size.x * 0.5, size.y * 0.02, 0]], 0xfde68a)
  return group
}

function createBrushDisk(size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.42
  addCylinder(group, '圆盘刷盘体', r, size.x * 0.14, [0, 0, 0], 0xf97316, 'x', 72)
  addCylinder(group, '中心轴孔', r * 0.18, size.x * 0.18, [0, 0, 0], 0x0f172a, 'x', 36)
  for (let i = 0; i < 32; i += 1) {
    const a = (Math.PI * 2 * i) / 32
    const bristle = addBox(group, `刷毛${i + 1}`, [size.x * 0.04, 0.012, r * 0.34], [0, Math.cos(a) * r * 1.08, Math.sin(a) * r * 1.08], 0xfb923c)
    bristle.rotation.x = -a
  }
  addBox(group, '刷盘电机座', [size.x * 0.42, size.y * 0.28, size.z * 0.42], [size.x * 0.28, 0, 0], 0x64748b)
  addTorus(group, '刷盘外缘', r, r * 0.035, [0, 0, 0], 0xfdba74, 'x')
  return group
}

function createGenericNonStandard(size) {
  const group = new THREE.Group()
  addBox(group, '非标主体板', [size.x, size.y * 0.28, size.z], [0, 0, 0], 0x64748b)
  addBox(group, '加强筋', [size.x * 0.74, size.y * 0.38, 0.035], [0, size.y * 0.22, 0], 0x94a3b8)
  addHolePattern(group, '非标件', size, size.y * 0.2)
  return group
}

export function nonStandardCategory(part = {}) {
  const text = textOf(part)
  if (/机架|车架|frame/.test(text)) return 'frame'
  if (/外壳|防护|罩/.test(text)) return 'shell'
  if (/磁|吸附/.test(text)) return 'magnetModule'
  if (/检测|传感器|sensor|滑轨|导轨/.test(text)) return 'detectionModule'
  if (/刷|清扫|brush/.test(text)) return 'brushDisk'
  if (/支架|安装架|快拆/.test(text)) return 'mountingBracket'
  return 'generic'
}

export function createNonStandardShape(part = {}, size = { x: 0.6, y: 0.3, z: 0.3 }) {
  const normalizedSize = {
    x: Math.max(Number(size.x) || 0.6, 0.08),
    y: Math.max(Number(size.y) || 0.3, 0.08),
    z: Math.max(Number(size.z) || 0.3, 0.08)
  }
  const creators = {
    frame: createFrame,
    shell: createShell,
    mountingBracket: createMountingBracket,
    magnetModule: createMagnetModule,
    detectionModule: createDetectionModule,
    brushDisk: createBrushDisk,
    generic: createGenericNonStandard
  }
  const category = nonStandardCategory(part)
  const group = (creators[category] || createGenericNonStandard)(normalizedSize)
  group.name = part.partName || part.name || '非标结构件'
  group.userData = { ...part, primitiveCategory: category }
  return group
}
