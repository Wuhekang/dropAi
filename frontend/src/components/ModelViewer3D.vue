<template>
  <div ref="wrap" class="model-viewer" @mouseenter="hovering = true" @mouseleave="hovering = false">
    <ModelControls @reset="resetView" @fullscreen="fullscreen" />
    <div v-if="statusMessage" class="model-status">{{ statusMessage }}</div>
    <div v-if="debugVisible && qualityInfo" class="model-debug">
      <span>{{ '\u6a21\u578b\u5b8c\u6574\u5ea6\uff1a' }}{{ qualityInfo.qualityScore ?? 0 }}</span>
      <span>{{ '\u6838\u5fc3\u7ed3\u6784\uff1a' }}{{ qualityInfo.coreStructureCount ?? 0 }}/{{ qualityInfo.requiredCoreCount ?? 0 }}</span>
      <span>{{ '\u96f6\u4ef6\u6570\u91cf\uff1a' }}{{ qualityInfo.partCount ?? 0 }}</span>
      <span>{{ '\u60ac\u6d6e\u96f6\u4ef6\uff1a' }}{{ (qualityInfo.floatingParts || []).length }}</span>
      <span>{{ '\u8d28\u91cf\u72b6\u6001\uff1a' }}{{ qualityInfo.success ? '\u901a\u8fc7' : qualityInfo.code }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { buildParametricMechanicalModel } from './ParametricModelBuilder'
import ModelControls from './ModelControls.vue'

const props = defineProps({ project: { type: Object, default: () => ({}) } })
const wrap = ref(null)
const hovering = ref(false)
const model = shallowRef(null)
const statusMessage = ref('')
const qualityInfo = ref(null)
let renderer, scene, camera, controls, frameId, observer
const debugVisible = computed(() => import.meta.env.DEV || props.project?.debugModelQuality)

function setupScene() {
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0f172a)
  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
  camera.position.set(4.6, 3.2, 5.2)
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2.5))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.08
  wrap.value.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.screenSpacePanning = true
  controls.minDistance = 0.02
  controls.maxDistance = 10000
  scene.add(new THREE.HemisphereLight(0xdbeafe, 0x1e293b, 1.4))
  const key = new THREE.DirectionalLight(0xffffff, 2.4)
  key.position.set(5, 8, 4)
  key.castShadow = true
  key.shadow.mapSize.width = 2048
  key.shadow.mapSize.height = 2048
  key.shadow.camera.near = 0.1
  key.shadow.camera.far = 60
  scene.add(key)
  const fill = new THREE.DirectionalLight(0x93c5fd, 0.85)
  fill.position.set(-4, 3, -5)
  scene.add(fill)
  const grid = new THREE.GridHelper(8, 16, 0x334155, 0x1e293b)
  grid.position.y = -1
  scene.add(grid)
  setModel()
  observer = new ResizeObserver(resize)
  observer.observe(wrap.value)
  resize()
  animate()
}

function setModel() {
  if (!scene) return
  if (model.value) scene.remove(model.value)
  try {
    model.value = buildParametricMechanicalModel(props.project)
  } catch (error) {
    console.error('3D model generation failed, switched to demo model', error)
    model.value = buildParametricMechanicalModel({})
  }
  qualityInfo.value = model.value?.userData?.quality || null
  if (model.value?.userData?.qualityFailed) {
    statusMessage.value = '\u6a21\u578b\u5b8c\u6574\u6027\u4e0d\u8db3\uff0c\u8bf7\u68c0\u67e5\u4efb\u52a1\u4e66\u6216\u964d\u4f4e\u751f\u6210\u8981\u6c42'
    return
  }
  if (model.value?.userData?.repairedByModelRepairAgent || model.value?.userData?.repairedBy === 'ModelRepairAgent') {
    statusMessage.value = '\u6a21\u578b\u5b8c\u6574\u6027\u6821\u9a8c\u672a\u901a\u8fc7\uff0c\u5df2\u81ea\u52a8\u8865\u5168\u540e\u5c55\u793a'
  } else {
    statusMessage.value = hasModelData(props.project) ? '' : '\u6682\u65e0\u6a21\u578b\u6570\u636e\uff0c\u5df2\u5c55\u793a\u6f14\u793a\u6a21\u578b'
  }
  scene.add(model.value)
  fitCameraToModel(model.value)
}

function hasModelData(project = {}) {
  return (Array.isArray(project.assemblyModel?.components) && project.assemblyModel.components.length > 0)
    || (Array.isArray(project.components) && project.components.length > 0)
    || (Array.isArray(project.resolvedParts) && project.resolvedParts.length > 0)
}

function animate() {
  frameId = requestAnimationFrame(animate)
  if (model.value && !hovering.value) model.value.rotation.y += 0.004
  controls.update()
  renderer.render(scene, camera)
}

function resize() {
  const { clientWidth, clientHeight } = wrap.value
  renderer.setSize(clientWidth, clientHeight)
  camera.aspect = clientWidth / Math.max(1, clientHeight)
  camera.updateProjectionMatrix()
}

function resetView() {
  fitCameraToModel(model.value)
}

function fitCameraToModel(object) {
  if (!object || !camera || !controls) return
  const box = new THREE.Box3().setFromObject(object)
  if (box.isEmpty()) {
    camera.position.set(4.6, 3.2, 5.2)
    controls.target.set(0, 0, 0)
    controls.update()
    return
  }
  const size = box.getSize(new THREE.Vector3())
  const center = box.getCenter(new THREE.Vector3())
  const radius = Math.max(size.x, size.y, size.z, 0.5)
  const distance = radius / Math.tan(THREE.MathUtils.degToRad(camera.fov * 0.5)) * 1.2
  camera.near = Math.max(distance / 500, 0.001)
  camera.far = Math.max(distance * 500, 1000)
  camera.position.set(center.x + distance * 0.9, center.y + distance * 0.58, center.z + distance * 0.95)
  camera.lookAt(center)
  camera.updateProjectionMatrix()
  controls.target.copy(center)
  controls.minDistance = Math.max(radius * 0.03, 0.01)
  controls.maxDistance = Math.max(radius * 80, 1000)
  controls.update()
}

function fullscreen() {
  if (wrap.value.requestFullscreen) wrap.value.requestFullscreen()
}

watch(() => props.project, setModel, { deep: true })
onMounted(setupScene)
onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  observer?.disconnect()
  controls?.dispose()
  renderer?.dispose()
})
</script>

<style scoped>
.model-viewer{position:relative;min-height:440px;height:100%;border-radius:24px;overflow:hidden;background:#0f172a;box-shadow:inset 0 0 70px rgba(59,130,246,.2)}
.model-viewer:fullscreen{width:100vw;height:100vh;border-radius:0}
canvas{display:block;width:100%;height:100%}
.model-status{position:absolute;left:16px;bottom:16px;max-width:calc(100% - 32px);padding:8px 12px;border-radius:8px;background:rgba(15,23,42,.76);color:#dbeafe;font-size:13px;line-height:1.4;pointer-events:none}
.model-debug{position:absolute;left:16px;top:16px;display:grid;gap:4px;max-width:260px;padding:10px 12px;border-radius:8px;background:rgba(15,23,42,.78);color:#dbeafe;font-size:12px;line-height:1.35;pointer-events:none}
</style>
