/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    // Colours resolve to the design tokens in tokens.css (single source of truth, light + dark).
    colors: {
      transparent: "transparent",
      current: "currentColor",
      white: "#fff",
      black: "#000",
      bg: "var(--background)",
      surface: "var(--surface)",
      "surface-2": "var(--surface-2)",
      "surface-hover": "var(--surface-hover)",
      border: "var(--border)",
      "border-strong": "var(--border-strong)",
      fg: "var(--foreground)",
      muted: "var(--muted-foreground)",
      subtle: "var(--subtle-foreground)",
      accent: "var(--accent)",
      "accent-strong": "var(--accent-strong)",
      success: "var(--success)",
      warning: "var(--warning)",
      error: "var(--error)",
    },
    extend: {
      borderRadius: {
        md: "var(--radius-md)",
        lg: "var(--radius-lg)",
        xl: "var(--radius-xl)",
        full: "var(--radius-full)",
      },
      maxWidth: { container: "var(--container-max)" },
    },
  },
  plugins: [],
};
