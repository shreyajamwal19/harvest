/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        cream: {
          50: '#FDFCF8',
          100: '#FAF7F0',
          200: '#F5F0E6',
          300: '#EDE5D5',
          400: '#E0D4BC',
          500: '#D1C2A3',
        },
        sage: {
          50: '#F4F7F4',
          100: '#E3EBE3',
          200: '#C5D6C5',
          300: '#9EBA9E',
          400: '#7A9E7A',
          500: '#5C825C',
          600: '#486848',
          700: '#3A523A',
          800: '#2E422E',
          900: '#263626',
        },
        terracotta: {
          50: '#FDF5F3',
          100: '#FAE8E3',
          200: '#F5D0C6',
          300: '#EDB0A0',
          400: '#E08A72',
          500: '#D46B4E',
          600: '#C2553A',
          700: '#A24430',
          800: '#863A2C',
          900: '#703428',
        },
      },
      fontFamily: {
        display: ['Playfair Display', 'Georgia', 'serif'],
        body: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
