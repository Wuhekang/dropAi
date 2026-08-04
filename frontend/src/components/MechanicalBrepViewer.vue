<template><div ref="host" class="brep-viewer"><div v-if="!src" class="viewer-empty"><b>等待真实 BRep 成果</b><span>OpenCascade 验证通过后加载浏览器 STL 预览</span></div></div></template>
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { STLLoader } from 'three/addons/loaders/STLLoader.js'

const props = defineProps({ src: { type: String, default: '' } })
const host = ref(null)
let renderer, scene, camera, controls, model, frame

function init() {
  scene = new THREE.Scene(); scene.background = new THREE.Color(0xe9eeec)
  camera = new THREE.PerspectiveCamera(38, 1, 0.1, 5000); camera.position.set(320, 240, 300)
  renderer = new THREE.WebGLRenderer({ antialias: true }); renderer.setPixelRatio(Math.min(devicePixelRatio, 2)); renderer.shadowMap.enabled = true
  host.value.appendChild(renderer.domElement)
  scene.add(new THREE.HemisphereLight(0xffffff, 0x60706b, 2.2))
  const light = new THREE.DirectionalLight(0xffffff, 2.5); light.position.set(300, 400, 250); light.castShadow = true; scene.add(light)
  const grid = new THREE.GridHelper(500, 20, 0x8fa29c, 0xcbd5d1); grid.position.y = -80; scene.add(grid)
  controls = new OrbitControls(camera, renderer.domElement); controls.enableDamping = true
  resize(); new ResizeObserver(resize).observe(host.value); animate()
}
function resize() { if (!renderer || !host.value) return; const { clientWidth:w, clientHeight:h }=host.value; renderer.setSize(w,h,false); camera.aspect=w/Math.max(h,1); camera.updateProjectionMatrix() }
function load() {
  if (!scene || !props.src) return
  if (model) { scene.remove(model); model.geometry.dispose(); model.material.dispose() }
  new STLLoader().load(props.src, geometry => {
    geometry.computeVertexNormals(); geometry.center()
    model = new THREE.Mesh(geometry, new THREE.MeshStandardMaterial({ color:0x8da9a0, metalness:.45, roughness:.38 }))
    model.castShadow=true; model.receiveShadow=true; scene.add(model)
    const box=new THREE.Box3().setFromObject(model), size=box.getSize(new THREE.Vector3()).length()
    camera.position.set(size*.8,size*.6,size*.9); controls.target.set(0,0,0); controls.update()
  })
}
function animate(){ frame=requestAnimationFrame(animate); controls?.update(); renderer?.render(scene,camera) }
onMounted(()=>{init();load()}); watch(()=>props.src,load)
onBeforeUnmount(()=>{cancelAnimationFrame(frame); controls?.dispose(); renderer?.dispose()})
</script>
<style scoped>.brep-viewer{position:relative;width:100%;height:440px;overflow:hidden;background:#e9eeec}.brep-viewer canvas{display:block}.viewer-empty{position:absolute;inset:0;z-index:2;display:grid;place-content:center;gap:8px;text-align:center;color:#4f5f5a}.viewer-empty span{color:#75817d;font-size:13px}</style>
