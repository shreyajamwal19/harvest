/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Warm paper surfaces — the app's "table linen"
        paper: {
          50: '#FEFDFB',
          100: '#F8F4EC',
          200: '#F1EADB',
          300: '#E6DAC2',
          400: '#D6C4A3',
          500: '#C2AC84',
        },
        // Warm near-black ink for text — never pure black
        ink: {
          400: '#8A7F73',
          500: '#5B5147',
          600: '#463D34',
          700: '#332B24',
          800: '#241F1A',
          900: '#181410',
        },
        // Roasted brick — primary accent (deliberately not Claude-terracotta orange)
        brick: {
          50: '#F7EBEA',
          100: '#E6C7C4',
          200: '#B97A78',
          300: '#8C3436',
          400: '#6B2226',
          500: '#4A1518',
          600: '#3A1013',
          700: '#2A0B0D',
        },
        // Deep moss — secondary accent
        moss: {
          50: '#EEF2ED',
          100: '#D3DED2',
          200: '#A6BDA3',
          300: '#799C74',
          400: '#587B54',
          500: '#3E5641',
          600: '#324536',
          700: '#26342A',
        },
        // Toasted gold — sparing highlight accent
        gold: {
          100: '#F6E8C8',
          300: '#E0BB6B',
          500: '#B98A2E',
          700: '#8C6A20',
        },
      },
      fontFamily: {
        display: ['Fraunces', 'Georgia', 'serif'],
        body: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        soft: '0 1px 2px rgba(36, 31, 26, 0.04), 0 8px 24px -12px rgba(36, 31, 26, 0.12)',
        lift: '0 4px 8px rgba(36, 31, 26, 0.06), 0 16px 32px -16px rgba(36, 31, 26, 0.18)',
      },
      borderRadius: {
        sheet: '0.375rem',
      },
      transitionTimingFunction: {
        quiet: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
    },
  },
  plugins: [],
}
