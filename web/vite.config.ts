import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // 自动将服务器暴露到局域网
    host: true, // 等同于 --host
    port: 5173, // 明确指定前端开发服务器端口
    allowedHosts: ['frp-put.com'],
    proxy: {
      // 配置 API 请求代理
      // 将所有发往 /api 的请求转发到 http://127.0.0.1:17890
      '/api': {
        target: 'http://127.0.0.1:17890',
        changeOrigin: true, // 必须设置为 true，以正确处理跨域
        // 如果您的后端 API 路径不包含 /api，可以使用 rewrite
        // rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // 如果有 WebSocket 连接，也需要代理
      '/ws': {
        target: 'ws://127.0.0.1:17890',
        ws: true,
      },
      '/assets': {
        target: 'http://127.0.0.1:17890',
        changeOrigin: true,
      },
    },
  },
  // 定义 webui 为基础路径
  base: '/webui/',
  build: {
    // 定义构建输出目录
    outDir: '../webui',
    emptyOutDir: true,
  },
})
