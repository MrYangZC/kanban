import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'

// Element Plus 组件与样式由 vite.config.ts 里的 unplugin-vue-components 按需引入，
// 不再 app.use(ElementPlus) + 全量 dist/index.css（那样无法 tree-shake）。
const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
