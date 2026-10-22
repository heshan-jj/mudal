import { defineConfig } from "astro/config";

// GitHub Pages project site: https://heshan-jj.github.io/mudal/
export default defineConfig({
  site: "https://heshan-jj.github.io",
  base: "/mudal",
  trailingSlash: "ignore",
  build: {
    inlineStylesheets: "auto",
  },
});
