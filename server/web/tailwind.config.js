/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    // Screens tuned so a phone and a tablet behave correctly in BOTH orientations:
    //   phone portrait  ~360–430    phone landscape  ~640–932
    //   tablet portrait ~768–834    tablet landscape  ~1024–1366
    // Width alone can't split "landscape phone" from "portrait tablet" (both fall in 768–932), so we
    // add `portrait:` / `landscape:` variants alongside the width breakpoints. Rule of thumb below:
    //   sm (640)  = roomy enough for nav labels + more tiles
    //   md (768)  = tablet portrait / landscape-phone: becomes the "keep it reasonably wide" tier
    //   lg (1024) = tablet landscape / desktop: roomy two-column surfaces
    //   xl (1280) = large desktop
    screens: {
      sm: "640px",
      md: "768px",
      lg: "1024px",
      xl: "1280px",
      portrait: { raw: "(orientation: portrait)" },
      landscape: { raw: "(orientation: landscape)" },
    },
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
