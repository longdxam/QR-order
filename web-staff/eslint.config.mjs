import nextConfig from "eslint-config-next";

const eslintConfig = [...nextConfig, { ignores: ["src/types/api.d.ts"] }];

export default eslintConfig;
