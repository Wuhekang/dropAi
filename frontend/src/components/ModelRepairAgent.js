import * as THREE from 'three'
import { addBox, addCylinder, addTorus } from './MechanicalPrimitiveLibrary.js'
import { modelDeviceType } from './ModelQualityGate.js'

function attach(object, mountTo = 'assembly-root') {
  object.userData = { ...object.userData, repairedAttachment: true, mountTo }
  return object
}

function addLeg(group, name, x, y, z, h) {
  attach(addBox(group, name, [0.08, h, 0.08], [x, y, z], 0x475569), 'chamber-body')
  attach(addBox(group, `${name}-base-plate`, [0.22, 0.035, 0.22], [x, y - h * 0.5 - 0.02, z], 0x334155), name)
  attach(addCylinder(group, `${name}-anchor-bolt-a`, 0.015, 0.04, [x - 0.06, y - h * 0.5 + 0.005, z - 0.06], 0xcbd5e1, 'y', 24), `${name}-base-plate`)
  attach(addCylinder(group, `${name}-anchor-bolt-b`, 0.015, 0.04, [x + 0.06, y - h * 0.5 + 0.005, z + 0.06], 0xcbd5e1, 'y', 24), `${name}-base-plate`)
}

function addFlange(group, prefix, x, y, z, axis = 'x') {
  attach(addCylinder(group, `${prefix}-pipe`, 0.18, 0.42, [x, y, z], 0x94a3b8, axis, 64), 'chamber-body')
  attach(addCylinder(group, `${prefix}-flange`, 0.25, 0.06, [x, y, z], 0x64748b, axis, 64), `${prefix}-pipe`)
  for (let i = 0; i < 8; i += 1) {
    const a = (Math.PI * 2 * i) / 8
    const by = y + Math.cos(a) * 0.2
    const bz = z + Math.sin(a) * 0.2
    attach(addCylinder(group, `${prefix}-flange-bolt-${i + 1}`, 0.012, 0.07, [x, by, bz], 0xe2e8f0, axis, 16), `${prefix}-flange`)
  }
}

function buildSettlingChamber(project = {}) {
  const group = new THREE.Group()
  group.name = '\u91cd\u529b\u6c89\u964d\u5ba4\u8d28\u91cf\u4fee\u590d\u6a21\u578b'
  const dims = { length: 3.4, width: 1.25, height: 1.25 }
  const body = attach(addBox(group, '\u7bb1\u4f53\u4e3b\u4f53-chamber-body', [dims.length, dims.height * 0.62, dims.width], [0, 0.18, 0], 0x60a5fa, 0.72), 'root')
  body.userData.featurePart = true
  attach(addBox(group, '\u7bb1\u4f53\u9876\u677f-top-plate', [dims.length, 0.045, dims.width], [0, 0.52, 0], 0x38bdf8), 'chamber-body')
  attach(addBox(group, '\u7bb1\u4f53\u5e95\u677f-bottom-plate', [dims.length, 0.045, dims.width], [0, -0.16, 0], 0x2563eb), 'chamber-body')
  attach(addBox(group, 'left-side-plate', [dims.length, dims.height * 0.58, 0.045], [0, 0.18, -dims.width * 0.5], 0x0ea5e9), 'chamber-body')
  attach(addBox(group, 'right-side-plate', [dims.length, dims.height * 0.58, 0.045], [0, 0.18, dims.width * 0.5], 0x0ea5e9), 'chamber-body')

  attach(addBox(group, '\u8fdb\u6c14\u53e3\u6269\u6563\u6bb5-inlet-diffuser', [0.55, 0.5, 0.58], [-dims.length * 0.66, 0.16, 0], 0x22c55e), 'chamber-body')
  addFlange(group, '\u8fdb\u6c14\u53e3-inlet', -dims.length * 0.94, 0.16, 0, 'x')
  addFlange(group, '\u51fa\u6c14\u53e3-outlet', dims.length * 0.94, 0.16, 0, 'x')

  attach(addBox(group, 'guide-plate-1', [0.035, 0.48, dims.width * 0.78], [-0.95, 0.16, 0], 0xfacc15), 'chamber-body')
  attach(addBox(group, 'guide-plate-2', [0.035, 0.46, dims.width * 0.72], [-0.55, 0.14, 0], 0xfacc15), 'chamber-body')
  attach(addBox(group, '\u68c0\u4fee\u95e8-access-door', [0.44, 0.34, 0.035], [0.45, 0.2, dims.width * 0.53], 0xf97316), 'right-side-plate')
  attach(addCylinder(group, 'access-door-handle', 0.018, 0.16, [0.45, 0.2, dims.width * 0.58], 0x111827, 'z', 24), 'access-door')
  attach(addCylinder(group, 'inspection-window', 0.11, 0.035, [-0.2, 0.28, dims.width * 0.54], 0x0f172a, 'z', 48), 'right-side-plate')

  attach(addBox(group, '\u7070\u6597-hopper-front-wall', [dims.length * 0.52, 0.42, 0.045], [0, -0.44, -0.23], 0x64748b), 'bottom-plate')
  attach(addBox(group, '\u7070\u6597-hopper-rear-wall', [dims.length * 0.52, 0.42, 0.045], [0, -0.44, 0.23], 0x64748b), 'bottom-plate')
  attach(addBox(group, 'hopper-left-wall', [0.045, 0.42, 0.46], [-0.88, -0.44, 0], 0x475569), 'bottom-plate')
  attach(addBox(group, 'hopper-right-wall', [0.045, 0.42, 0.46], [0.88, -0.44, 0], 0x475569), 'bottom-plate')
  attach(addCylinder(group, '\u5378\u7070\u53e3-ash-outlet', 0.13, 0.36, [0, -0.78, 0], 0x334155, 'y', 48), 'hopper')
  attach(addTorus(group, 'ash-outlet-flange', 0.16, 0.012, [0, -0.97, 0], 0xcbd5e1, 'z'), 'ash-outlet')

  const legY = -0.48
  ;[[-1.45, -0.46], [-1.45, 0.46], [1.45, -0.46], [1.45, 0.46]].forEach(([x, z], index) => {
    addLeg(group, `\u652f\u6491\u817f-support-leg-${index + 1}`, x, legY, z, 0.62)
  })
  for (let i = 0; i < 8; i += 1) {
    const x = -1.45 + i * (2.9 / 7)
    attach(addBox(group, `\u52a0\u5f3a\u7b4b-rib-${i + 1}`, [0.045, 0.48, 0.045], [x, 0.2, i % 2 === 0 ? -0.64 : 0.64], 0x1d4ed8), 'chamber-body')
  }
  attach(addBox(group, '\u652f\u6491\u67b6-front-cross-beam', [3.1, 0.055, 0.055], [0, -0.75, -0.46], 0x334155), 'support-frame')
  attach(addBox(group, '\u652f\u6491\u67b6-rear-cross-beam', [3.1, 0.055, 0.055], [0, -0.75, 0.46], 0x334155), 'support-frame')
  attach(addBox(group, 'equipment-nameplate', [0.34, 0.16, 0.02], [-1.0, 0.42, 0.64], 0xf8fafc), 'chamber-body')

  group.userData = {
    repairedBy: 'ModelRepairAgent',
    repairReason: 'settling_chamber_core_completion',
    featureBasedParts: 9,
    sourceProject: project.projectTitle || project.equipmentName || ''
  }
  return group
}

function buildGenericRepair() {
  const group = new THREE.Group()
  group.name = 'generic-quality-repair-model'
  attach(addBox(group, 'main-frame', [1.8, 0.22, 0.9], [0, 0, 0], 0x334155), 'root')
  for (let i = 0; i < 6; i += 1) {
    attach(addBox(group, `reinforcing-rib-${i + 1}`, [0.05, 0.32, 0.05], [(-0.75 + i * 0.3), 0.18, i % 2 ? -0.42 : 0.42], 0x64748b), 'main-frame')
  }
  attach(addCylinder(group, 'drive-motor', 0.16, 0.42, [0.55, 0.18, -0.32], 0x2563eb, 'x'), 'main-frame')
  attach(addBox(group, 'reducer', [0.32, 0.28, 0.28], [0.18, 0.15, -0.32], 0x64748b), 'main-frame')
  group.userData = { repairedBy: 'ModelRepairAgent', featureBasedParts: 2 }
  return group
}

export function repairModel(project = {}, failedGroup, quality) {
  const deviceType = quality?.deviceType || modelDeviceType(project)
  if (deviceType === 'settling_chamber') return buildSettlingChamber(project)
  return buildGenericRepair(project, failedGroup, quality)
}
