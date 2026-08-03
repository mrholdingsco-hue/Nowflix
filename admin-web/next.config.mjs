/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // sharp is a native dep used only in server route handlers; keep it external so it is
  // never pulled into any client bundle.
  experimental: {
    serverComponentsExternalPackages: ["sharp"],
  },
};

export default nextConfig;
