/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
    "./frontend/index.html",
    "./frontend/src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Theme colors matching the premium Material-inspired palette
        primary: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b', // main amber-500
          600: '#d97706',
          700: '#b45309',
          800: '#92400e',
          900: '#78350f',
          950: '#451a03',
        },
        surface: {
          light: '#ffffff',
          dark: '#0f172a', // slate-900 for dark mode background
          cardLight: '#f8fafc',
          cardDark: '#1e293b', // slate-800 for cards in dark mode
          borderLight: '#e2e8f0',
          borderDark: '#334155', // slate-700 for borders in dark mode
        }
      },
    },
  },
  plugins: [],
}
