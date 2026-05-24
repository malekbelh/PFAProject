/** @type {import('next').NextConfig} */
const nextConfig = {
  webpack: (config) => {
    // Permet le chargement de fichiers WASM (requis par web-tree-sitter CDN)
    config.experiments = {
      ...config.experiments,
      asyncWebAssembly: true,
    };
    return config;
  },

  // Headers COOP/COEP requis pour SharedArrayBuffer (web-tree-sitter WASM)
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Cross-Origin-Opener-Policy",  value: "same-origin" },
          { key: "Cross-Origin-Embedder-Policy", value: "require-corp" },
        ],
      },
    ];
  },
};

export default nextConfig;
