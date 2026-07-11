import * as THREE from 'three'

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function number(value, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function mmToScene(value, referenceMm, sceneReference) {
  return (number(value) / Math.max(referenceMm, 1)) * sceneReference
}

function collectParameters(project = {}) {
  const params = []
  ;[project.parameters, project.explicitParameters, project.derivedParameters, project.suggestedParameters].forEach(list => {
    if (Array.isArray(list)) params.push(...list)
  })
  return params
}

function parameterValue(project, patterns, fallback) {
  const found = collectParameters(project).find(item => patterns.some(pattern => pattern.test(`${item.name || ''}${item.label || ''}`)))
  const value = Number(found?.value)
  return Number.isFinite(value) ? value : fallback
}

function assemblyEnvelope(project = {}, dims) {
  const components = Array.isArray(project.assemblyModel?.components) ? project.assemblyModel.components : []
  const maxFromComponents = axis => Math.max(0, ...components.map(component => {
    const position = Math.abs(number(component.position?.[axis], 0))
    const size = Math.abs(number(component.size?.[axis], 0))
    return position + size
  }))
  return {
    x: parameterValue(project, [/总长/, /整机长/, /长度/, /length/i], Math.max(maxFromComponents('x') * 1.2, number(project.totalLength, 0), 1000)),
    y: parameterValue(project, [/总宽/, /整机宽/, /宽度/, /width/i], Math.max(maxFromComponents('y') * 1.2, number(project.totalWidth, 0), 600)),
    z: parameterValue(project, [/总高/, /整机高/, /高度/, /height/i], Math.max(maxFromComponents('z') * 1.2, number(project.totalHeight, 0), 400))
  }
}

function componentId(component = {}) {
  return component.id || component.partId || component.componentId || component.name || ''
}

function componentPart(component = {}) {
  const parameters = component.parameters || {}
  return {
    ...parameters,
    ...component,
    partId: componentId(component),
    name: component.name || componentId(component),
    partType: parameters.partType || component.type || parameters.category,
    category: parameters.category || parameters.geometry || component.type,
    geometry: parameters.geometry,
    material: parameters.material,
    modelingMethod: parameters.modelingMethod,
    featureTree: parameters.featureTree || [],
    cadFeatures: parameters.cadFeatures || []
  }
}

function transformSize(component = {}, envelope, dims) {
  const size = component.size || {}
  const x = Math.abs(mmToScene(size.x, envelope.x, dims.length))
  const y = Math.abs(mmToScene(size.z, envelope.z, dims.height))
  const z = Math.abs(mmToScene(size.y, envelope.y, dims.width))
  return {
    x: clamp(x || dims.length * 0.08, 0.03, dims.length),
    y: clamp(y || dims.height * 0.08, 0.02, dims.height),
    z: clamp(z || dims.width * 0.08, 0.02, dims.width)
  }
}

function transformPosition(component = {}, envelope, dims) {
  const position = component.position || {}
  const x = mmToScene(position.x, envelope.x, dims.length)
  const y = mmToScene(position.z, envelope.z, dims.height)
  const z = mmToScene(position.y, envelope.y, dims.width)
  return {
    x: clamp(x, -dims.length * 0.6, dims.length * 0.6),
    y: clamp(y, -dims.height * 0.55, dims.height * 0.65),
    z: clamp(z, -dims.width * 0.6, dims.width * 0.6)
  }
}

function transformRotation(component = {}) {
  const rotation = component.rotation || {}
  return {
    x: number(rotation.x),
    y: number(rotation.z),
    z: number(rotation.y)
  }
}

function findConstraint(component = {}, constraints = []) {
  const id = componentId(component)
  return constraints.find(item => item.componentA === id || item.partA === id || item.relation?.includes(component.name)) || null
}

export function buildAssemblyTransforms(project = {}, dims) {
  const assemblyModel = project.assemblyModel || {}
  const components = Array.isArray(assemblyModel.components) ? assemblyModel.components : []
  const constraints = Array.isArray(assemblyModel.constraints) ? assemblyModel.constraints : []
  if (!components.length || !constraints.length) {
    console.error('[DropAI 3D] AssemblyModel is not ready', {
      components: components.length,
      constraints: constraints.length
    })
    return []
  }
  const envelope = assemblyEnvelope(project, dims)
  return components.map(component => {
    const constraint = findConstraint(component, constraints)
    return {
      part: componentPart(component),
      constraint: {
        ...(constraint || {}),
        partId: componentId(component),
        partName: component.name,
        mountTo: constraint?.componentB || constraint?.partB || component.parent || ''
      },
      size: transformSize(component, envelope, dims),
      position: transformPosition(component, envelope, dims),
      rotation: transformRotation(component),
      hasConstraint: Boolean(constraint),
      assemblyModelDriven: true
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
    assemblyModelDriven: true
  }
  return object
}

export function addConstraintGuides(group, dims) {
  const centerLine = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-dims.length * 0.5, -dims.height * 0.44, 0),
    new THREE.Vector3(dims.length * 0.5, -dims.height * 0.44, 0)
  ])
  const line = new THREE.Line(
    centerLine,
    new THREE.LineDashedMaterial({ color: 0x60a5fa, dashSize: 0.08, gapSize: 0.04, transparent: true, opacity: 0.35 })
  )
  line.computeLineDistances()
  line.name = 'assembly-reference-line'
  group.add(line)
}
