<template>
  <main class="materials-page">
    <DocumentStudioChrome :current="3" :project-name="flow.project_name || 'AI 智能素材库'" :busy="uploading || analyzing || searching" :assistant-items="assistantItems" description="我会识别图片内容、推荐二级标题，并标记仍需人工确认的素材。" />
    <header><span>STEP 3 · MATERIALS</span><h1>AI智能素材库</h1><p>上传项目图片，AI自动识别、命名并匹配对应章节。</p></header>

    <section class="upload-card panel">
      <div class="upload-copy"><i>✦</i><div><small>AI VISION UPLOAD</small><h2>上传项目图片</h2><p>支持 JPG、PNG、WEBP，可一次选择多张图片。上传后将自动进行视觉分析。</p></div></div>
      <el-upload v-model:file-list="imageFiles" drag multiple :auto-upload="false" accept=".jpg,.jpeg,.png,.webp"><strong>拖放图片到这里，或点击选择文件</strong><span>单次支持多图上传 · AI 自动识别</span></el-upload>
      <button class="primary-action" type="button" :disabled="!imageFiles.length || uploading" @click="uploadImages">{{ uploading ? '正在上传并识别…' : `上传并开始 AI 识别${imageFiles.length ? `（${imageFiles.length}张）` : ''}` }}</button>
    </section>

    <section class="library-toolbar panel"><div><small>MATERIAL LIBRARY</small><h2>项目素材</h2><p>AI 推荐仅作为初始匹配，进入写作前请确认图片名称和所属二级标题。</p></div><div class="toolbar-actions"><button v-if="isEnvironment" type="button" :disabled="searching" @click="searchWeb">{{ searching ? 'AI 正在搜索…' : 'AI 搜索分析素材' }}</button><button type="button" :disabled="analyzing || !materials.length" @click="analyze">重新视觉识别</button></div></section>

    <section v-if="isEnvironment" class="web-hints panel"><div><b>环境专业联网素材</b><span>地图</span><span>区位图</span><span>场地分析图</span><span>周边环境图</span></div><p>网络图片仅用于项目确认与文档生成，可查看、替换或重新搜索，不提供单独下载。</p></section>

    <section v-if="materials.length" class="material-grid">
      <article v-for="item in materials" :key="item.id" class="material-card">
        <div class="preview"><img :src="previews[item.id]" :alt="item.display_name"/><span :class="sourceClass(item)">{{ sourceText(item) }}</span><em>{{ confidenceText(item) }}</em></div>
        <div class="card-body">
          <div class="recognition"><span>AI识别类型</span><b>{{ item.ai_category || '待确认素材' }}</b></div>
          <label>图片名称<el-input v-model="item.display_name" maxlength="100" placeholder="请输入图片名称"/></label>
          <label>绑定二级标题<el-select v-model="item.user_confirmed_section" placeholder="选择最终插入位置"><el-option-group v-for="chapter in flow.chapters" :key="chapter.id" :label="`第 ${chapter.chapter_no} 章 ${cleanTitle(chapter.title)}`"><el-option v-for="section in chapter.sections" :key="section.id" :label="`${section.section_no} ${cleanTitle(section.title)}`" :value="section.id"/></el-option-group></el-select></label>
          <p class="recommendation">AI推荐：{{ sectionLabel(item.ai_suggested_section) }}</p>
          <div class="card-status"><span :class="{confirmed:Boolean(item.is_confirmed)}">{{ item.is_confirmed ? '已确认' : '待确认' }}</span><small>{{ item.analysis_status || 'AI 已识别' }}</small></div>
          <div class="card-actions"><button type="button" @click="save(item)">{{ item.is_confirmed ? '保存修改' : '确认名称与章节' }}</button><button v-if="isWeb(item)" type="button" @click="view(item)">查看</button><button v-if="isWeb(item)" type="button" :disabled="searching" @click="replace(item)">替换</button><button class="delete" type="button" @click="remove(item)">删除</button></div>
        </div>
      </article>
    </section>
    <section v-else class="empty-library panel"><div>◇</div><h3>素材库还是空的</h3><p>上传设计图片，或使用 AI 搜索环境分析素材。</p></section>

    <footer class="materials-footer panel"><div><b>{{ pendingCount ? `还有 ${pendingCount} 张素材待确认` : '素材确认完成' }}</b><span>已确认 {{ confirmedCount }} / {{ materials.length }} 张素材</span></div><button class="primary-action" type="button" :disabled="pendingCount>0" @click="finish">进入 AI 写作 →</button></footer>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import DocumentStudioChrome from '../../components/DocumentStudioChrome.vue'
import { analyzeWritingMaterials, confirmWritingOutline, deleteWritingMaterial, getWritingMaterialContent, getWritingV2Flow, searchWritingWebImages, updateWritingMaterial, uploadWritingMaterials } from '../../api/rewrite'

const router=useRouter();const projectId=sessionStorage.getItem('dropai_writing_project_id')||''
const flow=ref({chapters:[],materials:[]});const imageFiles=ref([]);const previews=reactive({});const uploading=ref(false);const analyzing=ref(false);const searching=ref(false)
const materials=computed(()=>flow.value.materials||[]);const isEnvironment=computed(()=>flow.value.document_mode==='environment');const confirmedCount=computed(()=>materials.value.filter(item=>Boolean(item.is_confirmed)).length);const pendingCount=computed(()=>materials.value.length-confirmedCount.value)
const assistantItems=computed(()=>[{label:'素材数量',value:`${materials.value.length} 张`},{label:'已识别',value:`${materials.value.filter(item=>item.analysis_status!=='PENDING').length} 张`},{label:'待确认',value:`${pendingCount.value} 张`},{label:'来源',value:isEnvironment.value?'上传 + 联网':'用户上传'}])
async function load(){if(!projectId){ElMessage.warning('请先完成结构设计');return router.push('/writing-generator')}flow.value=await getWritingV2Flow(projectId);await refreshPreviews()}
async function refreshPreviews(){await Promise.all(materials.value.map(async item=>{if(previews[item.id])return;try{const blob=await getWritingMaterialContent(projectId,item.id);previews[item.id]=URL.createObjectURL(blob)}catch{previews[item.id]=''}}))}
async function uploadImages(){uploading.value=true;try{await uploadWritingMaterials(projectId,imageFiles.value);imageFiles.value=[];flow.value=await analyzeWritingMaterials(projectId);await refreshPreviews();ElMessage.success('图片已上传，AI 视觉识别完成')}finally{uploading.value=false}}
async function analyze(){analyzing.value=true;try{flow.value=await analyzeWritingMaterials(projectId);await refreshPreviews();ElMessage.success('素材识别结果已更新')}finally{analyzing.value=false}}
async function searchWeb(){searching.value=true;try{flow.value=await searchWritingWebImages(projectId);await refreshPreviews();ElMessage.success('联网素材搜索已完成，请确认候选图片')}finally{searching.value=false}}
async function save(item){if(!item.display_name?.trim())return ElMessage.warning('请填写图片名称');if(!item.user_confirmed_section)return ElMessage.warning('请选择所属二级标题');const section=allSections().find(value=>value.id===item.user_confirmed_section);const updated=await updateWritingMaterial(projectId,item.id,{displayName:item.display_name,userConfirmedChapter:section?.chapter_id||'',userConfirmedSection:item.user_confirmed_section,displayOrder:item.display_order||0});flow.value.materials=Array.isArray(updated)?updated:flow.value.materials;item.is_confirmed=1;ElMessage.success('素材名称与章节已确认')}
async function remove(item){await deleteWritingMaterial(projectId,item.id);if(previews[item.id])URL.revokeObjectURL(previews[item.id]);delete previews[item.id];await load();ElMessage.success('素材已删除')}
async function replace(item){await remove(item);await searchWeb()}
function view(item){if(previews[item.id])window.open(previews[item.id],'_blank','noopener,noreferrer')}
async function finish(){try{await confirmWritingOutline(projectId);sessionStorage.setItem('dropai_writing_outline_confirmed','true');sessionStorage.setItem('dropai_writing_v2_ready','true');router.push('/writing-generator/generate')}catch(error){ElMessage.warning(error?.message||'请先确认全部素材') }}
function allSections(){return (flow.value.chapters||[]).flatMap(chapter=>chapter.sections||[])}
function sectionLabel(id){const section=allSections().find(item=>item.id===id);return section?`${section.section_no} ${cleanTitle(section.title)}`:'暂未推荐'}
function cleanTitle(value){return String(value||'').replace(/^\d+(\.\d+)?\s*/,'').replace(/^第[一二三四五六七八九十\d]+章\s*/,'')}
function isWeb(item){return String(item.source_type||'').toUpperCase()==='WEB_SEARCH'}
function sourceText(item){return isWeb(item)?'联网搜索':'用户上传'}function sourceClass(item){return isWeb(item)?'web':'upload'}
function confidenceText(item){const value=Number(item.confidence||item.ai_confidence||0);return value>0?`置信度 ${Math.round(value<=1?value*100:value)}%`:(item.analysis_status==='PENDING'?'待识别':'AI 已识别')}
onMounted(load);onUnmounted(()=>Object.values(previews).forEach(value=>value&&URL.revokeObjectURL(value)))
</script>

<style scoped>
.materials-page{min-height:100vh;padding:0 300px 55px 260px;background:linear-gradient(45deg,#fbd7ea 0%,#f8eaf0 38%,#edf1f8 64%,#dcebff 100%);color:#252936}.materials-page>header{max-width:1500px;margin:0 auto;padding:38px 0 22px;text-align:center}.materials-page>header span,.library-toolbar small{color:#6e4fff;font-size:10px;font-weight:800;letter-spacing:.16em}.materials-page>header h1{margin:8px 0 5px;font-size:40px}.materials-page>header p{margin:0;color:#747989;font-size:16px}.panel{max-width:1500px;margin:16px auto;padding:22px;border:1px solid rgba(110,79,255,.09);border-radius:18px;background:rgba(255,255,255,.88);box-shadow:0 14px 42px rgba(61,53,104,.07)}.upload-card{display:grid;grid-template-columns:1fr minmax(360px,1.2fr) auto;align-items:center;gap:22px}.upload-copy{display:flex;gap:14px}.upload-copy>i{display:grid;place-items:center;width:44px;height:44px;border-radius:13px;background:linear-gradient(135deg,#6e4fff,#ff55b0);color:#fff;font-style:normal}.upload-copy small{color:#6e4fff;font-size:9px;letter-spacing:.14em}.upload-copy h2{margin:5px 0}.upload-copy p,.library-toolbar p,.web-hints p{margin:0;color:#747989;font-size:12px;line-height:1.55}.upload-card :deep(.el-upload-dragger){padding:20px;border-color:#cfc4ff;background:#fbfaff}.upload-card :deep(.el-upload-dragger strong),.upload-card :deep(.el-upload-dragger span){display:block}.upload-card :deep(.el-upload-dragger span){margin-top:5px;color:#9296a3;font-size:11px}.primary-action{padding:12px 18px;border:0;border-radius:10px;background:linear-gradient(135deg,#6e4fff,#ff55b0);color:#fff;font-weight:700;box-shadow:0 8px 22px rgba(110,79,255,.18)}.primary-action:disabled{opacity:.45}.library-toolbar{display:flex;align-items:center;justify-content:space-between}.library-toolbar h2{margin:5px 0}.toolbar-actions{display:flex;gap:9px}.toolbar-actions button,.card-actions button{padding:8px 11px;border:1px solid #bbaaff;border-radius:8px;background:#fff;color:#6e4fff}.web-hints{display:flex;align-items:center;justify-content:space-between;padding-top:15px;padding-bottom:15px}.web-hints>div{display:flex;align-items:center;gap:9px}.web-hints span{padding:5px 9px;border-radius:99px;background:#f1edff;color:#6e4fff;font-size:11px}.material-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px;max-width:1500px;margin:16px auto}.material-card{display:grid;grid-template-columns:210px 1fr;overflow:hidden;border:1px solid rgba(110,79,255,.1);border-radius:17px;background:#fff;box-shadow:0 12px 34px rgba(61,53,104,.07)}.preview{position:relative;min-height:280px;background:#f3f3f7}.preview img{width:100%;height:100%;object-fit:cover}.preview>span,.preview>em{position:absolute;top:12px;padding:5px 8px;border-radius:99px;font-size:9px;font-style:normal}.preview>span{left:12px;background:#fff;color:#6e4fff}.preview>span.web{color:#ad4f83}.preview>em{right:12px;background:rgba(37,41,54,.74);color:#fff}.card-body{display:grid;align-content:start;gap:10px;padding:17px}.recognition{display:flex;align-items:center;justify-content:space-between}.recognition span,.card-body label{color:#767a88;font-size:10px}.recognition b{color:#5f49bd;font-size:12px}.card-body label{display:grid;gap:5px}.recommendation{margin:0;color:#777b89;font-size:10px}.card-status{display:flex;align-items:center;gap:8px}.card-status span{padding:4px 8px;border-radius:99px;background:#fff3e2;color:#a86b18;font-size:9px}.card-status span.confirmed{background:#eaf5f1;color:#145b4d}.card-status small{color:#969aa5}.card-actions{display:flex;flex-wrap:wrap;gap:6px}.card-actions .delete{margin-left:auto;border-color:#f1c8d3;color:#c04e68}.empty-library{text-align:center}.empty-library>div{color:#b6a9ec;font-size:46px}.empty-library h3{margin:4px}.empty-library p{color:#858997}.materials-footer{position:sticky;bottom:14px;display:flex;align-items:center;justify-content:space-between;z-index:5}.materials-footer>div{display:grid;gap:4px}.materials-footer span{color:#858997;font-size:11px}@media(max-width:1500px){.materials-page{padding-right:40px}.upload-card{grid-template-columns:1fr 1fr}.upload-card>.primary-action{grid-column:1/-1}.material-grid{grid-template-columns:1fr}}@media(max-width:900px){.materials-page{padding:0 12px 35px}.upload-card,.material-card{grid-template-columns:1fr}.preview{height:240px}.library-toolbar,.web-hints,.materials-footer{align-items:stretch;flex-direction:column;gap:14px}.web-hints>div{flex-wrap:wrap}}
</style>
