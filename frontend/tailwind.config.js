/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg:       '#0a0a0f',
        surface:  '#13131a',
        raised:   '#1c1c27',
        border:   '#2a2a3a',
        muted:    '#3f3f5a',
        dim:      '#64748b',
        error:    '#ef4444',
        warn:     '#f59e0b',
        success:  '#22c55e',
        accent:   '#818cf8',
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', 'Menlo', 'Monaco', 'Courier New', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      keyframes: {
        shimmer: {
          '0%':   { backgroundPosition: '-400px 0' },
          '100%': { backgroundPosition: '400px 0' },
        },
        'ping-slow': {
          '0%, 100%': { transform: 'scale(1)', opacity: '1' },
          '50%':      { transform: 'scale(1.6)', opacity: '0' },
        },
      },
      animation: {
        shimmer:    'shimmer 1.6s ease-in-out infinite',
        'ping-slow':'ping-slow 2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
