<template>
  <main class="login-page">
    <header class="login-bar">
      <BrandLogo as="button" size="md" title="Dokiai" subtitle="Academic" @click="router.push('/')" />
    </header>

    <section class="login-layout">
      <div class="login-lead">
        <h1>{{ registering ? '注册账号' : '登录工作台' }}</h1>
        <p>使用手机号保存论文、项目和历史文件。每个账号的记录互相独立。</p>
      </div>

      <el-card class="login-card" shadow="never">
        <h2>{{ registering ? '创建账号' : '账号登录' }}</h2>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="手机号">
            <el-input v-model="form.phone" maxlength="11" placeholder="中国大陆手机号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-button class="submit" type="primary" :loading="loading" @click="submit">
            {{ registering ? '注册并进入工作台' : '登录并进入工作台' }}
          </el-button>
        </el-form>
        <button class="switch" type="button" @click="registering = !registering">
          {{ registering ? '已有账号，直接登录' : '首次使用，创建账号' }}
        </button>
        <p class="notice">请使用手机号和密码。短信验证码暂未开放。</p>
      </el-card>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '../../components/BrandLogo.vue'
import { login, register } from '../../api/rewrite'
import { setAuthSession } from '../../utils/authStorage'

const router = useRouter()
const route = useRoute()
const registering = ref(false)
const loading = ref(false)
const form = reactive({ phone: '', password: '' })
const college = computed(() => typeof route.query.college === 'string' ? route.query.college.trim() : '')
const redirect = computed(() => {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return value.startsWith('/') && !value.startsWith('//') ? value : ''
})

async function submit() {
  if (loading.value || !form.phone || !form.password) return
  loading.value = true
  try {
    const payload = registering.value ? { ...form, ...(college.value ? { college: college.value } : {}) } : form
    const result = await (registering.value ? register(payload) : login(payload))
    setAuthSession(result)
    const schoolViewer = String(result.role).toUpperCase() === 'SCHOOL_VIEWER'
    router.replace(schoolViewer ? '/school-statistics' : redirect.value || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  padding: 28px 6vw 48px;
  background: #f4f6f8;
  color: #1c2430;
}

.login-bar {
  width: min(1040px, 100%);
  margin: 0 auto 48px;
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 420px);
  gap: 64px;
  align-items: start;
  width: min(1040px, 100%);
  margin: 0 auto;
}

.login-lead h1,
.login-card h2 {
  margin: 0;
  font-family: "Songti SC", "STSong", "Noto Serif SC", "Source Han Serif SC", "SimSun", serif;
  font-weight: 600;
}

.login-lead h1 {
  font-size: clamp(36px, 4vw, 48px);
  line-height: 1.3;
}

.login-lead p,
.notice {
  color: #5c6b7a;
  font-size: 16px;
  line-height: 1.75;
}

.login-lead p {
  max-width: 420px;
  margin: 16px 0 0;
}

.login-card {
  border: 1px solid #d9dee7;
  border-radius: 6px;
  background: #fff;
  box-shadow: none;
}

.login-card :deep(.el-card__body) {
  padding: 28px 28px 24px;
}

.login-card h2 {
  margin-bottom: 22px;
  font-size: 24px;
}

.login-card :deep(.el-form-item__label) {
  color: #1c2430;
  font-weight: 600;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 0 0 1px #d5dbe3 inset;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1e3a5f inset;
}

.submit {
  width: 100%;
  height: 42px;
  margin-top: 6px;
  border: 0;
  border-radius: 4px;
  background: #1e3a5f;
}

.submit:hover {
  background: #17314f;
}

.switch {
  width: 100%;
  margin-top: 8px;
  padding: 10px 0;
  border: 0;
  color: #1e3a5f;
  background: none;
  cursor: pointer;
}

.notice {
  margin: 8px 0 0;
  padding-top: 14px;
  border-top: 1px solid #e6ebf1;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 800px) {
  .login-page {
    padding-inline: 20px;
  }

  .login-layout {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .login-lead h1 {
    font-size: 34px;
  }
}
</style>
