import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 相对路径：产物由 App 内嵌服务器从任意路径托管
  base: './',
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 600,
  },
})
