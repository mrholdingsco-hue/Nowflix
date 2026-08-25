import { defineConfig } from "vitest/config";
import path from "node:path";

export default defineConfig({
  test: {
    include: ["src/**/*.test.ts"],
    environment: "node",
  },
  resolve: {
    // 라우트 핸들러 테스트가 앱과 동일한 "@/..." 경로를 쓸 수 있게 한다(tsconfig paths 와 동일).
    alias: { "@": path.resolve(__dirname, "src") },
  },
});
