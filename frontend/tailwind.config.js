/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        navy: '#1E3A5F',
        teal: '#2E86AB',
        emerald: '#17A589',
        'accent-teal': '#2ee8a5',
        'accent-blue': '#3b82f6',
        'accent-purple': '#8b5cf6',
        'bg-primary': '#0a0a0f',
        'bg-secondary': '#111118',
        'bg-tertiary': '#1a1a24',
        'bg-card': '#13131d',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Space Grotesk', 'Inter', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
