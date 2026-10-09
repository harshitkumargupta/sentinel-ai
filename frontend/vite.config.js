import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vitejs.dev/config/
export default defineConfig({
  // Built files go to /static/ — "/assets" is an app route (Assets page), so it must not be a folder.
  // cssCodeSplit:false bundles all CSS into one file loaded in index.html, avoiding the
  // "Unable to preload CSS" crash that fires when Vite 5 tries to preload a lazy chunk's CSS on Railway.
  build: { assetsDir: 'static', cssCodeSplit: false },
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Proxy API calls to the Spring Boot backend during development.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // `vite preview` serves the production build; proxy /api so Lighthouse/manual checks work.
  preview: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    css: false,
    include: ['src/**/*.test.{js,jsx}'],
  },
});
