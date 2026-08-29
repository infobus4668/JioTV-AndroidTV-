import https from "node:https";

/**
 * Case-preserving HTTPS request for the Jio APIs.
 *
 * WHY NOT fetch(): undici/`fetch` lowercases every outgoing header name. Jio's `geturl` and
 * `refreshtoken` endpoints are case-sensitive about auth headers (`ssoToken`, `Accesstoken`, `Crmid`,
 * …) — exactly as the Android app sends them via HttpURLConnection. Lowercased headers get a 403.
 * Node's core `https` preserves the header-name case you pass, matching the app byte-for-byte.
 *
 * We also deliberately send no Accept-Encoding, so responses come back uncompressed (Jio returns
 * identity), avoiding manual gunzip.
 */
export function jioRequest(opts: {
  method: string;
  url: string;
  headers: Record<string, string>;
  body?: string;
}): Promise<{ status: number; text: string }> {
  const u = new URL(opts.url);
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: u.hostname,
        port: 443,
        path: u.pathname + u.search,
        method: opts.method,
        headers: opts.headers,
      },
      (res) => {
        const chunks: Buffer[] = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => resolve({ status: res.statusCode ?? 0, text: Buffer.concat(chunks).toString("utf8") }));
      }
    );
    req.on("error", reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}
