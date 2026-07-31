/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg:       'var(--color-bg)',
        surface:  'var(--color-surface)',
        raised:   'var(--color-raised)',
        border:   'var(--color-border)',
        muted:    'var(--color-muted)',
        dim:      'var(--color-dim)',
        error:    'var(--color-error)',
        warn:     'var(--color-warn)',
        success:  'var(--color-success)',
        accent:   'var(--color-accent)',
        text:     'var(--color-text)',
        white:    'var(--color-text)',
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
