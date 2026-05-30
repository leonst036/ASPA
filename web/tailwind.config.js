/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f5f7fa',
          100: '#eaeef4',
          200: '#d0dbe6',
          300: '#a7bdd2',
          400: '#7799b8',
          500: '#557a9b',
          600: '#42617f',
          700: '#364e67',
          800: '#2f4356',
          900: '#2a3a49',
          950: '#1c2632',
        },
        dark: {
          50: '#f6f6f7',
          100: '#e3e3e5',
          200: '#c7c7cc',
          300: '#a3a3ac',
          400: '#7c7c87',
          500: '#5e5e67',
          600: '#48484f',
          700: '#323238',
          800: '#1c1c1e',
          900: '#0f0f11',
          950: '#050505',
        },
        plan: {
          bg: '#0b0f19',
          card: '#131b2e',
          sidebar: '#0a0e1a',
          text: '#94a3b8',
          blue: '#4f46e5',
          green: '#10b981',
          yellow: '#f59e0b',
          red: '#f43f5e',
          cyan: '#06b6d4',
          purple: '#8b5cf6',
          lightBg: '#f8fafc',
          lightCard: '#ffffff',
          lightSidebar: '#f1f5f9',
        }
      },
      fontFamily: {
        sans: ['Outfit', 'Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        'glass': '0 8px 32px 0 rgba(0, 0, 0, 0.37)',
        'glass-light': '0 8px 32px 0 rgba(31, 38, 135, 0.07)',
      }
    },
  },
  plugins: [],
}
