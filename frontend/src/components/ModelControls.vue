<template>
  <div class="model-controls">
    <button type="button" @click="$emit('reset')">重置视角</button>
    <button type="button" @click="$emit('fullscreen')">全屏预览</button>
  </div>
</template>

<script setup>
defineEmits(['reset', 'fullscreen'])
</script>

<style scoped>
.model-controls{position:absolute;right:16px;top:16px;display:flex;gap:8px;z-index:2}
.model-controls button{border:1px solid rgba(255,255,255,.28);background:rgba(15,23,42,.72);color:#fff;border-radius:999px;padding:8px 12px;cursor:pointer;backdrop-filter:blur(12px)}
.model-controls button:hover{background:#2563eb}
</style>
