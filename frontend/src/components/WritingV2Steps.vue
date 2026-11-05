<template>
  <div class="v2-steps" aria-label="纯文字稿生成步骤">
    <template v-for="(label, index) in labels" :key="label">
      <span :class="{ active: index + 1 === current, done: index + 1 < current }"><b>{{ index + 1 }}</b>{{ label }}</span>
      <i v-if="index < labels.length - 1"></i>
    </template>
  </div>
</template>

<script setup>
defineProps({ current: { type: Number, required: true } })
const labels = ['文献处理', '文档设计中心', '正文生成']
</script>

<style scoped>
.v2-steps{display:flex;align-items:center;justify-content:center;gap:8px;overflow:auto}.v2-steps span{display:flex;align-items:center;gap:6px;color:#7b8498;white-space:nowrap;font-size:13px}.v2-steps b{display:grid;place-items:center;width:24px;height:24px;border:1px solid #ddd6f5;border-radius:50%;font-size:12px}.v2-steps i{width:28px;height:1px;background:linear-gradient(90deg,#c9c4ee,#e8cde2)}.v2-steps .active{color:#5b4cf0;font-weight:700}.v2-steps .active b,.v2-steps .done b{color:#fff;border-color:transparent;background:linear-gradient(145deg,#4a90ff,#6d5dfb 55%,#df66b7)}.v2-steps .done{color:#6d5c99}@media(max-width:900px){.v2-steps{justify-content:flex-start}.v2-steps i{width:12px}}
</style>
