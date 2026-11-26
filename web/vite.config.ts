import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 后端地址可由 .env 覆盖（VITE_API_TARGET），避免联调时必须改源码
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:9085'

  return {
    plugins: [
      vue(),
      // 模板里的 el-* 组件按需注册并按需引入样式，替代 main.ts 里全量 app.use(ElementPlus)
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts'
      }),
      // ElMessage / ElMessageBox / ElLoading 等函数式组件与指令的样式按需引入
      ElementPlus({})
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      allowedHosts: true,
      proxy: {
        // 生产由 nginx 把 /kanban-api/ 摘掉再转给后端 /api/；开发环境用同一规则，
        // 否则前端请求 /kanban-api 会 404（本地没有那层 nginx）
        '/kanban-api': {
          target: apiTarget,
          changeOrigin: true,
          rewrite: (p: string) => p.replace(/^\/kanban-api/, '/api')
        },
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      // 生产包去掉调试语句；chunk 过大告警阈值按实际体积调整
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          // 把体积大且更新频率低的依赖拆成独立 chunk，利于长期缓存；
          // 其余模块返回 undefined 交回 Rollup 默认策略，避免人为制造循环 chunk
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return undefined
            if (id.includes('echarts') || id.includes('zrender')) return 'charts'
            if (id.includes('element-plus')) return 'element-plus'
            return undefined
          }
        }
      }
    }
  }
})
