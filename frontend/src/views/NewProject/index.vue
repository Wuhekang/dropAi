<template>
  <main class="new-project-page">
    <header>
      <el-button text type="primary" @click="router.push('/dashboard')">← 返回 Dashboard</el-button>
    </header>
    <section>
      <span>INDEPENDENT PRODUCT</span>
      <h1>新项目空间</h1>
      <p>这里将承载一个与降重、降 AI 明确分离的新功能。它会拥有独立的页面结构、业务流程和视觉方向，但生成文档仍统一回到 Dashboard 的“我的文档”。</p>
      <el-tag type="info" size="large">等待功能需求</el-tag>
    </section>
  </main>
</template>
<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>
<style scoped>
.new-project-page{min-height:100vh;background:#111827;color:#f8fafc;padding:28px}.new-project-page header{max-width:1100px;margin:auto}.new-project-page section{max-width:800px;margin:18vh auto 0}.new-project-page span{font-size:12px;letter-spacing:.18em;color:#c084fc;font-weight:800}.new-project-page h1{font-size:52px;margin:16px 0}.new-project-page p{font-size:18px;line-height:1.9;color:#cbd5e1;margin-bottom:30px}
</style>
