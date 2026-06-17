<template>
  <section class="home-hero-3d">
    <ModelInfoPanel :project="project" :status="status" />
    <div class="viewer-shell">
      <ModelViewer3D :project="project" />
    </div>
  </section>
</template>

<script setup>
import ModelInfoPanel from './ModelInfoPanel.vue'
import ModelViewer3D from './ModelViewer3D.vue'
defineProps({ project: { type: Object, default: () => ({}) }, status: String })
</script>

<style scoped>
.home-hero-3d{display:grid;grid-template-columns:.9fr 1.1fr;gap:22px;align-items:stretch;padding:18px;border-radius:30px;background:linear-gradient(135deg,#0f172a,#172554 55%,#1e3a8a);box-shadow:0 24px 70px rgba(15,23,42,.24)}
.viewer-shell{min-height:460px}
@media(max-width:980px){.home-hero-3d{grid-template-columns:1fr}.viewer-shell{min-height:380px}}
</style>
