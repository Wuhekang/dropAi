<template>
  <main class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="phone-mark">手机</div>
      <h1>{{ registering ? '手机号注册' : '手机号登录' }}</h1>
      <p>登录后，你的生成文档和处理记录将按手机号账号独立保存。</p>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.phone" maxlength="11" placeholder="中国大陆手机号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码，至少 6 位" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="submit">
          {{ registering ? '注册并进入 Dashboard' : '登录并进入 Dashboard' }}
        </el-button>
      </el-form>
      <el-button text type="primary" style="width:100%;margin-top:12px" @click="registering = !registering">
        {{ registering ? '已有手机号账号，直接登录' : '首次使用，注册手机号账号' }}
      </el-button>
      <el-alert class="notice" type="info" :closable="false" title="当前使用密码登录" description="未接入短信服务商，因此不会生成虚假短信验证码。" />
    </el-card>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '../../api/rewrite'
const router = useRouter()
const registering = ref(false)
const loading = ref(false)
const form = reactive({ phone: '', password: '' })
async function submit() {
  if (loading.value) return
  if (!form.phone || !form.password) return
  loading.value = true
  try {
    const result = await (registering.value ? register(form) : login(form))
    sessionStorage.setItem('dropai_token', result.token)
    sessionStorage.setItem('dropai_username', result.username)
    sessionStorage.setItem('dropai_role', result.role || 'USER')
    router.replace('/dashboard')
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(circle at 12% -8%,rgba(255,126,179,.24),transparent 32rem),radial-gradient(circle at 88% 2%,rgba(79,172,254,.22),transparent 30rem),linear-gradient(135deg,#fff,#fff5fa 48%,#f3f8ff)}
.login-card{width:min(440px,100%);text-align:center;border:1px solid var(--line);border-radius:8px;background:rgba(255,255,255,.7);box-shadow:var(--shadow);backdrop-filter:blur(20px);animation:page-in .55s ease both}
.phone-mark{width:68px;height:68px;margin:0 auto 18px;border-radius:20px;display:grid;place-items:center;color:white;background:var(--primary-gradient);font-weight:700;font-size:18px;box-shadow:0 18px 38px rgba(108,99,255,.22)}
h1{margin:0 0 10px;font-size:27px;color:var(--text)}p{color:var(--muted);line-height:1.7;margin-bottom:24px}.notice{margin-top:18px;text-align:left}
</style>
