import * as THREE from 'three'

function mat(color, metalness = 0.25, roughness = 0.55) {
  return new THREE.MeshStandardMaterial({ color, metalness, roughness })
}

function box(group, name, size, position, color) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(...size), mat(color))
  mesh.name = name
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

function cyl(group, name, radius, depth, position, color, axis = 'x') {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, depth, 48), mat(color))
  mesh.name = name
  if (axis === 'x') mesh.rotation.z = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

function hopper(group, l, w, h) {
  const shape = new THREE.Shape()
  shape.moveTo(-l / 2, h / 2)
  shape.lineTo(l / 2, h / 2)
  shape.lineTo(l * 0.22, -h / 2)
  shape.lineTo(-l * 0.22, -h / 2)
  shape.lineTo(-l / 2, h / 2)
  const geo = new THREE.ExtrudeGeometry(shape, { depth: w, bevelEnabled: false })
  const mesh = new THREE.Mesh(geo, mat(0xf59e0b))
  mesh.position.set(-l / 2, -h / 2, -w / 2)
  mesh.castShadow = true
  group.add(mesh)
  return mesh
}

function conveyor(group, dims) {
  const l = dims.length, w = dims.width
  box(group, '机架', [l, 0.18, w], [0, -0.35, 0], 0x64748b)
  box(group, '输送带上层', [l * 0.86, 0.08, w * 0.62], [0, 0.1, 0], 0x1f2937)
  box(group, '输送带下层', [l * 0.78, 0.06, w * 0.56], [0, -0.08, 0], 0x374151)
  cyl(group, '驱动滚筒', 0.22, w * 0.72, [l * 0.43, 0.13, 0], 0x2563eb, 'z')
  cyl(group, '从动滚筒', 0.22, w * 0.72, [-l * 0.43, 0.13, 0], 0x2563eb, 'z')
  box(group, '电机减速机', [0.45, 0.35, 0.45], [l * 0.48, -0.08, -w * 0.56], 0x10b981)
  for (const x of [-l * 0.35, l * 0.35]) for (const z of [-w * 0.32, w * 0.32]) box(group, '支腿', [0.08, 0.75, 0.08], [x, -0.72, z], 0x475569)
}

function chamber(group, dims) {
  const l = dims.length, w = dims.width, h = dims.height
  box(group, '沉降腔', [l * 0.68, h * 0.46, w * 0.64], [0, 0.22, 0], 0x60a5fa)
  cyl(group, '进风口', h * 0.12, l * 0.18, [-l * 0.43, 0.28, 0], 0x22c55e, 'x')
  cyl(group, '出风口', h * 0.12, l * 0.18, [l * 0.43, 0.28, 0], 0x22c55e, 'x')
  hopper(group, l * 0.22, w * 0.48, h * 0.28).position.set(-l * 0.22, -0.38, -w * 0.24)
  hopper(group, l * 0.22, w * 0.48, h * 0.28).position.set(l * 0.08, -0.38, -w * 0.24)
  box(group, '检修门', [l * 0.18, h * 0.24, 0.04], [0, 0.24, -w * 0.34], 0xf97316)
  box(group, '支撑架', [l * 0.72, 0.12, w * 0.72], [0, -0.58, 0], 0x475569)
}

function bracket(group, dims) {
  const l = dims.length, w = dims.width, h = dims.height
  box(group, '底板', [l * 0.72, 0.12, w * 0.64], [0, -0.58, 0], 0x64748b)
  box(group, '立板', [0.14, h * 0.72, w * 0.18], [-l * 0.18, -0.14, 0], 0x60a5fa)
  box(group, '横梁', [l * 0.58, 0.14, w * 0.18], [0.05, 0.16, 0], 0x2563eb)
  cyl(group, '法兰孔', 0.12, 0.08, [l * 0.28, 0.16, 0], 0xf97316, 'z')
  for (const x of [-l * 0.25, l * 0.25]) for (const z of [-w * 0.2, w * 0.2]) cyl(group, '安装孔', 0.055, 0.13, [x, -0.5, z], 0x111827, 'y')
}

export function buildParametricMechanicalModel(project = {}) {
  const group = new THREE.Group()
  const dims = {
    length: Math.max(2.5, Number(project.length || project.totalLength || 4200) / 1200),
    width: Math.max(1.2, Number(project.width || project.totalWidth || 1600) / 1200),
    height: Math.max(1.2, Number(project.height || project.totalHeight || 1800) / 1200)
  }
  const type = `${project.designType || project.equipmentName || ''}`
  if (type.includes('输送')) conveyor(group, dims)
  else if (type.includes('沉降') || type.includes('分离')) chamber(group, dims)
  else bracket(group, dims)
  group.rotation.y = -0.45
  return group
}
