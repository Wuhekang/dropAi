function textOf(part = {}) {
  return `${part.partName || ''} ${part.name || ''} ${part.category || ''} ${part.type || ''}`.toLowerCase()
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function mmToScene(value, referenceMm, sceneReference) {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return (n / Math.max(referenceMm, 1)) * sceneReference
}

function partKey(part = {}) {
  return part.partId || part.id || part.componentId || part.name || part.partName || ''
}

function constraintKey(constraint = {}) {
  return constraint.partId || constraint.componentId || constraint.partName || constraint.name || ''
}

function findConstraint(part, constraints) {
  const key = partKey(part)
  return constraints.find(c => constraintKey(c) === key || c.partName === part.partName || c.mountTo === key) || {}
}

function extractAssemblyEnvelope(project = {}, dims) {
  const candidates = []
  ;[project.parameters, project.explicitParameters, project.derivedParameters, project.suggestedParameters].forEach(list => {
    if (Array.isArray(list)) candidates.push(...list)
  })
  const get = pattern => {
    const found = candidates.find(p => pattern.test(`${p.name || ''}${p.label || ''}`))
    const value = Number(found?.value)
    return Number.isFinite(value) ? value : undefined
  }
  return {
    x: get(/总长|长度|整机长/) || Number(project.totalLength) || Number(project.length) || dims.length * 1000,
    y: get(/总高|高度|整机高/) || Number(project.totalHeight) || Number(project.height) || dims.height * 1000,
    z: get(/总宽|宽度|整机宽/) || Number(project.totalWidth) || Number(project.width) || dims.width * 1000
  }
}

function normalizeSize(part = {}, envelope, dims) {
  const size = part.size || part.dimensions || {}
  const sx = size.x ?? size.length ?? part.length ?? part.width
  const sy = size.y ?? size.height ?? part.height
  const sz = size.z ?? size.width ?? part.depth
  const name = textOf(part)
  let fallback = { x: dims.length * 0.22, y: dims.height * 0.22, z: dims.width * 0.22 }
  if (/履带/.test(name)) fallback = { x: dims.length * 0.72, y: dims.height * 0.28, z: dims.width * 0.16 }
  if (/机架|车架/.test(name)) fallback = { x: dims.length * 0.72, y: dims.height * 0.2, z: dims.width * 0.58 }
  if (/外壳|防护/.test(name)) fallback = { x: dims.length * 0.48, y: dims.height * 0.22, z: dims.width * 0.42 }
  if (/磁|吸附/.test(name)) fallback = { x: dims.length * 0.24, y: dims.height * 0.1, z: dims.width * 0.16 }
  if (/刷|清扫/.test(name)) fallback = { x: dims.length * 0.18, y: dims.height * 0.3, z: dims.height * 0.3 }
  if (/检测|传感器/.test(name)) fallback = { x: dims.length * 0.24, y: dims.height * 0.24, z: dims.width * 0.22 }
  if (/电机/.test(name)) fallback = { x: dims.length * 0.18, y: dims.height * 0.18, z: dims.height * 0.18 }
  if (/减速/.test(name)) fallback = { x: dims.length * 0.17, y: dims.height * 0.2, z: dims.width * 0.18 }
  const result = {
    x: Number.isFinite(Number(sx)) ? Math.abs(mmToScene(sx, envelope.x, dims.length)) : fallback.x,
    y: Number.isFinite(Number(sy)) ? Math.abs(mmToScene(sy, envelope.y, dims.height)) : fallback.y,
    z: Number.isFinite(Number(sz)) ? Math.abs(mmToScene(sz, envelope.z, dims.width)) : fallback.z
  }
  return {
    x: clamp(result.x, 0.08, dims.length * 0.9),
    y: clamp(result.y, 0.06, dims.height * 0.75),
    z: clamp(result.z, 0.06, dims.width * 0.9)
  }
}

function normalizePosition(part = {}, constraint = {}, envelope, dims, index, totals) {
  const source = part.position || constraint.position || {}
  let x = Number.isFinite(Number(source.x)) ? mmToScene(source.x, envelope.x, dims.length) : 0
  let y = Number.isFinite(Number(source.z)) ? mmToScene(source.z, envelope.y, dims.height) : 0
  let z = Number.isFinite(Number(source.y)) ? mmToScene(source.y, envelope.z, dims.width) : 0
  const text = textOf(part)
  const face = `${constraint.mountingFace || ''}`.toLowerCase()
  const axis = `${constraint.axisId || ''}`.toLowerCase()
  const plane = `${constraint.symmetryPlane || ''}`.toLowerCase()

  if (!part.position && !constraint.position) {
    if (/机架|车架/.test(text)) [x, y, z] = [0, 0, 0]
    else if (/左.*履带|left.*track/.test(text)) [x, y, z] = [0, -dims.height * 0.16, -dims.width * 0.44]
    else if (/右.*履带|right.*track/.test(text)) [x, y, z] = [0, -dims.height * 0.16, dims.width * 0.44]
    else if (/履带/.test(text)) [x, y, z] = [0, -dims.height * 0.16, index % 2 === 0 ? -dims.width * 0.44 : dims.width * 0.44]
    else if (/磁|吸附/.test(text)) [x, y, z] = [(-0.24 + (index % 3) * 0.24) * dims.length, -dims.height * 0.36, 0]
    else if (/刷|清扫/.test(text)) [x, y, z] = [-dims.length * 0.52, -dims.height * 0.02, 0]
    else if (/检测|传感器/.test(text)) [x, y, z] = [-dims.length * 0.43, dims.height * 0.2, 0]
    else if (/外壳|防护/.test(text)) [x, y, z] = [dims.length * 0.05, dims.height * 0.23, 0]
    else if (/电机/.test(text)) [x, y, z] = [dims.length * 0.28, dims.height * 0.03, index % 2 === 0 ? -dims.width * 0.28 : dims.width * 0.28]
    else if (/减速/.test(text)) [x, y, z] = [dims.length * 0.18, dims.height * 0.02, index % 2 === 0 ? -dims.width * 0.28 : dims.width * 0.28]
    else if (/驱动轮/.test(text)) [x, y, z] = [dims.length * 0.34, -dims.height * 0.16, index % 2 === 0 ? -dims.width * 0.44 : dims.width * 0.44]
    else if (/从动轮/.test(text)) [x, y, z] = [-dims.length * 0.34, -dims.height * 0.16, index % 2 === 0 ? -dims.width * 0.44 : dims.width * 0.44]
    else if (/支重轮|滚轮/.test(text)) [x, y, z] = [(-0.16 + (index % 3) * 0.16) * dims.length, -dims.height * 0.18, index % 2 === 0 ? -dims.width * 0.44 : dims.width * 0.44]
    else [x, y, z] = [0, dims.height * 0.08, 0]
  }

  if (face.includes('bottom') || /底|下/.test(face)) y = Math.min(y, -dims.height * 0.26)
  if (face.includes('top') || /顶|上/.test(face)) y = Math.max(y, dims.height * 0.22)
  if (face.includes('front') || /前/.test(face)) x = Math.min(x, -dims.length * 0.36)
  if (face.includes('rear') || /后/.test(face)) x = Math.max(x, dims.length * 0.28)
  if (axis.includes('track') || /履带|轮系/.test(axis)) y = Math.min(y, -dims.height * 0.14)
  if (plane.includes('left') || /左/.test(plane)) z = -Math.abs(z || dims.width * 0.44)
  if (plane.includes('right') || /右/.test(plane)) z = Math.abs(z || dims.width * 0.44)

  const offset = Number(constraint.offsetDistance)
  if (Number.isFinite(offset)) y += mmToScene(offset, envelope.y, dims.height)

  return {
    x: clamp(x, -dims.length * 0.62, dims.length * 0.62),
    y: clamp(y, -dims.height * 0.46, dims.height * 0.52),
    z: clamp(z, -dims.width * 0.58, dims.width * 0.58)
  }
}

function normalizeRotation(part = {}, constraint = {}) {
  const source = part.rotation || constraint.rotation || {}
  const rx = Number(source.x) || 0
  const ry = Number(source.y) || 0
  const rz = Number(source.z) || 0
  return { x: rx, y: ry, z: rz }
}

function compareAssemblyOrder(a, b) {
  const order = ['机架', 'frame', '履带', 'track', '驱动轮', '从动轮', '支重轮', '磁', '吸附', '电机', '减速', '刷', '清扫', '检测', '外壳']
  const aa = textOf(a)
  const bb = textOf(b)
  const ai = order.findIndex(k => aa.includes(k))
  const bi = order.findIndex(k => bb.includes(k))
  return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
}

export function buildAssemblyTransforms(project = {}, dims) {
  const components = Array.isArray(project.components) && project.components.length
    ? project.components
    : Array.isArray(project.resolvedParts) ? project.resolvedParts : []
  const constraints = Array.isArray(project.assemblyConstraints) ? project.assemblyConstraints : []
  const envelope = extractAssemblyEnvelope(project, dims)
  const sorted = [...components].sort(compareAssemblyOrder)
  const totals = { count: sorted.length }
  return sorted.map((part, index) => {
    const constraint = findConstraint(part, constraints)
    return {
      part,
      constraint,
      size: normalizeSize(part, envelope, dims),
      position: normalizePosition(part, constraint, envelope, dims, index, totals),
      rotation: normalizeRotation(part, constraint),
      hasConstraint: Boolean(Object.keys(constraint).length || part.position)
    }
  })
}

export function applyTransform(object, transform) {
  object.position.set(transform.position.x, transform.position.y, transform.position.z)
  object.rotation.set(transform.rotation.x, transform.rotation.y, transform.rotation.z)
  object.userData = {
    ...object.userData,
    assemblyConstraint: transform.constraint,
    mountTo: transform.constraint.mountTo,
    axisId: transform.constraint.axisId,
    mountingFace: transform.constraint.mountingFace,
    symmetryPlane: transform.constraint.symmetryPlane
  }
  return object
}

export function addConstraintGuides(group, dims) {
  const centerLine = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-dims.length * 0.56, -dims.height * 0.44, 0),
    new THREE.Vector3(dims.length * 0.56, -dims.height * 0.44, 0)
  ])
  const line = new THREE.Line(centerLine, new THREE.LineDashedMaterial({ color: 0x60a5fa, dashSize: 0.08, gapSize: 0.04, transparent: true, opacity: 0.6 }))
  line.computeLineDistances()
  line.name = '整机长度基准线'
  group.add(line)

  const plane = new THREE.Mesh(
    new THREE.PlaneGeometry(dims.length * 0.96, dims.height * 0.72),
    new THREE.MeshBasicMaterial({ color: 0x38bdf8, transparent: true, opacity: 0.04, side: THREE.DoubleSide })
  )
  plane.name = '整机对称基准面'
  plane.rotation.y = Math.PI / 2
  plane.position.set(0, 0, 0)
  group.add(plane)
}
