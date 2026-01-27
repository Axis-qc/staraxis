import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  base: '/webui/', // 添加这一行
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:17890',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://127.0.0.1:17890',
        ws: true,
      },
    },
  },
  build: {
    outDir: '../webui',
    emptyOutDir: true,
  },
})
