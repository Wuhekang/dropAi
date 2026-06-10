<template>
  <main class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="wechat-mark">微信</div>
      <h1>微信扫码登录 DropAI</h1>
      <p>仅支持微信登录。扫码后，你的改写记录和处理文档将按微信账号独立保存。</p>
      <el-button
        type="success"
        size="large"
        :loading="loading"
        :disabled="!configured"
        style="width:100%"
        @click="startWechatLogin"
      >
        使用微信扫码登录
      </el-button>
      <el-alert
        v-if="!configured"
        class="config-alert"
        type="warning"
        :closable="false"
        title="微信登录待配置"
        description="管理员需要先配置微信开放平台网站应用的 AppID、AppSecret 和授权回调域。"
      />
    </el-card>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getWechatLoginStatus, getWechatLoginUrl } from '../../api/rewrite'

const route = useRoute()
const router = useRouter()
const configured = ref(false)
const loading = ref(false)

async function startWechatLogin() {
  loading.value = true
  try {
    const result = await getWechatLoginUrl()
    window.location.href = result.url
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (route.query.token) {
    localStorage.setItem('dropai_token', route.query.token)
    localStorage.setItem('dropai_username', route.query.username || '微信用户')
    await router.replace('/rewrite')
    return
  }
  const status = await getWechatLoginStatus()
  configured.value = status.configured
})
</script>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: linear-gradient(135deg,#ecfdf5,#f8fafc); }
.login-card { width: min(440px,100%); text-align: center; }
.wechat-mark { width: 68px; height: 68px; margin: 0 auto 18px; border-radius: 20px; display: grid; place-items: center; color: white; background: #07c160; font-weight: 700; font-size: 20px; }
h1 { margin: 0 0 10px; font-size: 27px; }
p { color: #64748b; line-height: 1.7; margin-bottom: 24px; }
.config-alert { margin-top: 18px; text-align: left; }
</style>
