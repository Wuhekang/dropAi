<template>
  <aside class="model-info-panel">
    <span class="eyebrow">PARAMETRIC DESIGN</span>
    <h2>{{ project.projectTitle || '机械毕业设计成果生成平台' }}</h2>
    <p>{{ project.equipmentName || '内置参数化机械模型，可用于首页展示、方案预览和答辩说明。' }}</p>
    <div class="param-grid">
      <div><small>设备类型</small><strong>{{ project.designType || '通用机械结构' }}</strong></div>
      <div><small>生成状态</small><strong>{{ status || '演示模型' }}</strong></div>
      <div><small>总长</small><strong>{{ params.length }} mm</strong></div>
      <div><small>总宽</small><strong>{{ params.width }} mm</strong></div>
      <div><small>总高</small><strong>{{ params.height }} mm</strong></div>
    </div>
    <ul>
      <li>设计参数表</li>
      <li>方案展示图</li>
      <li>CAD总装图与零件图</li>
      <li>设计说明书</li>
    </ul>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ project: { type: Object, default: () => ({}) }, status: String })
const params = computed(() => ({
  length: props.project.length || props.project.totalLength || 4200,
  width: props.project.width || props.project.totalWidth || 1600,
  height: props.project.height || props.project.totalHeight || 1800
}))
</script>

<style scoped>
.model-info-panel{padding:30px;color:#e5edf8}
.eyebrow{color:#93c5fd;font-size:12px;font-weight:800;letter-spacing:.16em}
h2{margin:12px 0 10px;font-size:34px;line-height:1.15}
p{color:#b6c4d7;line-height:1.8}
.param-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin:22px 0}
.param-grid div{padding:14px;border:1px solid rgba(148,163,184,.22);border-radius:14px;background:rgba(15,23,42,.45)}
small{display:block;color:#94a3b8;margin-bottom:6px}strong{color:#fff}
ul{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;padding:0;margin:20px 0 0;list-style:none}
li{padding:10px 12px;border-radius:999px;background:rgba(37,99,235,.18);color:#dbeafe;font-size:13px}
</style>
