import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import './style.css'
import App from './App.vue'
import router from './router'

// Element Plus 组件与样式由 vite.config.ts 里的 unplugin-vue-components 按需引入，
// 不再 app.use(ElementPlus) + 全量 dist/index.css（那样无法 tree-shake）。
//
// 但**指令**不会被自动注册：此前表格上写的 v-loading 一直被静默忽略，
// 表现是请求期间表格区一片空白（既不显示加载态，也不显示空态）。
// ElLoading 插件只负责注册 v-loading 指令与 $loading 服务，体积很小。
const app = createApp(App)
app.use(ElLoading)
app.use(createPinia())
app.use(router)
app.mount('#app')
