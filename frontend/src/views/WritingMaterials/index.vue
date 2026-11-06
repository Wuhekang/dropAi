<template>
  <main class="flow-page">
    <nav><button class="brand" @click="router.push('/dashboard')">D <span>Dokiai Academic</span></button><WritingV2Steps :current="2"/><button class="ghost" @click="router.push('/writing-generator')">返回文献库</button></nav>
    <header><span>STEP 2 · PROJECT MATERIALS</span><h1>上传项目图片素材</h1><p>请一次性上传本项目后续文档需要的全部图片。支持预览、删除和修改名称，图片将保存在当前项目下。</p></header>
    <section class="panel upload-panel">
      <el-upload v-model:file-list="pendingFiles" drag multiple accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" :auto-upload="false" :show-file-list="true">
        <div class="upload-icon">＋</div><strong>拖放图片到这里，或点击选择</strong><p>单张不超过 15MB，可同时选择多张</p>
      </el-upload>
      <el-button type="primary" size="large" :disabled="!pendingFiles.length" :loading="uploading" @click="upload">上传所选图片</el-button>
    </section>
    <section class="panel">
      <div class="head"><div><small>已上传素材</small><h2>项目图片（{{ materials.length }}）</h2></div><span>后续将精确绑定到二级标题</span></div>
      <div v-if="materials.length" class="grid">
        <article v-for="item in materials" :key="item.id">
          <img :src="previews[item.id]" :alt="item.display_name" />
          <el-input v-model="item.display_name" maxlength="100" @change="rename(item)" />
          <small>{{ item.original_name }}</small>
          <el-button text type="danger" @click="remove(item)">删除图片</el-button>
        </article>
      </div>
      <el-empty v-else description="尚未上传图片；普通纯文字稿也可以不上传素材" />
    </section>
    <footer class="panel"><div><strong>图片可稍后继续补充</strong><span>设计专业建议先上传平面图、效果图、展板图等全部成果图。</span></div><el-button type="primary" size="large" @click="next">进入下一步：生成提纲 →</el-button></footer>
  </main>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import WritingV2Steps from '../../components/WritingV2Steps.vue'
import { deleteWritingMaterial, getWritingMaterialContent, getWritingV2Flow, updateWritingMaterial, uploadWritingMaterials } from '../../api/rewrite'
const router=useRouter(); const projectId=sessionStorage.getItem('dropai_writing_project_id')||''; const pendingFiles=ref([]); const materials=ref([]); const previews=reactive({}); const uploading=ref(false)
async function load(){if(!projectId){ElMessage.warning('请先完成文献搜索');return router.push('/writing-generator')}const flow=await getWritingV2Flow(projectId);materials.value=flow.materials||[];await Promise.all(materials.value.map(loadPreview))}
async function loadPreview(item){if(previews[item.id])URL.revokeObjectURL(previews[item.id]);const blob=await getWritingMaterialContent(projectId,item.id);previews[item.id]=URL.createObjectURL(blob)}
async function upload(){uploading.value=true;try{materials.value=await uploadWritingMaterials(projectId,pendingFiles.value);pendingFiles.value=[];await Promise.all(materials.value.map(loadPreview));ElMessage.success('图片素材已上传')}finally{uploading.value=false}}
async function rename(item){materials.value=await updateWritingMaterial(projectId,item.id,{displayName:item.display_name,userConfirmedChapter:item.user_confirmed_chapter,userConfirmedSection:item.user_confirmed_section});ElMessage.success('图片名称已保存')}
async function remove(item){await ElMessageBox.confirm(`确认删除“${item.display_name}”吗？`,'删除图片',{type:'warning'});materials.value=await deleteWritingMaterial(projectId,item.id);if(previews[item.id]){URL.revokeObjectURL(previews[item.id]);delete previews[item.id]}}
function next(){router.push('/writing-generator/outline')}
onMounted(load);onUnmounted(()=>Object.values(previews).forEach(URL.revokeObjectURL))
</script>

<style scoped>
.flow-page{min-height:100vh;padding:0 5vw 50px;background:#f4f7f6;color:#17211e}.flow-page>nav{min-height:68px;display:grid;grid-template-columns:180px 1fr 180px;align-items:center;border-bottom:1px solid #dce4e1}.brand,.ghost{border:0;background:transparent;cursor:pointer}.brand{justify-self:start;width:36px;height:36px;color:#fff;background:#176b57}.brand span{position:absolute;margin-left:14px;color:#17211e;font-weight:700}.ghost{justify-self:end;color:#176b57}.flow-page>header{max-width:760px;margin:45px auto 28px;text-align:center}.flow-page>header span,.head small{color:#176b57;font-size:12px;letter-spacing:.12em}.flow-page h1{margin:8px;font-size:38px}.flow-page header p,.head span,footer span{color:#687772}.panel{max-width:1100px;margin:16px auto;padding:24px;border:1px solid #dce4e1;background:#fff}.upload-panel{display:grid;gap:18px}.upload-icon{font-size:40px;color:#176b57}.head,footer{display:flex;align-items:center;justify-content:space-between;gap:20px}.head h2{margin:5px 0}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:16px;margin-top:20px}.grid article{display:grid;gap:8px;padding:10px;border:1px solid #e1e7e5}.grid img{width:100%;height:150px;object-fit:cover;background:#eef2f1}.grid small{overflow:hidden;color:#89928f;text-overflow:ellipsis;white-space:nowrap}footer div{display:grid;gap:5px}@media(max-width:800px){.flow-page{padding:0 12px 30px}.flow-page>nav{grid-template-columns:60px 1fr}.flow-page>nav>.ghost{display:none}.head,footer{align-items:stretch;flex-direction:column}}
</style>
