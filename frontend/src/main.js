import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'

// Remove credentials created by older builds. Login state now belongs only to
// the current tab session and disappears when the page/browser is closed.
localStorage.removeItem('dropai_token')
localStorage.removeItem('dropai_username')

createApp(App)
  .use(router)
  .use(ElementPlus)
  .mount('#app')
