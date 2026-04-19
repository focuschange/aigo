import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 개발: Vite dev server (5173) 가 /api 요청을 Spring Boot(22001) 로 프록시
// 프로덕션: Spring Boot static resources 로 빌드 산출물 출력
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:22001',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: path.resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: true,
    sourcemap: false,
  },
});
