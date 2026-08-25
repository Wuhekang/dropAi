<template>
  <section class="draw-assistant" :data-stage="stage">
    <div class="visual" aria-live="polite">
      <picture :key="stage" class="character-picture">
        <img class="character" :src="asset('characters/pipeline', 'png')" :alt="`原创绘图助手：${label}`" draggable="false">
      </picture>
      <div class="status-bubble">
        <img :src="statusIcon" alt="" aria-hidden="true">
        <span>{{ statusText || label }}</span>
        <i v-if="isWorking" class="dots" aria-hidden="true"><b></b><b></b><b></b></i>
      </div>
    </div>

    <div class="progress" aria-label="绘图生成进度">
      <span v-for="(item,index) in steps" :key="item" :class="stepClass(index)">{{ item }}</span>
    </div>

    <div class="input-shell">
      <textarea :value="modelValue" :disabled="loading" rows="2" aria-label="绘图需求" placeholder="输入需求，AI助手帮你生成…" @input="$emit('update:modelValue',$event.target.value)" @keydown="onKeydown"></textarea>
      <button v-if="loading" type="button" class="send stop" aria-label="停止生成" @click="$emit('stop')">■</button>
      <button v-else type="button" class="send" :disabled="!canSend" aria-label="发送绘图需求" @click="$emit('send')"><img src="/draw-assistant/icons/ui/send.svg" alt=""></button>
    </div>

    <details class="details">
      <summary><span>详情与快捷操作</span><img src="/draw-assistant/icons/ui/more.svg" alt=""></summary>
      <div class="detail-actions"><button v-for="item in quickCommands" :key="item" :disabled="loading" @click="$emit('quick',item)">{{ item }}</button></div>
      <div class="messages"><p v-for="(message,index) in messages" :key="index" :class="message.role"><b>{{ message.role==='user'?'我':'助手' }}</b>{{ message.text }}</p></div>
      <footer><button v-if="hasUndo" :disabled="loading" @click="$emit('undo')">撤销上次AI修改</button><button :disabled="loading" @click="$emit('clear')">清空对话</button></footer>
    </details>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'

const props=defineProps({stage:{type:String,default:'idle'},statusText:{type:String,default:''},modelValue:{type:String,default:''},loading:Boolean,canSend:Boolean,messages:{type:Array,default:()=>[]},quickCommands:{type:Array,default:()=>[]},hasUndo:Boolean})
const emit=defineEmits(['update:modelValue','send','stop','clear','undo','quick'])
const labels={idle:'等待输入',analyzing:'正在理解需求…',generating:'正在生成代码…',assembling:'正在拼装代码…',rendering:'正在渲染图形…',success:'生成完成',error:'生成失败，请重试'}
const iconNames={idle:'analyze',analyzing:'analyze',generating:'code',assembling:'assemble',rendering:'render',success:'complete',error:'error'}
const steps=['生成代码','拼装代码','渲染图形','生成完成']
const stageIndexes={generating:0,assembling:1,rendering:2,success:3}
const label=computed(()=>labels[props.stage]||labels.idle)
const statusIcon=computed(()=>`/draw-assistant/icons/status/${iconNames[props.stage]||'analyze'}.svg`)
const isWorking=computed(()=>['analyzing','generating','assembling','rendering'].includes(props.stage))
function asset(folder,ext){return `/draw-assistant/${folder}/assistant_${props.stage}.${ext}`}
function stepClass(index){const active=stageIndexes[props.stage];return {active:active===index,done:Number.isInteger(active)&&index<active,error:props.stage==='error'&&index===Math.max(0,active||0)}}
function onKeydown(event){if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();if(props.canSend&&!props.loading)emit('send')}}
onMounted(()=>['idle','analyzing','generating','assembling','rendering','success','error'].forEach(stage=>{const image=new Image();image.src=`/draw-assistant/characters/pipeline/assistant_${stage}.png`}))
</script>

<style scoped>
.draw-assistant{--violet:#8b5cf6;--pink:#f09de8;--blue:#7dd3fc;display:grid;grid-template-rows:minmax(118px,1fr) auto auto auto;gap:8px;height:100%;min-height:0;padding:10px 12px;background:url('/draw-assistant/backgrounds/assistant-card-bg.svg') center/cover;border-radius:0 0 16px 16px;color:#17213a}.visual{position:relative;min-height:118px;overflow:hidden;border-radius:15px;background:linear-gradient(135deg,#ffffff8c,#f5f0ff82)}.character-picture{position:absolute;inset:6px auto 6px 8px;width:46%;display:flex;align-items:center;justify-content:center}.character{display:block;width:100%;height:100%;object-fit:contain;pointer-events:none;user-select:none;filter:drop-shadow(0 10px 16px #5947aa20)}.status-bubble{position:absolute;top:18px;right:14px;display:flex;align-items:center;gap:8px;max-width:55%;min-height:42px;padding:9px 13px;border:1px solid #a78bfa38;border-radius:999px;background:#fffffff0;box-shadow:0 8px 22px #5947aa19;color:var(--violet);font-size:12px;font-weight:700}.status-bubble>img{width:24px;height:24px}.status-bubble>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.dots{display:flex;gap:3px}.dots b{width:4px;height:4px;border-radius:50%;background:#a78bfa}.progress{display:grid;grid-template-columns:repeat(4,minmax(70px,1fr));gap:4px;overflow-x:auto;color:#9a9fba;font-size:11px}.progress span{position:relative;padding:5px 2px 7px;text-align:center;white-space:nowrap}.progress span:after{content:'';position:absolute;right:13%;bottom:0;left:13%;height:2px;border-radius:2px;background:#e6e1f7}.progress .active{color:var(--violet);font-weight:700}.progress .active:after,.progress .done:after{background:linear-gradient(90deg,var(--blue),#a78bfa,var(--pink))}.progress .done{color:#64708f}.progress .error{color:#f27193}.input-shell{display:grid;grid-template-columns:minmax(0,1fr) 42px;align-items:center;gap:8px;padding:7px 8px 7px 14px;border:1px solid #a78bfa42;border-radius:18px;background:#fffffff2;box-shadow:0 8px 22px #5947aa14}.input-shell textarea{min-width:0;resize:none;border:0;outline:0;background:transparent;color:#28324c;font:12px/1.45 'Microsoft YaHei',sans-serif}.send{display:grid;place-items:center;width:42px;height:42px;padding:0;border:0;border-radius:14px;background:linear-gradient(135deg,var(--blue),#a78bfa,var(--pink));box-shadow:0 7px 16px #8b5cf647;color:#fff}.send img{width:42px;height:42px}.send.stop{font-size:15px}.details{min-height:28px}.details summary{display:flex;align-items:center;justify-content:center;gap:6px;color:#7882a0;font-size:11px;cursor:pointer;list-style:none}.details summary img{width:18px;height:18px}.details[open]{overflow:auto;max-height:145px;padding-top:4px}.detail-actions{display:flex;gap:5px;overflow-x:auto;padding:4px 0}.detail-actions button,.details footer button{padding:4px 7px;font-size:10px}.messages{display:grid;gap:5px}.messages p{margin:0;padding:6px 8px;border-radius:8px;background:#ffffffb5;font-size:10px;white-space:pre-wrap}.messages p.user{background:#ede9ffcc}.messages b{display:block;color:var(--violet)}.details footer{display:flex;justify-content:flex-end;gap:5px;margin-top:5px}@media(max-height:800px){.visual{min-height:104px}.draw-assistant{grid-template-rows:minmax(104px,1fr) auto auto auto}.status-bubble{top:10px}}button:disabled{cursor:not-allowed;opacity:.5}
</style>
