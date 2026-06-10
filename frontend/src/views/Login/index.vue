<template>
  <main class="login-page">
    <el-card class="login-card" shadow="always">
      <h1>DropAI</h1>
      <p>登录后，你的改写记录和处理文档将按账号独立保存。</p>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="账号（字母、数字、下划线）" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码（至少 6 位）" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="submit">
          {{ registering ? '注册并进入' : '登录' }}
        </el-button>
      </el-form>
      <el-button text type="primary" style="width:100%;margin-top:12px" @click="registering = !registering">
        {{ registering ? '已有账号，直接登录' : '没有账号，创建账号' }}
      </el-button>
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
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username || !form.password) return
  loading.value = true
  try {
    const result = await (registering.value ? register(form) : login(form))
    localStorage.setItem('dropai_token', result.token)
    localStorage.setItem('dropai_username', result.username)
    router.replace('/rewrite')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: linear-gradient(135deg,#eef4ff,#f8fafc); }
.login-card { width: min(420px,100%); }
h1 { margin: 0 0 8px; font-size: 30px; }
p { color: #64748b; line-height: 1.7; margin-bottom: 24px; }
</style>
