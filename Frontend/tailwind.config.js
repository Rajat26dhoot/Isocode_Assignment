/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: { fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui'], mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular'] }, boxShadow: { panel: '0 1px 2px rgba(15,23,42,.03), 0 8px 24px rgba(15,23,42,.03)' } } },
  plugins: [],
}
