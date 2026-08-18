/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#1677FF',
          dark: '#0958D9',
          light: '#4096FF'
        },
        ink: {
          900: '#1F2329',
          600: '#646A73',
          400: '#8F959E'
        },
        surface: {
          base: '#F7F8FA',
          card: '#FFFFFF',
          muted: '#F0F2F5'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif']
      }
    }
  },
  plugins: []
}
