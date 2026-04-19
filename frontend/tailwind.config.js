/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        board: {
          1: "#dcb468",
          2: "#c8a040",
          3: "#b08820",
        },
        line: "#7a5c10",
        sidebar: "#1a1a2e",
        panel: "#16213e",
        accent: {
          DEFAULT: "#e94560",
          2: "#0f3460",
        },
        ink: {
          DEFAULT: "#e0e0e0",
          dim: "#9090a0",
        },
        bg: "#0d0d1a",
      },
      fontFamily: {
        sans: ["Segoe UI", "Apple SD Gothic Neo", "sans-serif"],
      },
      borderRadius: {
        DEFAULT: "10px",
      },
    },
  },
  plugins: [],
};
