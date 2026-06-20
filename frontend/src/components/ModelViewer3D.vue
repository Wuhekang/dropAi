<template>
  <div ref="wrap" class="model-viewer" @mouseenter="hovering = true" @mouseleave="hovering = false">
    <ModelControls @reset="resetView" @fullscreen="fullscreen" />
    <div v-if="statusMessage" class="model-status">{{ statusMessage }}</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { buildParametricMechanicalModel } from './ParametricModelBuilder'
import ModelControls from './ModelControls.vue'

const props = defineProps({ project: { type: Object, default: () => ({}) } })
const wrap = ref(null)
const hovering = ref(false)
const model = shallowRef(null)
const statusMessage = ref('')
let renderer, scene, camera, controls, frameId, observer

function setupScene() {
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0f172a)
  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
  camera.position.set(4.6, 3.2, 5.2)
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  wrap.value.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.minDistance = 2.5
  controls.maxDistance = 12
  scene.add(new THREE.HemisphereLight(0xdbeafe, 0x1e293b, 1.4))
  const key = new THREE.DirectionalLight(0xffffff, 2.4)
  key.position.set(5, 8, 4)
  key.castShadow = true
  scene.add(key)
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
    console.error('3D方案模型生成失败，已切换到演示模型', error)
    model.value = buildParametricMechanicalModel({})
  }
  statusMessage.value = hasModelData(props.project) ? '' : '暂无模型数据，已展示演示模型'
  scene.add(model.value)
}

function hasModelData(project = {}) {
  return (Array.isArray(project.components) && project.components.length > 0)
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
  camera.position.set(4.6, 3.2, 5.2)
  controls.target.set(0, 0, 0)
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
</style>
