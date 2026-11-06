<template>
  <main class="outline-page">
    <DocumentStudioChrome :current="2" :project-name="project.title || '纯文字稿大纲'" :busy="busy"
      description="这是全文生成前唯一一次确认。可修改题目、一级至三级标题以及原生图表和表格数量。" />
    <header class="page-head">
      <div><span>PURE TEXT PAPER · OUTLINE</span><h1>纯文字稿大纲确认</h1><p>确认后 Doki 将按每个二级标题并行生成、补写并组装完整 Word。300 积分 / 篇，生成成功后扣费。</p></div>
      <div class="metrics"><b>{{ chapters.length }}<small>章</small></b><b>{{ secondCount }}<small>二级标题</small></b><b>{{ thirdCount }}<small>三级标题</small></b><b>{{ chartCount }}<small>原生图表</small></b></div>
    </header>

    <section class="title-card">
      <label>论文题目</label>
      <el-input v-model="project.title" maxlength="200" show-word-limit @change="saveTitle" />
      <span>默认约 8000 字 · 正文不足 6000 字自动补写 · 上限 9000 字</span>
    </section>

    <section class="workspace">
      <aside class="tree panel">
        <div class="panel-head"><div><small>CHAPTER TREE</small><h2>章节目录</h2></div><button @click="addChapterRow">＋章</button></div>
        <article v-for="(chapter,chapterIndex) in chapters" :key="chapter.id">
          <div class="chapter-row">
            <button class="toggle" @click="toggle(chapter.id)">{{ expanded[chapter.id] ? '−' : '+' }}</button>
            <button class="chapter-title" @click="selectChapter(chapter)"><b>第{{ chineseNo(chapter.chapter_no) }}章</b><span>{{ chapter.title }}</span></button>
            <button @click="moveChapter(chapterIndex,-1)">↑</button><button @click="moveChapter(chapterIndex,1)">↓</button>
          </div>
          <div v-show="expanded[chapter.id]" class="section-tree">
            <template v-for="section in chapter.sections" :key="section.id">
              <button :class="{active:selected?.id===section.id}" @click="select(section,chapter)"><b>{{ section.section_no }}</b><span>{{ section.title }}</span></button>
              <button v-for="child in section.children || []" :key="child.id" class="third" :class="{active:selected?.id===child.id}" @click="select(child,chapter)"><b>{{ child.section_no }}</b><span>{{ child.title }}</span></button>
            </template>
            <button class="add-row" @click="addSecond(chapter)">＋新增二级标题</button>
          </div>
        </article>
      </aside>

      <section class="editor panel" v-if="selected">
        <div class="panel-head"><div><small>{{ Number(selected.heading_level || 2) === 3 ? 'THIRD LEVEL' : 'SECOND LEVEL' }}</small><h2>{{ selected.section_no }} 标题设置</h2></div><span class="autosave">自动保存</span></div>
        <label>标题名称<el-input v-model="selected.title" maxlength="160" @change="saveSelected" /></label>
        <div class="row-actions"><button @click="moveSelected(-1)">↑ 上移</button><button @click="moveSelected(1)">↓ 下移</button><button class="danger" @click="removeSelected">删除标题</button></div>

        <template v-if="Number(selected.heading_level || 2) === 2">
          <div class="third-editor">
            <div><h3>三级标题</h3><p>允许为0个；添加后至少保留2个。第三、第四章可以自动生成，其他章节由用户手动添加。</p></div>
            <button @click="addThird">＋添加三级标题</button>
          </div>
          <div v-if="selected.children?.length" class="children-list">
            <article v-for="child in selected.children" :key="child.id"><b>{{ child.section_no }}</b><el-input v-model="child.title" @change="saveNode(child)"/><button class="danger" @click="removeNode(child)">删除</button></article>
          </div>

          <h3 class="visual-title">本节图表位置与数量</h3>
          <div class="visual-counter">
            <article><span>原生可编辑图表</span><div><button @click="removeLastChart">−</button><b>{{ selectedCharts.length }}</b><button @click="addChartRow">＋</button></div></article>
            <article><span>Word 原生表格</span><div><button @click="removeLastTable">−</button><b>{{ selectedTables.length }}</b><button @click="addTableRow">＋</button></div></article>
          </div>
          <div class="asset-list">
            <article v-for="item in selectedCharts" :key="item.id"><i>图{{ item.chart_no }}</i><el-input v-model="item.title" @change="saveChart(item)"/><el-select v-model="item.chart_type" @change="saveChart(item)"><el-option label="柱状图" value="BAR"/><el-option label="折线图" value="LINE"/><el-option label="饼图" value="PIE"/></el-select></article>
            <article v-for="item in selectedTables" :key="item.id"><i>表{{ item.table_no }}</i><el-input v-model="item.title" @change="saveTable(item)"/></article>
          </div>
        </template>
      </section>

      <aside class="summary panel">
        <small>GENERATION RULES</small><h2>本稿配置</h2>
        <ul><li>所有二级标题同时启动独立豆包分支</li><li>不联网检索文献，不生成引用标号</li><li>原生图表保留嵌入数据，可在 Word 编辑</li><li>三级标题数量只能为0或至少2</li><li>自动目录收录一级至三级标题</li></ul>
        <div class="baseline"><b>抖音例文模式</b><span>六章论证结构</span><span>研究背景 → 理论框架 → 问题识别 → 优化方案 → 实施验证 → 结论</span></div>
      </aside>
    </section>

    <footer class="confirm-bar"><div><b>仅需确认这一次</b><span>点击后自动完成正文、原生图表、目录、字数补写与 DOCX 质检。</span></div><button class="primary-action" :disabled="busy" @click="generateNow">{{ busy ? '正在创建生成任务…' : '立即生成全文' }}</button></footer>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import DocumentStudioChrome from '../../components/DocumentStudioChrome.vue'
import { addWritingChapter, addWritingChart, addWritingSection, addWritingTable, deleteWritingChart, deleteWritingSection, deleteWritingTable, getWritingProject, reorderWritingChapters, reorderWritingSections, startWritingGeneration, updateWritingChart, updateWritingProject, updateWritingSection, updateWritingTable } from '../../api/rewrite'

const router=useRouter(),projectId=sessionStorage.getItem('dropai_writing_project_id')||localStorage.getItem('dropai_writing_project_id')||''
const project=ref({chapters:[]}),selected=ref(null),selectedChapter=ref(null),busy=ref(false),expanded=reactive({})
const chapters=computed(()=>project.value.chapters||[])
const secondCount=computed(()=>chapters.value.reduce((n,c)=>n+(c.sections?.length||0),0))
const thirdCount=computed(()=>chapters.value.reduce((n,c)=>n+(c.sections||[]).reduce((m,s)=>m+(s.children?.length||0),0),0))
const chartCount=computed(()=>chapters.value.reduce((n,c)=>n+(c.charts?.length||0),0))
const selectedCharts=computed(()=>selectedChapter.value?.charts?.filter(x=>x.insert_after_section===selected.value?.id)||[])
const selectedTables=computed(()=>selectedChapter.value?.tables?.filter(x=>x.insert_after_section===selected.value?.id)||[])

async function load(keepId){project.value=await getWritingProject(projectId);chapters.value.forEach(c=>expanded[c.id]=expanded[c.id]!==false);if(keepId){for(const c of chapters.value){for(const s of c.sections||[]){if(s.id===keepId){select(s,c);return}const child=(s.children||[]).find(x=>x.id===keepId);if(child){select(child,c);return}}}}if(!selected.value&&chapters.value[0]?.sections?.[0])select(chapters.value[0].sections[0],chapters.value[0])}
function select(node,chapter){selected.value=node;selectedChapter.value=chapter}
function selectChapter(chapter){expanded[chapter.id]=true;if(chapter.sections?.[0])select(chapter.sections[0],chapter)}
function toggle(id){expanded[id]=!expanded[id]}
function chineseNo(n){return ['零','一','二','三','四','五','六','七','八','九','十'][Number(n)]||n}
async function saveTitle(){await updateWritingProject(projectId,{title:project.value.title,targetWordCount:8000,skipReferences:true,documentType:'纯文字稿',writingTone:'本科论文'});sessionStorage.setItem('writing_project_title',project.value.title);localStorage.setItem('writing_project_title',project.value.title);ElMessage.success('题目已保存')}
async function saveNode(node){await updateWritingSection(projectId,node.id,{title:node.title,targetWordCount:node.target_word_count||400})}
async function saveSelected(){await saveNode(selected.value);await load(selected.value.id)}
async function addChapterRow(){project.value=await addWritingChapter(projectId,{title:'新增章节',sectionCount:2,imageCount:0,tableCount:0,targetWordCount:900});await load()}
async function addSecond(chapter){project.value=await addWritingSection(projectId,chapter.id,{title:'新增二级标题',targetWordCount:520});await load()}
async function addThird(){project.value=await addWritingSection(projectId,selectedChapter.value.id,{parentSectionId:selected.value.id,title:'新增三级标题',targetWordCount:280});await load(selected.value.id)}
async function removeNode(node){try{project.value=await deleteWritingSection(projectId,node.id);await load(selected.value?.id)}catch(e){ElMessage.warning(e?.responseData?.message||e.message)}}
async function removeSelected(){const id=selected.value.id;project.value=await deleteWritingSection(projectId,id);selected.value=null;await load()}
async function moveChapter(index,offset){const next=index+offset;if(next<0||next>=chapters.value.length)return;const ids=chapters.value.map(x=>x.id);[ids[index],ids[next]]=[ids[next],ids[index]];project.value=await reorderWritingChapters(projectId,ids);await load(selected.value?.id)}
async function moveSelected(offset){const list=Number(selected.value.heading_level||2)===3?(selectedChapter.value.sections.find(s=>s.id===selected.value.parent_section_id)?.children||[]):selectedChapter.value.sections;const i=list.findIndex(x=>x.id===selected.value.id),n=i+offset;if(i<0||n<0||n>=list.length)return;const ids=list.map(x=>x.id);[ids[i],ids[n]]=[ids[n],ids[i]];project.value=await reorderWritingSections(projectId,ids);await load(selected.value.id)}
async function addChartRow(){project.value=await addWritingChart(projectId,selectedChapter.value.id,{title:'已核验研究资料统计图',chartType:'BAR',insertAfterSection:selected.value.id});await load(selected.value.id)}
async function addTableRow(){project.value=await addWritingTable(projectId,selectedChapter.value.id,{title:'研究资料与分析依据',insertAfterSection:selected.value.id});await load(selected.value.id)}
async function removeLastChart(){const item=selectedCharts.value.at(-1);if(!item)return;project.value=await deleteWritingChart(projectId,item.id);await load(selected.value.id)}
async function removeLastTable(){const item=selectedTables.value.at(-1);if(!item)return;project.value=await deleteWritingTable(projectId,item.id);await load(selected.value.id)}
async function saveChart(item){project.value=await updateWritingChart(projectId,item.id,{...item,chartType:item.chart_type,title:item.title,sourceType:'REFERENCE_METADATA',isSimulated:false,insertAfterSection:selected.value.id});await load(selected.value.id)}
async function saveTable(item){project.value=await updateWritingTable(projectId,item.id,{...item,title:item.title,tableType:item.table_type,sourceType:'REFERENCE_METADATA',isSimulated:false,insertAfterSection:selected.value.id,useThreeLineStyle:true});await load(selected.value.id)}
async function generateNow(){busy.value=true;try{await saveTitle();await startWritingGeneration(projectId);router.push('/writing-generator/generate')}finally{busy.value=false}}
onMounted(()=>{if(!projectId){ElMessage.warning('请先输入论文题目');return router.push('/writing-generator')}load()})
</script>

<style scoped>
*{box-sizing:border-box}.outline-page{min-height:100vh;padding:32px 318px 110px 270px;background:linear-gradient(145deg,#fbf8ff,#edf5ff);color:#252936}.page-head{display:flex;align-items:end;justify-content:space-between;max-width:1500px;margin:auto}.page-head span,.panel small{color:#6e4fff;font-size:10px;font-weight:900;letter-spacing:.15em}.page-head h1{margin:7px 0;font-size:38px}.page-head p{margin:0;color:#7f8290}.metrics{display:flex;gap:10px}.metrics b{min-width:76px;padding:12px;border-radius:13px;background:#fff;text-align:center;box-shadow:0 8px 28px #4b3d7f0e}.metrics small{display:block;margin-top:4px;color:#999ca7;font-size:9px}.title-card,.panel{border:1px solid rgba(110,79,255,.1);border-radius:18px;background:rgba(255,255,255,.92);box-shadow:0 14px 42px rgba(61,53,104,.07)}.title-card{display:grid;grid-template-columns:85px 1fr auto;align-items:center;gap:14px;max-width:1500px;margin:22px auto 16px;padding:17px 20px}.title-card label{font-weight:900}.title-card span{color:#8d909d;font-size:10px}.workspace{display:grid;grid-template-columns:310px minmax(520px,1fr) 270px;gap:15px;max-width:1500px;margin:auto}.panel{padding:19px}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.panel-head h2{margin:4px 0 0;font-size:20px}.panel-head button,.tree button,.editor button{border:1px solid #d9d2f3;border-radius:8px;background:#fff;color:#6349c4}.tree article{margin-bottom:6px}.chapter-row{display:grid;grid-template-columns:26px 1fr 27px 27px;gap:4px}.chapter-title{display:grid!important;grid-template-columns:auto 1fr!important;gap:7px!important;padding:10px!important;text-align:left!important}.chapter-title span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.section-tree{margin:5px 0 12px 18px;padding-left:10px;border-left:1px solid #ded8f2}.section-tree>button{display:grid;width:100%;grid-template-columns:48px 1fr;gap:6px;margin:3px 0;padding:8px;border:0;background:transparent;text-align:left}.section-tree>button.active{background:#eee9ff;color:#6549d2}.section-tree>button.third{padding-left:20px;color:#6f7180}.section-tree .add-row{display:block;border:1px dashed #cfc5f2;text-align:center;color:#6e4fff}.editor{min-height:600px}.editor>label{display:grid;gap:7px;font-size:12px;font-weight:800}.autosave{padding:5px 9px;border-radius:99px;background:#eaf7f1!important;color:#187656!important;letter-spacing:0!important}.row-actions{display:flex;gap:7px;margin:12px 0 22px}.row-actions button,.third-editor button,.children-list button{padding:7px 10px}.danger{border-color:#f3c8d0!important;color:#c3485c!important}.third-editor{display:flex;align-items:center;justify-content:space-between;padding:15px;border-radius:13px;background:#faf9ff}.third-editor h3{margin:0 0 5px}.third-editor p{margin:0;color:#8c8f9c;font-size:10px}.children-list{display:grid;gap:7px;margin-top:10px}.children-list article{display:grid;grid-template-columns:60px 1fr auto;align-items:center;gap:7px}.visual-title{margin:25px 0 10px}.visual-counter{display:grid;grid-template-columns:1fr 1fr;gap:10px}.visual-counter article{display:flex;align-items:center;justify-content:space-between;padding:14px;border:1px solid #ebe7f5;border-radius:12px}.visual-counter article>div{display:flex;align-items:center;gap:8px}.visual-counter button{width:28px;height:28px}.asset-list{display:grid;gap:8px;margin-top:12px}.asset-list article{display:grid;grid-template-columns:68px 1fr 110px;align-items:center;gap:8px}.asset-list i{font-size:10px;font-style:normal;font-weight:900}.summary ul{display:grid;gap:12px;padding-left:18px;color:#656978;font-size:11px;line-height:1.5}.baseline{display:grid;gap:7px;margin-top:18px;padding:14px;border-radius:12px;background:#f3efff}.baseline span{color:#7f8290;font-size:10px;line-height:1.5}.confirm-bar{position:fixed;z-index:5;right:318px;bottom:18px;left:270px;display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border:1px solid #ded7f4;border-radius:16px;background:rgba(255,255,255,.95);box-shadow:0 18px 55px #3d35681c}.confirm-bar div{display:grid;gap:4px}.confirm-bar span{color:#8a8d99;font-size:10px}.primary-action{padding:13px 24px;border:0;border-radius:11px;background:linear-gradient(135deg,#6e4fff,#ad58e9);color:#fff;font-weight:900}@media(max-width:1450px){.outline-page{padding-right:35px}.confirm-bar{right:35px}.workspace{grid-template-columns:280px 1fr}.summary{grid-column:1/-1}}@media(max-width:900px){.outline-page{padding:20px 12px 130px}.page-head,.title-card,.confirm-bar{position:static;display:grid;grid-template-columns:1fr;gap:12px}.workspace{grid-template-columns:1fr}.metrics{flex-wrap:wrap}.confirm-bar{margin-top:15px}.visual-counter{grid-template-columns:1fr}}
</style>
