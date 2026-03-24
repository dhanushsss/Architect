/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#6366f1',
        danger: '#ef4444',
        warning: '#f59e0b',
        success: '#22c55e',
      }
    }
  },
  plugins: []
}
