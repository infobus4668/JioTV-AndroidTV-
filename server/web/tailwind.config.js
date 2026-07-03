/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Design tokens — matches the Android app's TiviMate-inspired dark palette.
        bg: "#0d1117",
        surface: "#161b22",
        surface2: "#21262d",
        border: "#30363d",
        primary: "#9c27b0",
        primary2: "#6a1b9a",
        muted: "#8b949e",
        text: "#f0f6fc",
        live: "#ff4444",
        ok: "#3fb950",
        danger: "#f85149",
      },
      boxShadow: {
        card: "0 12px 40px rgba(0,0,0,.4)",
      },
    },
  },
  plugins: [],
};
