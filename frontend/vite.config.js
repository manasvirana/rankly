import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // In development, forward API and WebSocket calls to the backend.
    proxy: {
      '/api': { target: 'http://localhost:8080' },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})