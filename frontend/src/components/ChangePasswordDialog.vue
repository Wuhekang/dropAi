<template>
  <el-dialog
    v-model="visible"
    class="change-password-dialog"
    title="修改登录密码"
    width="min(460px, calc(100vw - 28px))"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @closed="resetForm"
  >
    <div class="security-note">
      <span>安全验证</span>
      <p>修改成功后当前登录会失效，请使用新密码重新登录。</p>
    </div>

    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="当前密码">
        <el-input
          v-model="form.currentPassword"
          type="password"
          autocomplete="current-password"
          maxlength="72"
          show-password
          placeholder="请输入当前登录密码"
        />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input
          v-model="form.newPassword"
          type="password"
          autocomplete="new-password"
          maxlength="72"
          show-password
          placeholder="6–72 位，建议包含字母和数字"
        />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          maxlength="72"
          show-password
          placeholder="请再次输入新密码"
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changeMyPassword } from '../api/rewrite'
import { clearAuthSession } from '../utils/authStorage'

const visible = defineModel({ type: Boolean, default: false })
const router = useRouter()
const submitting = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

function resetForm() {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

async function submit() {
  if (submitting.value) return
  if (!form.currentPassword) return ElMessage.warning('请输入当前密码')
  if (form.newPassword.length < 6 || form.newPassword.length > 72) return ElMessage.warning('新密码长度必须为 6–72 位')
  if (form.newPassword === form.currentPassword) return ElMessage.warning('新密码不能与当前密码相同')
  if (form.newPassword !== form.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')

  submitting.value = true
  try {
    await changeMyPassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    clearAuthSession()
    visible.value = false
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    await router.replace('/login')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.security-note {
  margin-bottom: 18px;
  padding: 13px 15px;
  border: 1px solid #e6defb;
  border-radius: 12px;
  background: linear-gradient(135deg, #f5f1ff, #fff5fa);
}

.security-note span {
  color: #6551ce;
  font-size: 12px;
  font-weight: 800;
}

.security-note p {
  margin: 5px 0 0;
  color: #737b8e;
  font-size: 12px;
  line-height: 1.6;
}

:deep(.el-form-item:last-child) { margin-bottom: 0; }
:deep(.el-input__wrapper) { min-height: 42px; }
</style>
