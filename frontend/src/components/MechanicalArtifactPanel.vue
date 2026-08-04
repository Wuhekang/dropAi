<template>
  <div>
    <h2>{{ categoryLabel }}</h2>
    <div v-if="!files.length" class="empty">暂无已验证成果</div>
    <article v-for="file in files" :key="file.name" class="artifact">
      <b>{{ file.name }}</b><span>已验证</span><button @click="$emit('download', file)">下载</button>
    </article>
  </div>
</template>
<script setup>
import { computed } from 'vue'
const props=defineProps({category:{type:String,default:''},artifacts:{type:Array,default:()=>[]}})
defineEmits(['download'])
const files=computed(()=>props.artifacts.filter(item=>item.category===props.category))
const categoryLabel=computed(()=>({DRAWING:'工程图',ANALYSIS:'分析云图',DOCUMENT:'设计文档',PACKAGE:'成果包'})[props.category]||props.category)
</script>
<style scoped>.empty{padding:30px;text-align:center;color:#687772}.artifact{display:grid;grid-template-columns:1fr auto auto;gap:12px;padding:10px;border-bottom:1px solid #e2e7e5}.artifact button{border:0;color:#176b57;background:transparent;cursor:pointer}</style>
