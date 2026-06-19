import * as THREE from 'three'

export function material(color, metalness = 0.3, roughness = 0.52, opacity = 1) {
  return new THREE.MeshStandardMaterial({
    color,
    metalness,
    roughness,
    opacity,
    transparent: opacity < 1
  })
}

export function addMesh(group, mesh, name, position = [0, 0, 0]) {
  mesh.name = name
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

export function addBox(group, name, size, position, color, opacity = 1) {
  return addMesh(group, new THREE.Mesh(new THREE.BoxGeometry(...size), material(color, 0.24, 0.56, opacity)), name, position)
}

export function addCylinder(group, name, radius, depth, position, color, axis = 'x', segments = 64) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, depth, segments), material(color, 0.34, 0.46))
  if (axis === 'x') mesh.rotation.z = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

export function addTorus(group, name, radius, tube, position, color, axis = 'x') {
  const mesh = new THREE.Mesh(new THREE.TorusGeometry(radius, tube, 16, 72), material(color, 0.36, 0.44))
  if (axis === 'x') mesh.rotation.y = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

export function addLine(group, name, points, color = 0xdbeafe) {
  const geo = new THREE.BufferGeometry().setFromPoints(points.map(p => new THREE.Vector3(...p)))
  const mesh = new THREE.Line(geo, new THREE.LineBasicMaterial({ color }))
  mesh.name = name
  group.add(mesh)
  return mesh
}

export function semanticCategory(part = {}) {
  const text = `${part.category || ''} ${part.partCategory || ''} ${part.partName || ''} ${part.name || ''} ${part.model || ''}`.toLowerCase()
  if (/motor|电机|马达/.test(text)) return 'motor'
  if (/reducer|gearbox|减速/.test(text)) return 'reducer'
  if (/bearing|轴承/.test(text)) return 'bearing'
  if (/sprocket|链轮/.test(text)) return 'sprocket'
  if (/pulley|同步带轮|带轮/.test(text)) return 'timingPulley'
  if (/roller|滚轮|支重轮|从动轮|驱动轮|履带轮/.test(text)) return 'trackWheel'
  if (/flange|法兰/.test(text)) return 'flange'
  if (/coupling|联轴器/.test(text)) return 'coupling'
  if (/bracket|支架|安装架/.test(text)) return 'bracket'
  if (/plate|安装板|底板/.test(text)) return 'mountingPlate'
  if (/frame|机架|车架|框架/.test(text)) return 'frame'
  if (/track|履带/.test(text)) return 'trackAssembly'
  if (/bolt|screw|螺栓|螺钉/.test(text)) return 'bolt'
  return ''
}

function boltHead(group, name, radius, height, position, color = 0x64748b) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, height, 6), material(color, 0.38, 0.42))
  mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

function boltPattern(group, prefix, x, y, z, lx, lz, countX = 2, countZ = 2) {
  for (let ix = 0; ix < countX; ix += 1) {
    for (let iz = 0; iz < countZ; iz += 1) {
      const px = x + (ix - (countX - 1) / 2) * lx
      const pz = z + (iz - (countZ - 1) / 2) * lz
      boltHead(group, `${prefix}螺栓${ix + 1}-${iz + 1}`, 0.035, 0.026, [px, y, pz])
    }
  }
}

function spokes(group, prefix, radius, hubRadius, width, position, color) {
  const [x, y, z] = position
  for (let i = 0; i < 8; i += 1) {
    const a = (Math.PI * 2 * i) / 8
    const len = radius - hubRadius
    const cx = x
    const cy = y + Math.cos(a) * (hubRadius + len / 2)
    const cz = z + Math.sin(a) * (hubRadius + len / 2)
    const spoke = addBox(group, `${prefix}轮辐${i + 1}`, [width * 1.05, 0.028, len], [cx, cy, cz], color)
    spoke.rotation.x = -a
  }
}

function createBearing(part, size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.42
  const width = Math.max(size.x * 0.72, 0.12)
  addTorus(group, '轴承外圈', r, r * 0.08, [0, 0, 0], 0x9ca3af, 'x')
  addTorus(group, '轴承内圈', r * 0.55, r * 0.075, [0, 0, 0], 0xcbd5e1, 'x')
  addCylinder(group, '轴承宽度体', r * 0.78, width, [0, 0, 0], 0x94a3b8, 'x')
  for (let i = 0; i < 12; i += 1) {
    const a = (Math.PI * 2 * i) / 12
    const ball = new THREE.Mesh(new THREE.SphereGeometry(r * 0.08, 16, 12), material(0xe5e7eb, 0.5, 0.3))
    ball.position.set(0, Math.cos(a) * r * 0.72, Math.sin(a) * r * 0.72)
    ball.name = `滚珠${i + 1}`
    group.add(ball)
  }
  addTorus(group, '保持架', r * 0.72, r * 0.018, [0, 0, 0], 0xfbbf24, 'x')
  group.name = part.partName || part.name || '轴承'
  return group
}

function createMotor(part, size) {
  const group = new THREE.Group()
  const bodyL = Math.max(size.x * 0.62, 0.45)
  const radius = Math.max(size.y, size.z) * 0.28
  addCylinder(group, '电机圆柱壳体', radius, bodyL, [0, 0, 0], 0x2563eb, 'x')
  addCylinder(group, '前端安装法兰', radius * 1.18, 0.08, [-bodyL / 2 - 0.04, 0, 0], 0x1e40af, 'x')
  addCylinder(group, '输出轴', radius * 0.24, size.x * 0.28, [-bodyL / 2 - size.x * 0.17, 0, 0], 0xe5e7eb, 'x')
  addBox(group, '接线盒', [bodyL * 0.28, radius * 0.35, radius * 0.52], [bodyL * 0.12, radius * 0.88, 0], 0x1d4ed8)
  for (let i = 0; i < 9; i += 1) {
    addBox(group, `散热筋${i + 1}`, [bodyL * 0.72, 0.018, 0.032], [0.02, radius * 0.52, (i - 4) * radius * 0.16], 0x60a5fa)
  }
  boltPattern(group, '电机法兰', -bodyL / 2 - 0.084, 0, 0, 0, radius * 1.55, 1, 4)
  group.name = part.partName || part.name || '电机'
  return group
}

function createReducer(part, size) {
  const group = new THREE.Group()
  addBox(group, '减速器箱体', [size.x * 0.72, size.y * 0.62, size.z * 0.62], [0, 0, 0], 0x64748b)
  addCylinder(group, '输入轴', size.y * 0.12, size.x * 0.32, [-size.x * 0.52, size.y * 0.08, 0], 0xe5e7eb, 'x')
  addCylinder(group, '输出轴', size.y * 0.16, size.x * 0.34, [size.x * 0.52, -size.y * 0.06, 0], 0xe2e8f0, 'x')
  addBox(group, '安装底脚左', [size.x * 0.28, size.y * 0.08, size.z * 0.78], [-size.x * 0.18, -size.y * 0.36, 0], 0x475569)
  addBox(group, '安装底脚右', [size.x * 0.28, size.y * 0.08, size.z * 0.78], [size.x * 0.18, -size.y * 0.36, 0], 0x475569)
  for (let i = 0; i < 5; i += 1) {
    addBox(group, `箱体加强筋${i + 1}`, [0.035, size.y * 0.48, 0.035], [(i - 2) * size.x * 0.12, 0.03, size.z * 0.34], 0x94a3b8)
  }
  boltPattern(group, '减速器底脚', 0, -size.y * 0.43, 0, size.x * 0.42, size.z * 0.52, 2, 2)
  group.name = part.partName || part.name || '减速器'
  return group
}

function createTrackWheel(part, size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.42
  const width = Math.max(size.x * 0.34, 0.12)
  addCylinder(group, '履带轮轮缘', r, width, [0, 0, 0], 0x334155, 'x')
  addCylinder(group, '轮毂', r * 0.42, width * 1.18, [0, 0, 0], 0x94a3b8, 'x')
  addCylinder(group, '轴孔', r * 0.16, width * 1.24, [0, 0, 0], 0x0f172a, 'x')
  addTorus(group, '外侧轮缘槽', r * 0.9, r * 0.035, [-width * 0.53, 0, 0], 0x64748b, 'x')
  addTorus(group, '内侧轮缘槽', r * 0.9, r * 0.035, [width * 0.53, 0, 0], 0x64748b, 'x')
  spokes(group, '履带轮', r * 0.75, r * 0.28, width * 0.22, [0, 0, 0], 0xcbd5e1)
  group.name = part.partName || part.name || '履带轮'
  return group
}

function createSprocket(part, size) {
  const group = createTrackWheel(part, size)
  const r = Math.max(size.y, size.z) * 0.47
  for (let i = 0; i < 18; i += 1) {
    const a = (Math.PI * 2 * i) / 18
    const tooth = addBox(group, `链轮齿${i + 1}`, [size.x * 0.16, 0.035, 0.085], [0, Math.cos(a) * r, Math.sin(a) * r], 0x475569)
    tooth.rotation.x = -a
  }
  group.name = part.partName || part.name || '链轮'
  return group
}

function createTimingPulley(part, size) {
  const group = createTrackWheel(part, size)
  for (let i = 0; i < 8; i += 1) {
    addTorus(group, `同步带轮齿槽${i + 1}`, Math.max(size.y, size.z) * (0.26 + i * 0.018), 0.006, [0, 0, 0], 0x1f2937, 'x')
  }
  group.name = part.partName || part.name || '同步带轮'
  return group
}

function createFlange(part, size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.42
  const w = Math.max(size.x * 0.24, 0.08)
  addCylinder(group, '法兰盘', r, w, [0, 0, 0], 0x94a3b8, 'x')
  addCylinder(group, '中心孔', r * 0.36, w * 1.04, [0, 0, 0], 0x0f172a, 'x')
  boltPattern(group, '法兰孔', 0, 0, 0, 0, r * 1.35, 1, 8)
  group.name = part.partName || part.name || '法兰'
  return group
}

function createCoupling(part, size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.24
  addCylinder(group, '左半联轴器', r, size.x * 0.32, [-size.x * 0.18, 0, 0], 0x64748b, 'x')
  addCylinder(group, '右半联轴器', r, size.x * 0.32, [size.x * 0.18, 0, 0], 0x64748b, 'x')
  addCylinder(group, '弹性连接段', r * 0.88, size.x * 0.18, [0, 0, 0], 0xf97316, 'x')
  addCylinder(group, '中心轴孔', r * 0.34, size.x * 0.82, [0, 0, 0], 0x0f172a, 'x')
  boltHead(group, '紧定螺钉左', r * 0.13, r * 0.32, [-size.x * 0.2, r * 0.82, 0], 0xe5e7eb)
  boltHead(group, '紧定螺钉右', r * 0.13, r * 0.32, [size.x * 0.2, r * 0.82, 0], 0xe5e7eb)
  group.name = part.partName || part.name || '联轴器'
  return group
}

function createBracket(part, size) {
  const group = new THREE.Group()
  addBox(group, '立板', [size.x * 0.72, size.y * 0.78, 0.045], [0, 0, -size.z * 0.18], 0x64748b)
  addBox(group, '底板', [size.x * 0.78, 0.05, size.z * 0.58], [0, -size.y * 0.38, size.z * 0.08], 0x475569)
  addBox(group, '三角加强肋左', [0.05, size.y * 0.58, size.z * 0.38], [-size.x * 0.24, -size.y * 0.08, 0], 0x94a3b8)
  addBox(group, '三角加强肋右', [0.05, size.y * 0.58, size.z * 0.38], [size.x * 0.24, -size.y * 0.08, 0], 0x94a3b8)
  boltPattern(group, '支架安装孔', 0, -size.y * 0.43, size.z * 0.08, size.x * 0.42, size.z * 0.3, 2, 2)
  group.name = part.partName || part.name || '支架'
  return group
}

function createMountingPlate(part, size) {
  const group = new THREE.Group()
  addBox(group, '安装板', [size.x, Math.max(size.y * 0.12, 0.035), size.z], [0, 0, 0], 0x64748b)
  addBox(group, '折弯边前', [size.x, size.y * 0.18, 0.035], [0, size.y * 0.12, -size.z * 0.52], 0x475569)
  addBox(group, '折弯边后', [size.x, size.y * 0.18, 0.035], [0, size.y * 0.12, size.z * 0.52], 0x475569)
  boltPattern(group, '安装板孔', 0, size.y * 0.11, 0, size.x * 0.34, size.z * 0.36, 3, 2)
  group.name = part.partName || part.name || '安装板'
  return group
}

function createFrame(part, size) {
  const group = new THREE.Group()
  addBox(group, '左侧梁', [size.x, size.y * 0.12, size.z * 0.08], [0, 0, -size.z * 0.42], 0x334155)
  addBox(group, '右侧梁', [size.x, size.y * 0.12, size.z * 0.08], [0, 0, size.z * 0.42], 0x334155)
  for (let i = 0; i < 4; i += 1) {
    addBox(group, `横梁${i + 1}`, [size.x * 0.08, size.y * 0.1, size.z * 0.86], [(i - 1.5) * size.x * 0.27, 0, 0], 0x475569)
  }
  addBox(group, '中部安装板', [size.x * 0.5, size.y * 0.055, size.z * 0.42], [0, size.y * 0.12, 0], 0x64748b)
  boltPattern(group, '机架孔', 0, size.y * 0.18, 0, size.x * 0.34, size.z * 0.28, 3, 2)
  group.name = part.partName || part.name || '机架'
  return group
}

function createTrackAssembly(part, size) {
  const group = new THREE.Group()
  const len = Math.max(size.x, 1.0)
  const h = Math.max(size.y, 0.28)
  const w = Math.max(size.z, 0.16)
  addTorus(group, '履带前端圆弧', h * 0.42, w * 0.18, [-len * 0.42, 0, 0], 0x111827, 'x')
  addTorus(group, '履带后端圆弧', h * 0.42, w * 0.18, [len * 0.42, 0, 0], 0x111827, 'x')
  addBox(group, '上履带带段', [len * 0.84, h * 0.08, w], [0, h * 0.36, 0], 0x1f2937)
  addBox(group, '下履带带段', [len * 0.84, h * 0.08, w], [0, -h * 0.36, 0], 0x1f2937)
  for (let i = 0; i < 16; i += 1) {
    addBox(group, `履带板${i + 1}`, [len * 0.035, h * 0.035, w * 1.08], [(-0.42 + i * 0.056) * len, -h * 0.42, 0], 0x374151)
  }
  ;[-0.42, 0.42, -0.14, 0.14].forEach((x, index) => {
    const wheel = createTrackWheel({ name: index < 2 ? '履带端轮' : '支重轮' }, [w, h * 0.74, h * 0.74])
    wheel.position.set(x * len, 0, 0)
    group.add(wheel)
  })
  group.name = part.partName || part.name || '履带机构'
  return group
}

function createBolt(part, size) {
  const group = new THREE.Group()
  const r = Math.max(size.y, size.z) * 0.11
  addCylinder(group, '螺杆', r, size.x * 0.76, [0, 0, 0], 0xd1d5db, 'x', 32)
  const head = new THREE.Mesh(new THREE.CylinderGeometry(r * 1.75, r * 1.75, size.x * 0.16, 6), material(0x9ca3af, 0.38, 0.42))
  head.rotation.z = Math.PI / 2
  addMesh(group, head, '六角头', [-size.x * 0.44, 0, 0])
  for (let i = 0; i < 7; i += 1) addTorus(group, `螺纹线${i + 1}`, r * 1.02, 0.004, [(i - 1) * size.x * 0.055, 0, 0], 0x6b7280, 'x')
  group.name = part.partName || part.name || '螺栓'
  return group
}

export function createMechanicalPrimitive(part = {}, size = { x: 0.6, y: 0.3, z: 0.3 }) {
  const normalizedSize = {
    x: Math.max(Number(size.x) || 0.6, 0.08),
    y: Math.max(Number(size.y) || 0.3, 0.08),
    z: Math.max(Number(size.z) || 0.3, 0.08)
  }
  const category = semanticCategory(part)
  const creators = {
    motor: createMotor,
    reducer: createReducer,
    bearing: createBearing,
    sprocket: createSprocket,
    timingPulley: createTimingPulley,
    trackWheel: createTrackWheel,
    flange: createFlange,
    coupling: createCoupling,
    bracket: createBracket,
    mountingPlate: createMountingPlate,
    frame: createFrame,
    trackAssembly: createTrackAssembly,
    bolt: createBolt
  }
  const create = creators[category] || createMountingPlate
  const group = create(part, normalizedSize)
  group.userData = { ...part, primitiveCategory: category || 'mountingPlate' }
  return group
}
