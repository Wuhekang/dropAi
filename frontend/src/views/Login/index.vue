<template>
  <main class="login">
    <button class="brand" type="button" @click="router.push('/')">
      <b>D</b>
      <span>
        <strong>Dokiai Academic</strong>
        <small>学术写作与文档工作台</small>
      </span>
    </button>

    <section class="intro">
      <p class="kicker">学术工作台</p>
      <h1>欢迎使用<br><em>Dokiai Academic</em></h1>
      <p class="lede">面向论文、文献与工程文档的写作工作台。登录后，项目、文稿与积分按账号分别保存。</p>
      <ul>
        <li><b>01</b><strong>学术优化</strong><small>润色表述，整理引用与格式</small></li>
        <li><b>02</b><strong>文档创作</strong><small>文献、大纲、素材与正文</small></li>
        <li><b>03</b><strong>项目存档</strong><small>进度、历史文件与积分</small></li>
      </ul>
      <footer>
        <b>文献</b><i></i><b>大纲</b><i></i><b>素材</b><i></i><b>正文</b><i></i><b>DOCX</b>
      </footer>
    </section>

    <el-card class="card" shadow="never">
      <div class="mark">D</div>
      <p class="kicker">{{ registering ? '创建账号' : '账号登录' }}</p>
      <h2>{{ registering ? '创建 Dokiai 账号' : '登录 Dokiai Academic' }}</h2>
      <p class="lede">使用手机号继续。项目与历史文件只保存在当前账号下。</p>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="中国大陆手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="密码，至少 6 位" />
        </el-form-item>
        <el-button class="submit" type="primary" :loading="loading" @click="submit">
          {{ registering ? '注册并进入工作台' : '登录并进入工作台' }}
        </el-button>
      </el-form>
      <button class="switch" type="button" @click="registering = !registering">
        {{ registering ? '已有账号？直接登录' : '首次使用？创建手机号账号' }}
      </button>
      <div class="notice">
        <b>i</b>
        <span>
          <strong>使用密码登录</strong>
          <small>无需短信验证码。请使用手机号与密码进入工作台。</small>
        </span>
      </div>
    </el-card>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
.login {
  display: grid;
  grid-template-columns: minmax(0, 640px) minmax(320px, 460px);
  align-items: center;
  justify-content: center;
  gap: 72px;
  min-height: 100vh;
  padding: 108px 6vw 72px;
  background:
    radial-gradient(circle at 12% 6%, rgba(74, 144, 255, 0.18), transparent 32rem),
    radial-gradient(circle at 88% 92%, rgba(223, 102, 183, 0.2), transparent 30rem),
    radial-gradient(circle at 70% 8%, rgba(108, 99, 255, 0.16), transparent 26rem),
    linear-gradient(135deg, #fafbff, #f7f4ff 52%, #fff6fb);
  color: #1c2438;
}

.brand {
  position: absolute;
  top: 28px;
  left: 6vw;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}

.brand > b,
.mark {
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(145deg, #4a90ff 0%, #6d5dfb 55%, #df66b7 100%);
  box-shadow: 0 14px 28px rgba(109, 93, 251, 0.28);
  font-weight: 800;
}

.brand > b {
  width: 42px;
  height: 42px;
  font-size: 20px;
}

.brand span,
.notice span {
  display: grid;
}

.brand strong {
  color: #161b33;
  font-size: 16px;
  letter-spacing: -0.02em;
}

.brand small {
  margin-top: 2px;
  color: #7d879b;
  font-size: 12px;
}

.kicker {
  margin: 0;
  color: #5b4cf0;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.intro h1 {
  margin: 14px 0 0;
  color: #161b33;
  font-size: clamp(46px, 4.6vw, 64px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.035em;
}

.intro h1 em {
  font-style: normal;
  font-weight: 700;
  background: linear-gradient(100deg, #3f46c9 0%, #6d4ed4 46%, #b83d84 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.lede {
  max-width: 540px;
  margin: 18px 0 0;
  color: #4c5870;
  font-size: 16px;
  line-height: 1.75;
}

.intro ul {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
}

.intro li {
  display: grid;
  gap: 8px;
  min-height: 132px;
  padding: 16px;
  border: 1px solid rgba(108, 99, 255, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 30px rgba(64, 53, 127, 0.06);
}

.intro li > b {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  color: #5b4cf0;
  background: #efeaff;
  font-size: 12px;
}

.intro li strong {
  font-size: 16px;
}

.intro li small {
  color: #667186;
  font-size: 13px;
  line-height: 1.5;
}

.intro footer {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 540px;
  margin-top: 28px;
  color: #5c6578;
  font-size: 13px;
}

.intro footer i {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, #6c63ff, #df66b7);
}

.card {
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 28px 70px rgba(64, 57, 124, 0.14);
  backdrop-filter: blur(18px);
}

.card :deep(.el-card__body) {
  padding: 28px 26px 24px;
}

.mark {
  width: 52px;
  height: 52px;
  margin-bottom: 16px;
  font-size: 24px;
}

.card h2 {
  margin: 8px 0 0;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.card .lede {
  margin-bottom: 8px;
}

.card :deep(.el-form-item__label) {
  color: #243049;
  font-weight: 600;
}

.card :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 0 0 1px rgba(108, 99, 255, 0.16) inset;
}

.submit {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  border: 0;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, #6c63ff 0%, #ff7eb3 100%);
  box-shadow: 0 14px 28px rgba(108, 99, 255, 0.24);
  font-weight: 700;
}

.switch {
  width: 100%;
  margin-top: 8px;
  padding: 10px;
  border: 0;
  color: #5b4cf0;
  background: none;
  cursor: pointer;
  font-weight: 600;
}

.notice {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(108, 99, 255, 0.08), rgba(255, 126, 179, 0.08));
}

.notice > b {
  display: grid;
  place-items: center;
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #5b4cf0;
  background: #fff;
  font-size: 12px;
}

.notice strong {
  font-size: 13px;
}

.notice small {
  margin-top: 3px;
  color: #667186;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 1050px) {
  .login {
    grid-template-columns: 1fr;
    padding-top: 108px;
  }

  .intro {
    display: none;
  }

  .card {
    width: min(460px, 100%);
    margin: auto;
  }
}

@media (max-width: 520px) {
  .login {
    padding-inline: 18px;
  }

  .brand {
    left: 18px;
  }

  .card :deep(.el-card__body) {
    padding: 20px 16px 16px;
  }
}
</style>
