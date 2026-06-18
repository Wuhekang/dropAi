import * as THREE from 'three'

function material(color, metalness = 0.28, roughness = 0.5, opacity = 1) {
  return new THREE.MeshStandardMaterial({
    color,
    metalness,
    roughness,
    opacity,
    transparent: opacity < 1
  })
}

function add(group, mesh, name, position = [0, 0, 0]) {
  mesh.name = name
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

function cube(group, name, size, position, color, opacity = 1) {
  return add(group, new THREE.Mesh(new THREE.BoxGeometry(...size), material(color, 0.22, 0.58, opacity)), name, position)
}

function cylinder(group, name, radius, depth, position, color, axis = 'x', opacity = 1, segments = 48) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, depth, segments), material(color, 0.34, 0.46, opacity))
  if (axis === 'x') mesh.rotation.z = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return add(group, mesh, name, position)
}

function hexCylinder(group, name, radius, depth, position, color, axis = 'y') {
  return cylinder(group, name, radius, depth, position, color, axis, 1, 6)
}

function torus(group, name, radius, tube, position, color, axis = 'x') {
  const mesh = new THREE.Mesh(new THREE.TorusGeometry(radius, tube, 16, 72), material(color, 0.36, 0.42))
  if (axis === 'x') mesh.rotation.y = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return add(group, mesh, name, position)
}

function sphere(group, name, radius, position, color, segments = 18) {
  return add(group, new THREE.Mesh(new THREE.SphereGeometry(radius, segments, segments), material(color, 0.42, 0.38)), name, position)
}

function line(group, name, points, color = 0xdbeafe) {
  const geometry = new THREE.BufferGeometry().setFromPoints(points.map(p => new THREE.Vector3(...p)))
  const mesh = new THREE.Line(geometry, new THREE.LineBasicMaterial({ color }))
  mesh.name = name
  group.add(mesh)
  return mesh
}

function localGroup(group, name, position) {
  const partGroup = new THREE.Group()
  partGroup.name = name
  partGroup.position.set(...position)
  group.add(partGroup)
  return partGroup
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function drawBearing(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.46
  const inner = radius * 0.48
  torus(g, 'bearing outer ring', radius, radius * 0.08, [0, 0, 0], 0x334155, 'x')
  torus(g, 'bearing inner ring', inner, radius * 0.08, [0, 0, 0], 0x64748b, 'x')
  cylinder(g, 'bearing center hole', inner * 0.82, sx * 1.04, [0, 0, 0], 0x020617, 'x', 0.92, 48)
  const ballRadius = clamp(radius * 0.095, 0.018, 0.07)
  for (let i = 0; i < 12; i += 1) {
    const angle = (Math.PI * 2 * i) / 12
    sphere(g, 'bearing ball', ballRadius, [0, Math.cos(angle) * radius * 0.72, Math.sin(angle) * radius * 0.72], 0xe2e8f0, 14)
  }
  torus(g, 'bearing cage', radius * 0.72, radius * 0.025, [0, 0, 0], 0xfacc15, 'x')
  return g
}

function drawMotor(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.36
  cylinder(g, 'motor cylindrical housing', radius, sx * 0.72, [-sx * 0.05, 0, 0], 0x2563eb, 'x', 1, 48)
  cylinder(g, 'front mounting flange', radius * 1.22, sx * 0.1, [sx * 0.34, 0, 0], 0x1d4ed8, 'x', 1, 48)
  cylinder(g, 'output shaft', radius * 0.24, sx * 0.36, [sx * 0.58, 0, 0], 0x0f172a, 'x', 1, 24)
  for (let i = 0; i < 6; i += 1) {
    const y = -radius * 0.72 + i * radius * 0.28
    cube(g, 'motor cooling fin', [sx * 0.58, radius * 0.045, radius * 0.08], [-sx * 0.08, y, radius * 1.02], 0x60a5fa)
    cube(g, 'motor cooling fin', [sx * 0.58, radius * 0.045, radius * 0.08], [-sx * 0.08, y, -radius * 1.02], 0x60a5fa)
  }
  for (const y of [-radius * 0.72, radius * 0.72]) for (const z of [-radius * 0.72, radius * 0.72]) {
    cylinder(g, 'motor flange mounting hole', radius * 0.055, sx * 0.115, [sx * 0.405, y, z], 0x020617, 'x', 1, 16)
  }
  cube(g, 'motor terminal box', [sx * 0.2, radius * 0.5, radius * 0.62], [-sx * 0.12, radius * 0.82, 0], 0x0f766e)
  return g
}

function drawReducer(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  cube(g, 'reducer ribbed case', [sx * 0.72, sy * 0.72, sz * 0.72], [0, 0, 0], 0x15803d)
  cylinder(g, 'reducer input shaft', Math.min(sy, sz) * 0.12, sx * 0.32, [-sx * 0.52, 0, 0], 0x111827, 'x', 1, 24)
  cylinder(g, 'reducer output shaft', Math.min(sy, sz) * 0.16, sx * 0.38, [sx * 0.55, 0, 0], 0x111827, 'x', 1, 24)
  cube(g, 'reducer mounting foot', [sx * 0.88, sy * 0.12, sz * 0.22], [0, -sy * 0.48, -sz * 0.26], 0x166534)
  cube(g, 'reducer mounting foot', [sx * 0.88, sy * 0.12, sz * 0.22], [0, -sy * 0.48, sz * 0.26], 0x166534)
  for (const x of [-sx * 0.28, sx * 0.28]) for (const z of [-sz * 0.28, sz * 0.28]) {
    cylinder(g, 'reducer mounting bolt hole', Math.min(sy, sz) * 0.045, sy * 0.13, [x, -sy * 0.41, z], 0x020617, 'y', 1, 12)
  }
  for (let i = -2; i <= 2; i += 1) cube(g, 'reducer reinforcement rib', [sx * 0.04, sy * 0.82, sz * 0.05], [i * sx * 0.13, 0, sz * 0.39], 0x22c55e)
  return g
}

function drawRail(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  cube(g, 'linear guide rail base', [sx, sy * 0.18, sz * 0.28], [0, -sy * 0.16, 0], 0x475569)
  cube(g, 'linear guide rail crown', [sx, sy * 0.16, sz * 0.18], [0, sy * 0.02, 0], 0x64748b)
  cube(g, 'linear slider block', [sx * 0.28, sy * 0.42, sz * 0.72], [sx * 0.1, sy * 0.24, 0], 0x38bdf8)
  for (let i = -3; i <= 3; i += 1) cylinder(g, 'rail counterbore hole', Math.min(sy, sz) * 0.065, sy * 0.08, [i * sx * 0.12, -sy * 0.03, 0], 0x020617, 'y', 1, 16)
  return g
}

function drawCoupling(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.28
  cylinder(g, 'left half coupling', radius, sx * 0.35, [-sx * 0.22, 0, 0], 0xf59e0b, 'x', 1, 32)
  cylinder(g, 'right half coupling', radius, sx * 0.35, [sx * 0.22, 0, 0], 0xf59e0b, 'x', 1, 32)
  cylinder(g, 'elastic spider section', radius * 0.92, sx * 0.18, [0, 0, 0], 0xef4444, 'x', 1, 32)
  for (const x of [-sx * 0.22, sx * 0.22]) cylinder(g, 'coupling set screw', radius * 0.08, radius * 0.7, [x, radius * 0.72, 0], 0x020617, 'y', 1, 12)
  line(g, 'coupling centerline', [[-sx * 0.58, 0, 0], [sx * 0.58, 0, 0]], 0x93c5fd)
  return g
}

function drawBoltGroup(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const count = 4
  for (let i = 0; i < count; i += 1) {
    const x = (i - 1.5) * sx * 0.22
    hexCylinder(g, 'hex bolt head', Math.min(sy, sz) * 0.18, sy * 0.16, [x, sy * 0.1, 0], 0x334155, 'y')
    cylinder(g, 'bolt shank', Math.min(sy, sz) * 0.08, sy * 0.55, [x, -sy * 0.18, 0], 0x64748b, 'y', 1, 16)
    for (let j = 0; j < 4; j += 1) torus(g, 'simplified thread line', Math.min(sy, sz) * 0.085, 0.003, [x, -sy * 0.12 - j * sy * 0.055, 0], 0xe2e8f0, 'y')
  }
  return g
}

function drawFlange(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.44
  cylinder(g, 'flange disk', radius, sx * 0.2, [0, 0, 0], 0xf97316, 'x', 1, 64)
  cylinder(g, 'flange center opening', radius * 0.42, sx * 0.215, [0, 0, 0], 0x020617, 'x', 0.95, 48)
  for (let i = 0; i < 8; i += 1) {
    const a = (Math.PI * 2 * i) / 8
    cylinder(g, 'flange bolt hole', radius * 0.07, sx * 0.22, [0, Math.cos(a) * radius * 0.72, Math.sin(a) * radius * 0.72], 0x111827, 'x', 1, 16)
  }
  return g
}

function drawShaft(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.16
  cylinder(g, 'stepped shaft main journal', radius, sx * 0.72, [0, 0, 0], 0x64748b, 'x', 1, 32)
  cylinder(g, 'shaft shoulder', radius * 1.32, sx * 0.16, [-sx * 0.32, 0, 0], 0x94a3b8, 'x', 1, 32)
  cylinder(g, 'shaft output journal', radius * 0.82, sx * 0.28, [sx * 0.42, 0, 0], 0x475569, 'x', 1, 32)
  cube(g, 'shaft keyway flat', [sx * 0.22, radius * 0.2, radius * 0.32], [sx * 0.18, radius * 0.82, 0], 0x111827)
  line(g, 'shaft centerline', [[-sx * 0.62, 0, 0], [sx * 0.62, 0, 0]], 0x93c5fd)
  return g
}

function drawKey(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  cube(g, 'parallel key body', [sx * 0.7, Math.max(0.018, sy * 0.22), Math.max(0.024, sz * 0.28)], [0, 0, 0], 0x334155)
  cube(g, 'key chamfer marker', [sx * 0.08, Math.max(0.012, sy * 0.16), Math.max(0.02, sz * 0.24)], [-sx * 0.38, sy * 0.04, 0], 0x64748b)
  cube(g, 'key chamfer marker', [sx * 0.08, Math.max(0.012, sy * 0.16), Math.max(0.02, sz * 0.24)], [sx * 0.38, sy * 0.04, 0], 0x64748b)
  return g
}

function drawPin(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sy, sz) * 0.12
  cylinder(g, 'dowel pin body', radius, sx * 0.72, [0, 0, 0], 0x94a3b8, 'x', 1, 28)
  cylinder(g, 'pin chamfer end', radius * 0.88, sx * 0.05, [-sx * 0.39, 0, 0], 0xe2e8f0, 'x', 1, 28)
  cylinder(g, 'pin chamfer end', radius * 0.88, sx * 0.05, [sx * 0.39, 0, 0], 0xe2e8f0, 'x', 1, 28)
  return g
}

function drawSpring(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const points = []
  const radius = Math.max(sy, sz) * 0.18
  const turns = 7
  const samples = 120
  for (let i = 0; i <= samples; i += 1) {
    const t = i / samples
    const a = Math.PI * 2 * turns * t
    points.push([-sx * 0.42 + sx * 0.84 * t, Math.cos(a) * radius, Math.sin(a) * radius])
  }
  line(g, 'coil spring wire', points, 0xeab308)
  cylinder(g, 'spring end seat', radius * 1.12, sx * 0.035, [-sx * 0.45, 0, 0], 0xca8a04, 'x', 1, 32)
  cylinder(g, 'spring end seat', radius * 1.12, sx * 0.035, [sx * 0.45, 0, 0], 0xca8a04, 'x', 1, 32)
  return g
}

function drawTrackAssembly(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const wheelR = Math.max(sy, sz) * 0.26
  cube(g, 'rubber track upper run', [sx * 0.82, sy * 0.16, sz * 0.88], [0, sy * 0.18, 0], 0x111827)
  cube(g, 'rubber track lower run', [sx * 0.82, sy * 0.16, sz * 0.88], [0, -sy * 0.18, 0], 0x020617)
  cylinder(g, 'track drive sprocket', wheelR, sz * 0.96, [-sx * 0.41, 0, 0], 0x64748b, 'z', 1, 40)
  cylinder(g, 'track idler wheel', wheelR, sz * 0.96, [sx * 0.41, 0, 0], 0x64748b, 'z', 1, 40)
  for (let i = -2; i <= 2; i += 1) cylinder(g, 'track support roller', wheelR * 0.46, sz * 0.9, [i * sx * 0.14, -sy * 0.03, 0], 0x94a3b8, 'z', 1, 24)
  for (let i = -8; i <= 8; i += 1) cube(g, 'individual track shoe', [sx * 0.032, sy * 0.22, sz * 0.96], [i * sx * 0.045, sy * 0.34, 0], 0x1f2937)
  return g
}

function drawBrush(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  const radius = Math.max(sx, sz) * 0.34
  cylinder(g, 'brush disk', radius, Math.max(0.035, sy * 0.28), [0, 0, 0], 0xf97316, 'y', 1, 56)
  cylinder(g, 'brush center hub', radius * 0.22, sy * 0.45, [0, 0, 0], 0x0f172a, 'y', 1, 24)
  for (let i = 0; i < 28; i += 1) {
    const a = (Math.PI * 2 * i) / 28
    line(g, 'radial brush bristle', [[Math.cos(a) * radius * 0.35, 0, Math.sin(a) * radius * 0.35], [Math.cos(a) * radius * 1.18, -sy * 0.18, Math.sin(a) * radius * 1.18]], 0xfbbf24)
  }
  cube(g, 'brush motor seat', [sx * 0.34, sy * 0.32, sz * 0.28], [-sx * 0.35, sy * 0.22, 0], 0x334155)
  return g
}

function drawMagnetModule(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  cube(g, 'magnet mounting plate', [sx, sy * 0.24, sz], [0, 0, 0], 0x475569)
  for (let i = -2; i <= 2; i += 1) cube(g, 'permanent magnet block', [sx * 0.13, sy * 0.48, sz * 0.62], [i * sx * 0.16, -sy * 0.28, 0], 0x10b981)
  cube(g, 'magnet protective cover', [sx * 0.92, sy * 0.12, sz * 0.9], [0, sy * 0.24, 0], 0x94a3b8, 0.55)
  for (const x of [-sx * 0.38, sx * 0.38]) for (const z of [-sz * 0.32, sz * 0.32]) cylinder(g, 'magnet fixing hole', Math.min(sy, sz) * 0.055, sy * 0.26, [x, sy * 0.08, z], 0x020617, 'y', 1, 12)
  return g
}

function drawSensorBracket(group, name, size, position) {
  const [sx, sy, sz] = size
  const g = localGroup(group, name, position)
  cube(g, 'sensor vertical plate', [sx * 0.18, sy * 0.82, sz * 0.5], [-sx * 0.34, 0, 0], 0x0ea5e9)
  cube(g, 'sensor cross beam', [sx * 0.8, sy * 0.16, sz * 0.3], [0, sy * 0.28, 0], 0x38bdf8)
  cube(g, 'sensor guide rail', [sx * 0.72, sy * 0.08, sz * 0.16], [sx * 0.04, sy * 0.04, 0], 0x64748b)
  cube(g, 'sensor slider', [sx * 0.18, sy * 0.22, sz * 0.42], [sx * 0.25, sy * 0.06, 0], 0x22d3ee)
  cube(g, 'sensor block', [sx * 0.16, sy * 0.28, sz * 0.32], [sx * 0.42, -sy * 0.16, 0], 0xfacc15)
  for (const y of [-sy * 0.22, sy * 0.22]) cylinder(g, 'sensor mounting hole', sz * 0.055, sx * 0.19, [-sx * 0.44, y, 0], 0x020617, 'x', 1, 12)
  return g
}

export function drawParametricStandardPartGeometry(group, part, size, position, fallbackColor = 0x2563eb) {
  const geometry = String(part?.geometry || '').toUpperCase()
  const name = part?.name || 'part'
  if (geometry.includes('BEARING')) return drawBearing(group, name, size, position)
  if (geometry.includes('MOTOR')) return drawMotor(group, name, size, position)
  if (geometry.includes('GEARBOX') || geometry.includes('REDUCER')) return drawReducer(group, name, size, position)
  if (geometry.includes('RAIL') && !geometry.includes('SENSOR')) return drawRail(group, name, size, position)
  if (geometry.includes('COUPLING')) return drawCoupling(group, name, size, position)
  if (geometry.includes('BOLT')) return drawBoltGroup(group, name, size, position)
  if (geometry.includes('FLANGE')) return drawFlange(group, name, size, position)
  if (geometry.includes('SHAFT')) return drawShaft(group, name, size, position)
  if (geometry.includes('KEY')) return drawKey(group, name, size, position)
  if (geometry.includes('PIN')) return drawPin(group, name, size, position)
  if (geometry.includes('SPRING')) return drawSpring(group, name, size, position)
  if (geometry.includes('TRACK')) return drawTrackAssembly(group, name, size, position)
  if (geometry.includes('BRUSH') || name.includes('刷')) return drawBrush(group, name, size, position)
  if (geometry.includes('MAGNET') || name.includes('磁')) return drawMagnetModule(group, name, size, position)
  if (geometry.includes('SENSOR_RAIL') || name.includes('检测') || name.includes('传感')) return drawSensorBracket(group, name, size, position)
  cube(group, name, size, position, fallbackColor, 0.88)
  return null
}

export function hasParametricGeometry(part = {}) {
  const geometry = String(part.geometry || '').toUpperCase()
  const name = String(part.name || '')
  return [
    'BEARING', 'MOTOR', 'GEARBOX', 'REDUCER', 'RAIL', 'COUPLING', 'BOLT',
    'FLANGE', 'SHAFT', 'KEY', 'PIN', 'SPRING', 'TRACK', 'BRUSH', 'MAGNET', 'SENSOR_RAIL'
  ].some(key => geometry.includes(key)) || ['刷', '磁', '检测', '传感'].some(key => name.includes(key))
}
