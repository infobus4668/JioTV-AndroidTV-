import { jio } from "../config";
import { jioRequest } from "./http";
import type { AuthData } from "./types";

/**
 * Refreshes the SSO/auth tokens. Returns an updated AuthData (unchanged fields preserved) or throws.
 * Mirrors JioApiClient.refreshToken — header casing matters (see http.ts), which is why the previous
 * fetch()-based version failed ("Refresh failed").
 */
export async function refreshTokens(auth: AuthData): Promise<AuthData> {
  if (!auth.ssoToken) throw new Error("No ssoToken to refresh");

  const res = await jioRequest({
    method: "POST",
    url: "https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6",
    headers: {
      ssotoken: auth.ssoToken,
      appname: jio.APP_NAME,
      os: jio.OS,
      devicetype: jio.DEVICE_TYPE,
      deviceId: auth.deviceId,
      uniqueId: auth.uniqueId,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ appName: jio.APP_NAME, deviceId: auth.deviceId, uniqueId: auth.uniqueId }),
  });

  if (res.status < 200 || res.status >= 300) throw new Error(`Refresh failed (HTTP ${res.status})`);
  const json = (res.text ? JSON.parse(res.text) : {}) as Record<string, any>;

  const newSso = json.ssoToken ?? "";
  const newAuth = json.authToken ?? "";
  if (!newSso && !newAuth) throw new Error("Refresh returned no new tokens");

  return {
    ...auth,
    ssoToken: newSso || auth.ssoToken,
    authToken: newAuth || auth.authToken,
  };
}
