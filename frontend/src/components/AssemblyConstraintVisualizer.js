import * as THREE from 'three'

function textOf(part = {}) {
  return `${part.partName || ''} ${part.name || ''} ${part.category || ''} ${part.type || ''} ${part.geometry || ''}`.toLowerCase()
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

function buildFromAssemblyModel(project = {}, dims) {
  const assemblyModel = project.assemblyModel || {}
  const components = Array.isArray(assemblyModel.components) ? assemblyModel.components : []
  if (!components.length) return []
  const constraints = Array.isArray(assemblyModel.constraints) ? assemblyModel.constraints : []
  if (!constraints.length) {
    console.error('[DropAI 3D] assembly incomplete: constraints=0')
    return []
  }
  const envelope = extractAssemblyEnvelope(project, dims)
  return components.map((component, index) => {
    const part = {
      ...component,
      partId: component.id,
      name: component.name,
      partType: component.parameters?.partType || component.type,
      category: component.parameters?.category || component.parameters?.geometry || component.type,
      geometry: component.parameters?.geometry,
      material: component.parameters?.material,
      modelingMethod: component.parameters?.modelingMethod,
      featureTree: component.parameters?.featureTree || [],
      cadFeatures: component.parameters?.cadFeatures || []
    }
    const constraint = constraints.find(item => item.componentA === component.id || item.relation?.includes(component.name)) || {}
    return {
      part,
      constraint: {
        ...constraint,
        partId: component.id,
        partName: component.name,
        mountTo: constraint.componentB,
        constraintType: constraint.type,
        position: component.position,
        rotation: component.rotation
      },
      size: normalizeSize({ ...part, size: component.size }, envelope, dims),
      position: normalizePosition({ ...part, position: component.position }, {}, envelope, dims, index),
      rotation: normalizeRotation({ ...part, rotation: component.rotation }, {}),
      hasConstraint: true,
      assemblyModelDriven: true
    }
  })
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
    x: get(/总长|整机长|长度/) || Number(project.totalLength) || Number(project.length) || dims.length * 1000,
    y: get(/总高|整机高|高度/) || Number(project.totalHeight) || Number(project.height) || dims.height * 1000,
    z: get(/总宽|整机宽|宽度/) || Number(project.totalWidth) || Number(project.width) || dims.width * 1000
  }
}

function normalizeSize(part = {}, envelope, dims) {
  const size = part.size || part.dimensions || {}
  const sx = size.x ?? size.length ?? part.length ?? part.width
  const sy = size.y ?? size.height ?? part.height
  const sz = size.z ?? size.width ?? part.depth
  const name = textOf(part)
  let fallback = { x: dims.length * 0.2, y: dims.height * 0.18, z: dims.width * 0.18 }
  if (/履带|track/.test(name)) fallback = { x: dims.length * 0.68, y: dims.height * 0.24, z: dims.width * 0.13 }
  if (/机架|车架|frame/.test(name)) fallback = { x: dims.length * 0.66, y: dims.height * 0.16, z: dims.width * 0.5 }
  if (/外壳|防护|cover|shell/.test(name)) fallback = { x: dims.length * 0.42, y: dims.height * 0.2, z: dims.width * 0.36 }
  if (/磁|吸附|magnet/.test(name)) fallback = { x: dims.length * 0.22, y: dims.height * 0.08, z: dims.width * 0.14 }
  if (/刷|清扫|brush/.test(name)) fallback = { x: dims.length * 0.14, y: dims.height * 0.28, z: dims.height * 0.28 }
  if (/检测|传感|sensor|导轨|滑轨|rail/.test(name)) fallback = { x: dims.length * 0.22, y: dims.height * 0.2, z: dims.width * 0.18 }
  if (/电机|motor/.test(name)) fallback = { x: dims.length * 0.16, y: dims.height * 0.16, z: dims.height * 0.16 }
  if (/减速|reducer|gear/.test(name)) fallback = { x: dims.length * 0.15, y: dims.height * 0.18, z: dims.width * 0.16 }

  const result = {
    x: Number.isFinite(Number(sx)) ? Math.abs(mmToScene(sx, envelope.x, dims.length)) : fallback.x,
    y: Number.isFinite(Number(sy)) ? Math.abs(mmToScene(sy, envelope.y, dims.height)) : fallback.y,
    z: Number.isFinite(Number(sz)) ? Math.abs(mmToScene(sz, envelope.z, dims.width)) : fallback.z
  }
  return {
    x: clamp(result.x, 0.08, dims.length * 0.82),
    y: clamp(result.y, 0.05, dims.height * 0.55),
    z: clamp(result.z, 0.05, dims.width * 0.72)
  }
}

function normalizePosition(part = {}, constraint = {}, envelope, dims, index) {
  const source = part.position || constraint.position || {}
  let x = Number.isFinite(Number(source.x)) ? mmToScene(source.x, envelope.x, dims.length) : 0
  let y = Number.isFinite(Number(source.z)) ? mmToScene(source.z, envelope.y, dims.height) : 0
  let z = Number.isFinite(Number(source.y)) ? mmToScene(source.y, envelope.z, dims.width) : 0
  const text = textOf(part)
  const face = `${constraint.mountingFace || ''}`.toLowerCase()
  const axis = `${constraint.axisId || ''}`.toLowerCase()
  const plane = `${constraint.symmetryPlane || ''}`.toLowerCase()

  if (!part.position && !constraint.position) {
    if (/机架|车架|frame/.test(text)) [x, y, z] = [0, 0, 0]
    else if (/左.*履带|left.*track/.test(text)) [x, y, z] = [0, -dims.height * 0.18, -dims.width * 0.38]
    else if (/右.*履带|right.*track/.test(text)) [x, y, z] = [0, -dims.height * 0.18, dims.width * 0.38]
    else if (/履带|track/.test(text)) [x, y, z] = [0, -dims.height * 0.18, index % 2 === 0 ? -dims.width * 0.38 : dims.width * 0.38]
    else if (/磁|吸附|magnet/.test(text)) [x, y, z] = [(-0.2 + (index % 3) * 0.2) * dims.length, -dims.height * 0.34, 0]
    else if (/刷|清扫|brush/.test(text)) [x, y, z] = [-dims.length * 0.42, -dims.height * 0.04, 0]
    else if (/检测|传感|sensor/.test(text)) [x, y, z] = [-dims.length * 0.34, dims.height * 0.18, 0]
    else if (/外壳|防护|cover|shell/.test(text)) [x, y, z] = [0, dims.height * 0.2, 0]
    else if (/电机|motor/.test(text)) [x, y, z] = [dims.length * 0.24, dims.height * 0.02, index % 2 === 0 ? -dims.width * 0.22 : dims.width * 0.22]
    else if (/减速|reducer|gear/.test(text)) [x, y, z] = [dims.length * 0.12, dims.height * 0.02, index % 2 === 0 ? -dims.width * 0.22 : dims.width * 0.22]
    else if (/驱动轮|drive.*wheel/.test(text)) [x, y, z] = [dims.length * 0.32, -dims.height * 0.18, index % 2 === 0 ? -dims.width * 0.38 : dims.width * 0.38]
    else if (/从动轮|idler|follower/.test(text)) [x, y, z] = [-dims.length * 0.32, -dims.height * 0.18, index % 2 === 0 ? -dims.width * 0.38 : dims.width * 0.38]
    else if (/支重轮|滚轮|roller/.test(text)) [x, y, z] = [(-0.14 + (index % 3) * 0.14) * dims.length, -dims.height * 0.2, index % 2 === 0 ? -dims.width * 0.38 : dims.width * 0.38]
    else [x, y, z] = [0, dims.height * 0.05, 0]
  }

  if (face.includes('bottom') || /底|下/.test(face)) y = Math.min(y, -dims.height * 0.28)
  if (face.includes('top') || /顶|上/.test(face)) y = Math.max(y, dims.height * 0.18)
  if (face.includes('front') || /前/.test(face)) x = Math.min(x, -dims.length * 0.32)
  if (face.includes('rear') || /后/.test(face)) x = Math.max(x, dims.length * 0.25)
  if (axis.includes('track') || /履带|轮系/.test(axis)) y = Math.min(y, -dims.height * 0.15)
  if (plane.includes('left') || /左/.test(plane)) z = -Math.abs(z || dims.width * 0.38)
  if (plane.includes('right') || /右/.test(plane)) z = Math.abs(z || dims.width * 0.38)

  const offset = Number(constraint.offsetDistance)
  if (Number.isFinite(offset)) y += mmToScene(offset, envelope.y, dims.height)

  return {
    x: clamp(x, -dims.length * 0.5, dims.length * 0.5),
    y: clamp(y, -dims.height * 0.42, dims.height * 0.45),
    z: clamp(z, -dims.width * 0.5, dims.width * 0.5)
  }
}

function normalizeRotation(part = {}, constraint = {}) {
  const source = part.rotation || constraint.rotation || {}
  return { x: Number(source.x) || 0, y: Number(source.y) || 0, z: Number(source.z) || 0 }
}

function compareAssemblyOrder(a, b) {
  const order = ['机架', 'frame', '履带', 'track', '驱动轮', '从动轮', '支重轮', '磁', '吸附', '电机', 'motor', '减速', 'reducer', '刷', '清扫', '检测', 'sensor', '外壳', 'cover']
  const aa = textOf(a)
  const bb = textOf(b)
  const ai = order.findIndex(k => aa.includes(k))
  const bi = order.findIndex(k => bb.includes(k))
  return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
}

export function buildAssemblyTransforms(project = {}, dims) {
  const assemblyModelTransforms = buildFromAssemblyModel(project, dims)
  if (assemblyModelTransforms.length) return assemblyModelTransforms
  const components = Array.isArray(project.components) && project.components.length
    ? project.components
    : Array.isArray(project.resolvedParts) ? project.resolvedParts : []
  const constraints = Array.isArray(project.assemblyConstraints) ? project.assemblyConstraints : []
  const envelope = extractAssemblyEnvelope(project, dims)
  const sorted = [...components].sort(compareAssemblyOrder)
  return sorted.map((part, index) => {
    const constraint = findConstraint(part, constraints)
    return {
      part,
      constraint,
      size: normalizeSize(part, envelope, dims),
      position: normalizePosition(part, constraint, envelope, dims, index),
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
    new THREE.Vector3(-dims.length * 0.5, -dims.height * 0.44, 0),
    new THREE.Vector3(dims.length * 0.5, -dims.height * 0.44, 0)
  ])
  const line = new THREE.Line(centerLine, new THREE.LineDashedMaterial({ color: 0x60a5fa, dashSize: 0.08, gapSize: 0.04, transparent: true, opacity: 0.35 }))
  line.computeLineDistances()
  line.name = '整机长度基准线'
  group.add(line)
}
