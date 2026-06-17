<template>
  <div ref="wrap" class="model-viewer" @mouseenter="hovering = true" @mouseleave="hovering = false">
    <ModelControls @reset="resetView" @fullscreen="fullscreen" />
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
  model.value = buildParametricMechanicalModel(props.project)
  scene.add(model.value)
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
</style>
