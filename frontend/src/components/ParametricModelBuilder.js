import * as THREE from 'three'

function mat(color, metalness = 0.28, roughness = 0.52, opacity = 1) {
  return new THREE.MeshStandardMaterial({
    color,
    metalness,
    roughness,
    opacity,
    transparent: opacity < 1
  })
}

function addMesh(group, mesh, name, position = [0, 0, 0]) {
  mesh.name = name
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

function box(group, name, size, position, color, opacity = 1) {
  return addMesh(group, new THREE.Mesh(new THREE.BoxGeometry(...size), mat(color, 0.25, 0.56, opacity)), name, position)
}

function cyl(group, name, radius, depth, position, color, axis = 'x', opacity = 1, segments = 48) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, depth, segments), mat(color, 0.32, 0.48, opacity))
  if (axis === 'x') mesh.rotation.z = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

function cone(group, name, topRadius, bottomRadius, height, position, color, axis = 'y') {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(topRadius, bottomRadius, height, 4), mat(color, 0.3, 0.55))
  mesh.rotation.y = Math.PI / 4
  if (axis === 'x') mesh.rotation.z = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

function torus(group, name, radius, tube, position, color, axis = 'x') {
  const mesh = new THREE.Mesh(new THREE.TorusGeometry(radius, tube, 12, 56), mat(color, 0.34, 0.46))
  if (axis === 'x') mesh.rotation.y = Math.PI / 2
  if (axis === 'z') mesh.rotation.x = Math.PI / 2
  return addMesh(group, mesh, name, position)
}

function line(group, name, points, color = 0xdbeafe) {
  const geo = new THREE.BufferGeometry().setFromPoints(points.map(p => new THREE.Vector3(...p)))
  const mesh = new THREE.Line(geo, new THREE.LineBasicMaterial({ color }))
  mesh.name = name
  group.add(mesh)
  return mesh
}

function label(group, text, position) {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 72
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = 'rgba(15,23,42,.72)'
  ctx.roundRect?.(2, 6, 252, 56, 14)
  if (ctx.roundRect) ctx.fill()
  else ctx.fillRect(2, 6, 252, 56)
  ctx.fillStyle = '#dbeafe'
  ctx.font = 'bold 24px Microsoft YaHei, Arial'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 128, 35)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true }))
  sprite.name = `标注-${text}`
  sprite.position.set(...position)
  sprite.scale.set(0.86, 0.24, 1)
  group.add(sprite)
  return sprite
}

function boltCircle(group, center, radius, count, axis = 'x') {
  for (let i = 0; i < count; i += 1) {
    const a = (Math.PI * 2 * i) / count
    const y = center[1] + Math.cos(a) * radius
    const z = center[2] + Math.sin(a) * radius
    const x = center[0]
    cyl(group, '法兰螺栓', 0.025, 0.08, [x, y, z], 0x0f172a, axis, 1, 16)
  }
}

function supportLeg(group, x, z, height) {
  box(group, '支腿立柱', [0.12, height, 0.12], [x, -height / 2 - 0.16, z], 0x475569)
  box(group, '底座板', [0.34, 0.05, 0.28], [x, -height - 0.36, z], 0x1f2937)
  for (const dx of [-0.1, 0.1]) for (const dz of [-0.07, 0.07]) cyl(group, '地脚螺栓', 0.018, 0.07, [x + dx, -height - 0.31, z + dz], 0x111827, 'y', 1, 12)
}

function ladder(group, x, z, height) {
  for (const dz of [-0.09, 0.09]) box(group, '爬梯立杆', [0.035, height, 0.035], [x, height / 2 - 0.95, z + dz], 0xca8a04)
  for (let i = 0; i < 7; i += 1) box(group, '爬梯踏棍', [0.04, 0.025, 0.25], [x, -0.72 + i * 0.22, z], 0xeab308)
}

function rail(group, l, w, h) {
  const y = h * 0.54 + 0.36
  for (const z of [-w * 0.38, w * 0.38]) {
    box(group, '顶部护栏横杆', [l * 0.76, 0.035, 0.035], [0, y, z], 0xca8a04)
    box(group, '顶部护栏中杆', [l * 0.76, 0.03, 0.03], [0, y - 0.18, z], 0xca8a04)
    for (let i = -4; i <= 4; i += 1) box(group, '顶部护栏立杆', [0.03, 0.38, 0.03], [i * l * 0.085, y - 0.18, z], 0xca8a04)
  }
  for (const x of [-l * 0.38, l * 0.38]) {
    box(group, '端部护栏', [0.035, 0.035, w * 0.76], [x, y, 0], 0xca8a04)
    box(group, '端部护栏中杆', [0.03, 0.03, w * 0.76], [x, y - 0.18, 0], 0xca8a04)
  }
}

function detailedSedimentationChamber(group, dims) {
  const l = dims.length
  const w = dims.width
  const h = dims.height
  const shellL = l * 0.82
  const shellW = w * 0.72
  const shellH = h * 0.62
  const shellY = 0.2
  const wall = 0.045

  box(group, '沉降室半透明外壳', [shellL, shellH, shellW], [0, shellY, 0], 0x64748b, 0.38)
  box(group, '前侧壳板', [shellL, shellH, wall], [0, shellY, -shellW / 2], 0x334155, 0.92)
  box(group, '后侧壳板', [shellL, shellH, wall], [0, shellY, shellW / 2], 0x334155, 0.45)
  box(group, '顶板', [shellL, wall, shellW], [0, shellY + shellH / 2, 0], 0x475569)
  box(group, '底部框梁', [shellL + 0.18, 0.09, shellW + 0.18], [0, shellY - shellH / 2 - 0.05, 0], 0x1f2937)

  for (let i = -2; i <= 2; i += 1) box(group, '纵向加强筋', [0.045, shellH * 0.9, 0.055], [i * shellL * 0.16, shellY, -shellW / 2 - 0.035], 0x94a3b8)
  for (const y of [shellY - shellH * 0.22, shellY + shellH * 0.2]) box(group, '横向加强筋', [shellL * 0.92, 0.045, 0.055], [0, y, -shellW / 2 - 0.04], 0x94a3b8)

  box(group, '导流板', [0.06, shellH * 0.76, shellW * 0.54], [-shellL * 0.22, shellY, 0], 0x38bdf8, 0.75)
  box(group, '布流板', [0.06, shellH * 0.7, shellW * 0.48], [-shellL * 0.02, shellY, 0], 0x0ea5e9, 0.6)
  for (let i = -3; i <= 3; i += 1) cyl(group, '布流孔', 0.025, 0.08, [-shellL * 0.02, shellY + i * 0.12, -shellW * 0.25], 0xe0f2fe, 'x', 1, 16)

  const inletX = -shellL / 2 - 0.38
  const outletX = shellL / 2 + 0.38
  cyl(group, '进风管', shellH * 0.13, 0.76, [inletX, shellY + shellH * 0.08, 0], 0x16a34a, 'x')
  cyl(group, '出风管', shellH * 0.13, 0.76, [outletX, shellY + shellH * 0.08, 0], 0x16a34a, 'x')
  for (const x of [inletX + 0.34, outletX - 0.34]) {
    torus(group, '管口法兰', shellH * 0.16, 0.026, [x, shellY + shellH * 0.08, 0], 0xf97316, 'x')
    boltCircle(group, [x, shellY + shellH * 0.08, 0], shellH * 0.18, 8, 'x')
  }

  cone(group, '排灰斗', shellL * 0.09, shellL * 0.19, shellH * 0.42, [-shellL * 0.2, shellY - shellH * 0.53, 0], 0xf59e0b)
  cone(group, '排灰斗', shellL * 0.09, shellL * 0.19, shellH * 0.42, [shellL * 0.2, shellY - shellH * 0.53, 0], 0xf59e0b)
  for (const x of [-shellL * 0.2, shellL * 0.2]) {
    cyl(group, '排灰口', 0.06, 0.18, [x, shellY - shellH * 0.79, 0], 0x78350f, 'y', 1, 24)
    box(group, '星型卸灰阀', [0.22, 0.13, 0.2], [x, shellY - shellH * 0.9, 0], 0xb45309)
  }

  box(group, '检修门', [shellL * 0.18, shellH * 0.34, 0.055], [shellL * 0.12, shellY, -shellW / 2 - 0.075], 0xf97316)
  box(group, '检修门铰链', [0.035, shellH * 0.34, 0.04], [shellL * 0.02, shellY, -shellW / 2 - 0.115], 0x111827)
  cyl(group, '观察窗', shellH * 0.08, 0.035, [shellL * 0.3, shellY + shellH * 0.08, -shellW / 2 - 0.09], 0x93c5fd, 'z', 0.72, 40)

  for (const x of [-shellL * 0.38, shellL * 0.38]) for (const z of [-shellW * 0.38, shellW * 0.38]) supportLeg(group, x, z, shellH * 0.58)
  rail(group, shellL, shellW, shellH)
  ladder(group, -shellL * 0.52, -shellW * 0.55, shellH * 0.92)

  for (const x of [-shellL * 0.28, shellL * 0.28]) {
    torus(group, '吊耳', 0.09, 0.018, [x, shellY + shellH / 2 + 0.08, -shellW * 0.18], 0x0f172a, 'z')
  }

  line(group, '气流方向', [[-shellL * 0.42, shellY + 0.06, 0], [shellL * 0.42, shellY + 0.06, 0]], 0x22c55e)
  label(group, '进风管', [inletX - 0.1, shellY + shellH * 0.36, -0.42])
  label(group, '沉降腔', [0, shellY + shellH * 0.56, 0])
  label(group, '检修门/观察窗', [shellL * 0.25, shellY + shellH * 0.28, -shellW * 0.65])
  label(group, '排灰斗与卸灰阀', [0, shellY - shellH * 0.88, -shellW * 0.42])
}

function conveyor(group, dims) {
  const l = dims.length
  const w = dims.width
  box(group, '桁架机架', [l, 0.16, w], [0, -0.32, 0], 0x475569)
  box(group, '输送带上层', [l * 0.86, 0.08, w * 0.62], [0, 0.08, 0], 0x111827)
  box(group, '输送带回程段', [l * 0.78, 0.06, w * 0.56], [0, -0.1, 0], 0x374151)
  cyl(group, '驱动滚筒', 0.22, w * 0.72, [l * 0.43, 0.12, 0], 0x2563eb, 'z')
  cyl(group, '从动滚筒', 0.22, w * 0.72, [-l * 0.43, 0.12, 0], 0x2563eb, 'z')
  for (let i = -3; i <= 3; i += 1) cyl(group, '托辊', 0.055, w * 0.68, [i * l * 0.11, -0.01, 0], 0x94a3b8, 'z', 1, 24)
  box(group, '电机减速机', [0.45, 0.35, 0.45], [l * 0.5, -0.08, -w * 0.56], 0x10b981)
  box(group, '防护罩', [0.58, 0.28, 0.52], [l * 0.43, 0.3, -w * 0.42], 0xf59e0b, 0.72)
  for (const x of [-l * 0.38, -l * 0.12, l * 0.12, l * 0.38]) for (const z of [-w * 0.32, w * 0.32]) supportLeg(group, x, z, 0.74)
  label(group, '驱动滚筒', [l * 0.42, 0.68, 0])
  label(group, '输送带', [0, 0.48, 0])
}

function manipulator(group, dims) {
  const h = dims.height
  cyl(group, '回转底座', 0.42, 0.22, [0, -0.62, 0], 0x475569, 'y')
  cyl(group, '立柱', 0.16, h * 0.92, [0, -0.08, 0], 0x2563eb, 'y')
  box(group, '大臂', [dims.length * 0.45, 0.16, 0.16], [dims.length * 0.22, 0.55, 0], 0x60a5fa)
  box(group, '小臂', [dims.length * 0.34, 0.13, 0.13], [dims.length * 0.55, 0.4, 0], 0x38bdf8)
  cyl(group, '肩部轴承座', 0.18, 0.22, [0, 0.55, 0], 0xf97316, 'z')
  cyl(group, '肘部轴承座', 0.14, 0.2, [dims.length * 0.42, 0.48, 0], 0xf97316, 'z')
  box(group, '夹爪座', [0.18, 0.16, 0.22], [dims.length * 0.74, 0.36, 0], 0x0f172a)
  for (const z of [-0.1, 0.1]) box(group, '夹爪', [0.26, 0.055, 0.045], [dims.length * 0.88, 0.39, z], 0xeab308)
  label(group, '机械手臂', [dims.length * 0.35, 0.9, 0])
}

function crawlerRobot(group, dims) {
  const l = dims.length
  const w = dims.width
  const h = dims.height
  const trackY = -0.42
  const trackZ = w * 0.34
  for (const z of [-trackZ, trackZ]) {
    box(group, '履带外轮廓', [l * 0.86, 0.16, w * 0.16], [0, trackY, z], 0x111827)
    box(group, '履带接地段', [l * 0.74, 0.06, w * 0.18], [0, trackY - 0.08, z], 0x020617)
    cyl(group, '驱动轮', h * 0.16, w * 0.18, [-l * 0.36, trackY, z], 0x64748b, 'z', 1, 40)
    cyl(group, '从动轮', h * 0.16, w * 0.18, [l * 0.36, trackY, z], 0x64748b, 'z', 1, 40)
    for (let i = -2; i <= 2; i += 1) cyl(group, '支重轮', h * 0.075, w * 0.17, [i * l * 0.14, trackY - 0.02, z], 0x94a3b8, 'z', 1, 24)
  }
  box(group, '铝合金机架', [l * 0.68, 0.1, w * 0.48], [0, -0.2, 0], 0x475569)
  box(group, '防护外壳', [l * 0.48, h * 0.34, w * 0.38], [-l * 0.04, 0.02, 0], 0x2563eb, 0.86)
  box(group, '电池/控制模块安装舱', [l * 0.24, h * 0.22, w * 0.28], [-l * 0.02, 0.05, 0], 0x1d4ed8)
  for (let i = -3; i <= 3; i += 1) {
    box(group, '磁吸附模块', [l * 0.075, 0.035, w * 0.12], [i * l * 0.095, trackY - 0.18, 0], 0x10b981)
  }
  box(group, '检测传感器滑轨', [l * 0.28, 0.045, w * 0.12], [l * 0.34, 0.12, 0], 0x38bdf8)
  box(group, '检测传感器安装架', [l * 0.12, h * 0.18, w * 0.12], [l * 0.43, 0.02, 0], 0x0ea5e9)
  cyl(group, '圆盘清扫刷', h * 0.16, 0.06, [l * 0.52, -0.2, 0], 0xf97316, 'y', 1, 56)
  for (let i = 0; i < 12; i += 1) {
    const a = Math.PI * 2 * i / 12
    line(group, '清扫刷刷丝', [[l * 0.52, -0.2, 0], [l * 0.52 + Math.cos(a) * h * 0.2, -0.2, Math.sin(a) * h * 0.2]], 0xf59e0b)
  }
  for (const z of [-w * 0.18, w * 0.18]) {
    box(group, '驱动电机', [l * 0.12, h * 0.14, w * 0.1], [-l * 0.32, -0.08, z], 0x16a34a)
    box(group, '减速器', [l * 0.1, h * 0.12, w * 0.1], [-l * 0.22, -0.08, z], 0x15803d)
  }
  for (const x of [-l * 0.25, 0, l * 0.25]) for (const z of [-w * 0.18, w * 0.18]) cyl(group, '螺栓连接细节', 0.018, 0.04, [x, 0.12, z], 0x0f172a, 'y', 1, 12)
  label(group, '左右履带行走机构', [0, -0.02, w * 0.62])
  label(group, '磁吸附模块', [-l * 0.08, -0.72, 0])
  label(group, '清扫刷/检测安装架', [l * 0.42, 0.42, 0])
  label(group, '电池与控制舱', [-l * 0.08, 0.42, 0])
}

function engineeringBracket(group, dims) {
  const l = dims.length
  const w = dims.width
  const h = dims.height
  box(group, '箱体主体', [l * 0.62, h * 0.42, w * 0.52], [0, 0.08, 0], 0x60a5fa, 0.62)
  box(group, '底座', [l * 0.74, 0.12, w * 0.66], [0, -0.54, 0], 0x475569)
  box(group, '检修门', [l * 0.18, h * 0.22, 0.045], [0, 0.08, -w * 0.28], 0xf97316)
  for (const x of [-l * 0.25, 0, l * 0.25]) box(group, '加强筋', [0.045, h * 0.48, 0.05], [x, 0.08, -w * 0.31], 0x94a3b8)
  for (const x of [-l * 0.28, l * 0.28]) {
    cyl(group, '接口法兰', 0.18, 0.16, [x, 0.08, 0], 0xf97316, 'x')
    boltCircle(group, [x, 0.08, 0], 0.2, 8, 'x')
  }
  for (const x of [-l * 0.28, l * 0.28]) for (const z of [-w * 0.24, w * 0.24]) supportLeg(group, x, z, 0.62)
  label(group, '工程化机械结构', [0, h * 0.5, 0])
}

function assemblyDrivenModel(group, dims, parts = []) {
  const l = dims.length
  const w = dims.width
  const h = dims.height
  parts.slice(0, 28).forEach((part, index) => {
    const name = part.name || `部件${index + 1}`
    const x = -l * 0.35 + (index % 5) * l * 0.18
    const z = -w * 0.28 + (Math.floor(index / 5) % 4) * w * 0.18
    const y = -0.45 + (index % 4) * h * 0.12
    const color = part.partType === 'standard' ? 0x2563eb : 0xf97316
    if (name.includes('轮') || name.includes('滚筒') || name.includes('刷')) {
      cyl(group, name, h * 0.08, w * 0.16, [x, y, z], color, 'z', 1, 32)
    } else if (name.includes('履带') || name.includes('带')) {
      box(group, name, [l * 0.28, h * 0.08, w * 0.12], [x, y, z], 0x111827)
      cyl(group, `${name}-端轮`, h * 0.055, w * 0.13, [x - l * 0.14, y, z], 0x64748b, 'z', 1, 24)
      cyl(group, `${name}-端轮`, h * 0.055, w * 0.13, [x + l * 0.14, y, z], 0x64748b, 'z', 1, 24)
    } else if (name.includes('磁') || name.includes('吸附')) {
      box(group, name, [l * 0.12, h * 0.035, w * 0.1], [x, -0.66, z], 0x10b981)
    } else if (name.includes('导轨') || name.includes('滑轨') || name.includes('检测')) {
      box(group, name, [l * 0.18, h * 0.04, w * 0.08], [x, y + h * 0.25, z], 0x38bdf8)
    } else if (name.includes('电机') || name.includes('减速器')) {
      box(group, name, [l * 0.11, h * 0.11, w * 0.11], [x, y, z], 0x16a34a)
    } else if (name.includes('外壳') || name.includes('防护')) {
      box(group, name, [l * 0.24, h * 0.22, w * 0.22], [x, y + h * 0.18, z], 0x60a5fa, 0.76)
    } else {
      box(group, name, [l * 0.16, h * 0.08, w * 0.14], [x, y, z], color, 0.88)
    }
  })
  box(group, '装配基准机架', [l * 0.72, h * 0.06, w * 0.62], [0, -0.52, 0], 0x475569)
  label(group, '结构树驱动装配模型', [0, h * 0.45, 0])
}

export function buildParametricMechanicalModel(project = {}) {
  const group = new THREE.Group()
  const dims = {
    length: Math.max(2.8, Number(project.length || project.totalLength || 4200) / 1200),
    width: Math.max(1.35, Number(project.width || project.totalWidth || 2000) / 1200),
    height: Math.max(1.6, Number(project.height || project.totalHeight || 3200) / 1200)
  }
  const type = `${project.designType || ''}${project.equipmentName || ''}${project.projectTitle || ''}`
  if (Array.isArray(project.resolvedParts) && project.resolvedParts.length >= 4) assemblyDrivenModel(group, dims, project.resolvedParts)
  else if (type.includes('输送')) conveyor(group, dims)
  else if (type.includes('爬壁') || type.includes('履带') || type.includes('磁吸附') || type.includes('油罐检测')) crawlerRobot(group, dims)
  else if (type.includes('机械手') || type.includes('机械臂')) manipulator(group, dims)
  else if (type.includes('沉降') || type.includes('除尘') || type.includes('分离')) detailedSedimentationChamber(group, dims)
  else engineeringBracket(group, dims)
  group.rotation.y = -0.45
  return group
}
