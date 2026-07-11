import * as THREE from 'three'

function countMeshes(group) {
  let count = 0
  group?.traverse?.(item => {
    if (item.isMesh) count += 1
  })
  return count
}

function validBox(group) {
  const box = new THREE.Box3().setFromObject(group)
  if (box.isEmpty()) return { valid: false, box, size: new THREE.Vector3() }
  const size = box.getSize(new THREE.Vector3())
  const max = Math.max(size.x, size.y, size.z)
  const min = Math.min(size.x, size.y, size.z)
  return {
    valid: Number.isFinite(max) && max > 0.001 && min >= 0,
    box,
    size
  }
}

function checkDistribution(group) {
  const childCenters = []
  for (const child of group?.children || []) {
    if (!child.visible || /grid|shadow|reference|ground|plane/i.test(child.name || '')) continue
    const box = new THREE.Box3().setFromObject(child)
    if (!box.isEmpty()) childCenters.push(box.getCenter(new THREE.Vector3()))
  }
  if (childCenters.length < 2) return { scatteredParts: [], coincidentOriginParts: [] }
  const coincidentOriginParts = []
  const unique = new Set()
  childCenters.forEach((center, index) => {
    if (center.length() < 0.001) coincidentOriginParts.push(`component-${index + 1}`)
    unique.add(`${center.x.toFixed(2)},${center.y.toFixed(2)},${center.z.toFixed(2)}`)
  })
  return {
    scatteredParts: [],
    coincidentOriginParts: unique.size <= 1 ? coincidentOriginParts : []
  }
}

export function modelDeviceType() {
  return 'dynamic_mechanical_ir'
}

export function evaluateModelQuality(project = {}, group, context = {}) {
  const meshCount = countMeshes(group)
  const box = validBox(group)
  const hasBackendAssembly = Array.isArray(project.assemblyModel?.components) && project.assemblyModel.components.length > 0
  const hasBackendConstraints = Array.isArray(project.assemblyModel?.constraints) && project.assemblyModel.constraints.length > 0
  const assemblyIncomplete = hasBackendAssembly && !hasBackendConstraints
  const { scatteredParts, coincidentOriginParts } = checkDistribution(group)
  const issues = []
  if (meshCount <= 0) issues.push('NO_RENDERABLE_GEOMETRY')
  if (!box.valid) issues.push('INVALID_BOUNDING_BOX')
  if (assemblyIncomplete) issues.push('BACKEND_ASSEMBLY_CONSTRAINTS_MISSING')
  if (coincidentOriginParts.length) issues.push('COINCIDENT_ORIGIN_COMPONENTS')

  const success = issues.length === 0
  return {
    success,
    code: success ? 'PREVIEW_RENDERABLE' : 'PREVIEW_NOT_READY',
    message: success ? 'Preview geometry is renderable' : 'CAD assembly is not ready for final preview',
    deviceType: 'dynamic_mechanical_ir',
    missingParts: [],
    symmetryMissing: [],
    floatingParts: [...scatteredParts, ...coincidentOriginParts],
    qualityScore: success ? 100 : Math.max(0, 100 - issues.length * 20),
    partCount: meshCount,
    coreStructureCount: hasBackendAssembly ? project.assemblyModel.components.length : 0,
    requiredCoreCount: 0,
    issues,
    backendAssemblyPresent: hasBackendAssembly,
    backendConstraintsPresent: hasBackendConstraints,
    featureBasedParts: context.featureBasedParts || 0,
    boundingBox: box.valid ? { x: box.size.x, y: box.size.y, z: box.size.z } : null
  }
}
