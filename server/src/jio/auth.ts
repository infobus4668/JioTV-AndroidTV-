import { randomUUID } from "node:crypto";
import { jio } from "../config";
import type { AuthData } from "./types";

/** +91-normalise then base64 (matches the app's Base64.NO_WRAP of the +91 number). */
function encodeMobile(mobile: string): string {
  const formatted = mobile.startsWith("+91") ? mobile : `+91${mobile}`;
  return Buffer.from(formatted, "utf8").toString("base64");
}

/** Sends an OTP to the given Jio mobile number. Resolves on the API's 204. */
export async function sendOtp(mobile: string): Promise<void> {
  const res = await fetch("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send", {
    method: "POST",
    headers: {
      "user-agent": jio.USER_AGENT,
      os: jio.OS,
      host: jio.HOST,
      devicetype: jio.DEVICE_TYPE,
      appname: jio.APP_NAME,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ number: encodeMobile(mobile) }),
  });
  if (res.status !== 204 && !res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Failed to send OTP (HTTP ${res.status}) ${text}`.trim());
  }
}

/** Verifies the OTP and returns the resulting AuthData. */
export async function verifyOtp(mobile: string, otp: string): Promise<AuthData> {
  const body = {
    number: encodeMobile(mobile),
    otp,
    deviceInfo: {
      consumptionDeviceName: "unknown sdk_google_atv_x86",
      info: {
        type: "android",
        platform: { name: "generic_x86" },
        androidId: randomUUID(),
      },
    },
  };

  const res = await fetch("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify", {
    method: "POST",
    headers: {
      "user-agent": jio.USER_AGENT,
      os: jio.OS,
      devicetype: jio.DEVICE_TYPE,
      appname: jio.APP_NAME,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const text = await res.text();
  if (!res.ok) throw new Error(`OTP verify failed (HTTP ${res.status})`);

  const json = JSON.parse(text) as Record<string, any>;
  if (!json.ssoToken) {
    throw new Error(json.message ?? "OTP verification did not return a token");
  }
  const user = json.sessionAttributes?.user ?? {};
  return {
    ssoToken: json.ssoToken ?? "",
    authToken: json.authToken ?? "",
    crmid: user.subscriberId ?? "",
    uniqueId: user.unique ?? "",
    deviceId: json.deviceId ?? "",
    userId: user.uid ?? "",
  };
}
