<template>
  <main class="login">
    <button class="brand" @click="router.push('/')"><b>D</b><span><strong>Dokiai Academic</strong><small>Document Knowledge Intelligence AI</small></span></button>
    <section class="intro">
      <span>WELCOME TO DOKIAI</span><h1>欢迎使用<br><em>Dokiai Academic</em></h1>
      <p>面向学术研究与专业文档创作的 AI 生产工作台，把分散的写作任务组织成一条清晰流程。</p>
      <ul><li><b>01</b><strong>学术优化</strong><small>文档润色与表达优化</small></li><li><b>02</b><strong>文档创作</strong><small>文献、大纲、素材与正文</small></li><li><b>03</b><strong>项目管理</strong><small>进度、历史文件与积分</small></li></ul>
      <footer><b>文献</b><i></i><b>大纲</b><i></i><b>素材</b><i></i><b>正文</b><i></i><b>DOCX</b></footer>
    </section>
    <el-card class="card" shadow="never">
      <div class="mark">D</div><span>{{ registering ? 'CREATE ACCOUNT' : 'ACCOUNT LOGIN' }}</span><h2>{{ registering ? '创建 Dokiai 账号' : '登录 Dokiai Academic' }}</h2><p>使用手机号继续，项目与历史文件将按账号独立保存。</p>
      <el-form :model="form" label-position="top" @submit.prevent="submit"><el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" placeholder="中国大陆手机号" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password placeholder="密码，至少 6 位" /></el-form-item><el-button class="submit" type="primary" :loading="loading" @click="submit">{{ registering ? '注册并进入工作台' : '登录并进入工作台' }}</el-button></el-form>
      <button class="switch" @click="registering = !registering">{{ registering ? '已有账号？直接登录' : '首次使用？创建手机号账号' }}</button><div class="notice"><b>i</b><span><strong>当前使用密码登录</strong><small>未接入短信服务，因此不会生成虚假验证码。</small></span></div>
    </el-card>
  </main>
</template>
<script setup>
import { computed,reactive,ref } from 'vue';import { useRoute,useRouter } from 'vue-router';import { login, register } from '../../api/rewrite';import { setAuthSession } from '../../utils/authStorage'
const router=useRouter(),route=useRoute(),registering=ref(false),loading=ref(false),form=reactive({phone:'',password:''}),college=computed(()=>typeof route.query.college==='string'?route.query.college.trim():'')
async function submit(){if(loading.value||!form.phone||!form.password)return;loading.value=true;try{const payload=registering.value?{...form,...(college.value?{college:college.value}:{})}:form;const result=await(registering.value?register(payload):login(payload));setAuthSession(result);router.replace(String(result.role).toUpperCase()==='SCHOOL_VIEWER'?'/school-statistics':'/dashboard')}finally{loading.value=false}}
</script>
<style scoped>
.login{display:grid;grid-template-columns:minmax(0,690px) 470px;justify-content:center;gap:64px;align-items:center;min-height:100vh;padding:88px 5vw 52px;background:radial-gradient(circle at 12% 5%,#705bef29,transparent 33rem),radial-gradient(circle at 92% 90%,#e266b323,transparent 30rem),linear-gradient(135deg,#fafbff,#f8f5ff 55%,#fff7fb)}.brand{position:absolute;left:6vw;top:27px;display:flex;align-items:center;gap:11px;border:0;background:none;text-align:left}.brand>b,.mark{display:grid;place-items:center;border-radius:12px;color:#fff;background:linear-gradient(145deg,#4198ff,#7658ef 60%,#df66b7);font-weight:900}.brand>b{width:40px;height:40px;font-size:21px}.brand span,.notice span{display:grid}.brand small{color:#8c96aa;font-size:9px}.intro>span,.card>span{color:#6857ea;font-size:11px;font-weight:800;letter-spacing:.18em}.intro h1{margin:18px 0;font-size:clamp(50px,5vw,70px);line-height:1.04;letter-spacing:-.04em}.intro h1 em{font-style:normal;background:linear-gradient(100deg,#586eea,#9860df,#dc65ae);-webkit-background-clip:text;color:transparent}.intro>p{max-width:590px;color:#657189;font-size:17px;line-height:1.75}.intro ul{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:28px 0;padding:0;list-style:none}.intro li{display:grid;gap:7px;padding:14px;border:1px solid #e4e0f3;border-radius:13px;background:#ffffff8f;color:#445067}.intro li>b{display:grid;place-items:center;width:30px;height:30px;border-radius:8px;color:#6859de;background:#ebe8fb;font-size:10px}.intro li small{color:#8a94a8}.intro footer{display:flex;align-items:center;gap:10px;max-width:570px;margin-top:28px;color:#6f7790;font-size:11px}.intro footer i{flex:1;height:1px;background:linear-gradient(90deg,#c9c4ee,#e8cde2)}.card{padding:15px 13px;border:1px solid #ffffffe6;border-radius:24px;background:#ffffffce;box-shadow:0 30px 90px #40397c26;backdrop-filter:blur(24px)}.card :deep(.el-card__body){padding:24px}.mark{width:50px;height:50px;margin-bottom:18px;font-size:24px}.card h2{margin:8px 0;font-size:28px}.card>p{margin-bottom:22px;color:#758096;line-height:1.6}.card :deep(.el-form-item__label){font-weight:650}.card :deep(.el-input__wrapper){min-height:44px;border-radius:11px;background:#fff;box-shadow:0 0 0 1px #e4e2ed inset}.submit{width:100%;height:46px;border-radius:11px;background:linear-gradient(110deg,#5b71ed,#8d5ddd 60%,#d663ae)}.switch{width:100%;margin-top:12px;padding:8px;border:0;color:#6657d7;background:none}.notice{display:flex;gap:10px;margin-top:14px;padding:12px;border-radius:12px;background:#f6f5fb}.notice>b{display:grid;place-items:center;width:23px;height:23px;border-radius:50%;color:#6858dd;background:#e8e5fb}.notice small{color:#8b94a6}@media(max-width:1050px){.login{grid-template-columns:1fr}.intro{display:none}.card{width:min(470px,100%);margin:auto}}@media(max-width:520px){.login{padding-inline:18px}.brand{left:20px}.card :deep(.el-card__body){padding:15px}}
</style>
<style scoped>
@media (min-width: 1051px) {
  .intro,
  .card {
    zoom: 1.1;
  }
}
</style>
