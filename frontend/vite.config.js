import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true,
      }
    }
  },
  build: {
    target: 'es2018',
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            // Maps library — only loaded when Deliveries page is open
            if (id.includes('leaflet') || id.includes('react-leaflet')) {
              return 'maps'
            }
            // Charts library — only loaded when Dashboard is open
            if (id.includes('recharts') || id.includes('d3-') || id.includes('victory')) {
              return 'charts'
            }
            // PDF generation — only loaded when printing a bill
            if (id.includes('html2pdf') || id.includes('jspdf') || id.includes('html2canvas')) {
              return 'pdf-lib'
            }
            // Animations library
            if (id.includes('framer-motion')) {
              return 'motion'
            }
            // React core — always needed
            if (id.includes('react') || id.includes('react-dom') || id.includes('react-router')) {
              return 'vendor'
            }
            // All other node_modules
            return 'vendor-misc'
          }
        }
      }
    }
  }
})

