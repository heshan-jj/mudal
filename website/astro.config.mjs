import { defineConfig } from "astro/config";

export default defineConfig({
  site: process.env.VERCEL_PROJECT_PRODUCTION_URL
    ? `https://${process.env.VERCEL_PROJECT_PRODUCTION_URL}`
    : undefined,
  trailingSlash: "ignore",
  build: {
    inlineStylesheets: "always",
  },
});



