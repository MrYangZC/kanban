/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#409EFF',
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
          secondary: '#909399',
          placeholder: '#c0c4cc',
          900: '#303133',
          600: '#606266',
          400: '#909399'
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
          light: '#f6ffed'
        },
        warning: {
          DEFAULT: '#faad14',
          light: '#fffbe6'
        },
        danger: {
          DEFAULT: '#f56c6c',
          light: '#fef0f0'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 1px 2px rgba(0,0,0,0.04)',
        'card-hover': '0 2px 8px rgba(0,0,0,0.08)',
        modal: '0 8px 24px rgba(0,0,0,0.12)'
      }
    }
  },
  plugins: []
}
