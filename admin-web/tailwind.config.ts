import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Bright, calm admin palette — not the kiosk's Netflix black/red.
        ink: "#111827",
        subtle: "#6B7280",
        line: "#E5E7EB",
        canvas: "#F6F7F9",
        brand: "#2563EB",
        brandDark: "#1D4ED8",
        ok: "#059669",
        warn: "#D97706",
        danger: "#DC2626",
      },
      fontFamily: {
        sans: ["Pretendard", "system-ui", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 2px rgba(17,24,39,0.04), 0 4px 16px rgba(17,24,39,0.06)",
      },
    },
  },
  plugins: [],
};

export default config;
