declare module "selfsigned" {
  export function generate(
    attrs?: Array<{ name: string; value: string }>,
    opts?: Record<string, unknown>
  ): { private: string; public: string; cert: string };
  const _default: { generate: typeof generate };
  export default _default;
}
