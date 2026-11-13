/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#409EFF',
          50: '#f0f7ff',
          100: '#e6f3ff',
          200: '#bae0ff',
          300: '#8cc8ff',
          400: '#69b1ff',
          500: '#409EFF',
          600: '#1890ff',
          700: '#096dd9',
          800: '#0050b3',
          900: '#003a8c',
          dark: '#1890ff',
          light: '#e6f7ff'
        },
        brand: {
          DEFAULT: '#409EFF',
          dark: '#1890ff',
          light: '#e6f7ff'
        },
        ink: {
          DEFAULT: '#303133',
          regular: '#606266',
          /* 次要文字：原 #909399 在 #f5f7fa 上仅 2.99:1，不满足 WCAG AA(4.5:1) */
          secondary: '#666c78',
          /* 11-12px 小字专用档，比 secondary 再稍深，保证小字号可读 */
          quiet: '#5b6270',
          placeholder: '#a8adb6',
          900: '#303133',
          600: '#606266',
          400: '#666c78'
        },
        line: {
          DEFAULT: '#dcdfe6',
          light: '#e4e7ed',
          lighter: '#f0f0f0'
        },
        surface: {
          page: '#f5f7fa',
          card: '#ffffff',
          hover: '#fafafa',
          light: '#f0f2f5',
          base: '#f5f7fa',
          muted: '#f0f2f5'
        },
        success: {
          DEFAULT: '#52c41a',
          light: '#f6ffed',
          bg: '#f6ffed'
        },
        warning: {
          DEFAULT: '#faad14',
          light: '#fffbe6',
          bg: '#fff7e6'
        },
        danger: {
          DEFAULT: '#f56c6c',
          light: '#fef0f0',
          bg: '#fff2f0'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.02)',
        'card-hover': '0 4px 16px rgba(0,0,0,0.08), 0 2px 4px rgba(0,0,0,0.04)',
        'card-hover-lg': '0 8px 24px rgba(64,158,255,0.12), 0 4px 8px rgba(0,0,0,0.06)',
        modal: '0 12px 32px rgba(0,0,0,0.12)',
        'inner-glow': 'inset 0 1px 0 rgba(255,255,255,0.6)'
      },
      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, #409EFF 0%, #1890ff 100%)',
        'gradient-sidebar': 'linear-gradient(180deg, #ffffff 0%, #fafbfc 100%)',
        'gradient-page': 'linear-gradient(180deg, #f5f7fa 0%, #f0f2f5 100%)',
        'gradient-card-hover': 'linear-gradient(135deg, rgba(64,158,255,0.04) 0%, rgba(24,144,255,0.02) 100%)'
      },
      transitionTimingFunction: {
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
        'spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)'
      },
      keyframes: {
        'fade-in-up': {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' }
        }
      },
      animation: {
        'fade-in-up': 'fade-in-up 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        'fade-in': 'fade-in 0.2s ease-out'
      }
    }
  },
  plugins: []
}
