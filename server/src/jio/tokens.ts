import { jio } from "../config";
import type { AuthData } from "./types";

/**
 * Refreshes the SSO/auth tokens. Returns an updated AuthData (unchanged fields preserved) or throws.
 * Mirrors JioApiClient.refreshToken in the Android app.
 */
export async function refreshTokens(auth: AuthData): Promise<AuthData> {
  if (!auth.ssoToken) throw new Error("No ssoToken to refresh");

  const res = await fetch("https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6", {
    method: "POST",
    headers: {
      ssotoken: auth.ssoToken,
      appname: jio.APP_NAME,
      os: jio.OS,
      devicetype: jio.DEVICE_TYPE,
    },
  });

  if (!res.ok) throw new Error(`Refresh failed (HTTP ${res.status})`);
  const json = (await res.json().catch(() => ({}))) as Record<string, any>;

  const newSso = json.ssoToken ?? "";
  const newAuth = json.authToken ?? "";
  if (!newSso && !newAuth) throw new Error("Refresh returned no new tokens");

  return {
    ...auth,
    ssoToken: newSso || auth.ssoToken,
    authToken: newAuth || auth.authToken,
  };
}
