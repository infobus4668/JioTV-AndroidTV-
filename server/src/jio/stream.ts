import { randomUUID } from "node:crypto";
import { jio } from "../config";
import { jioRequest } from "./http";
import type { AuthData } from "./types";

export interface StreamData {
  streamUrl: string;
  licenseUrl: string;
  isMpd: boolean;
  streamHeaders: Record<string, string>;
  licenseHeaders: Record<string, string>;
}

/** Thrown for auth failures (401/403/419) so the caller can refresh + retry. */
export class GeturlAuthError extends Error {
  constructor(public status: number) {
    super(`geturl failed (HTTP ${status})`);
  }
}

/**
 * Resolves a channel to a playable stream + DRM info (mirrors JioApiClient.getStreamUrl, incl. the
 * exact header set + casing from the Kodi plugin / Android app — casing matters, see http.ts).
 * @param streamType "Live" for live; "Seek" (with beginEpochMs) for catch-up.
 */
export async function getStreamData(
  channelId: string,
  auth: AuthData,
  opts: { streamType?: "Live" | "Seek"; beginEpochMs?: number } = {}
): Promise<StreamData> {
  const streamType = opts.streamType ?? "Live";
  let body = `stream_type=${streamType}&channel_id=${encodeURIComponent(channelId)}`;
  if (streamType === "Seek" && opts.beginEpochMs) {
    body += `&begin=${Math.floor(opts.beginEpochMs / 1000)}`;
  }

  const res = await jioRequest({
    method: "POST",
    url: "https://jiotvapi.media.jio.com/playback/apis/v1.1/geturl",
    headers: {
      Host: jio.HOST,
      Appkey: "NzNiMDhlYzQyNjJm",
      Devicetype: jio.DEVICE_TYPE,
      Os: jio.OS,
      Deviceid: auth.deviceId,
      Osversion: "13",
      Dm: "Google Pixel 5",
      Uniqueid: auth.deviceId,
      Usergroup: "tvYR7NSNn7rymo3F",
      Languageid: "6",
      Userid: auth.userId,
      Sid: "892898ba-f9de-4572-b6c2-e717b0ad",
      Crmid: auth.crmid,
      Isott: "false",
      Channel_id: channelId,
      Langid: "6",
      ssoToken: auth.ssoToken,
      Accesstoken: auth.authToken,
      Subscriberid: auth.crmid,
      analyticsId: auth.deviceId,
      Lbcookie: "1",
      Versioncode: "389",
      "user-agent": "okhttp/4.2.2",
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  if (res.status === 401 || res.status === 403 || res.status === 419) throw new GeturlAuthError(res.status);
  if (res.status < 200 || res.status >= 300) throw new Error(`geturl failed (HTTP ${res.status})`);
  const json = JSON.parse(res.text) as any;

  let streamUrl: string = json.result ?? "";
  const mpd = json.mpd;
  const isMpd = !!(mpd && mpd.result);
  let licenseUrl = "";
  if (isMpd) {
    streamUrl = mpd.result ?? "";
    licenseUrl = mpd.key ?? "";
  }

  let cookieStr = "";
  if (streamUrl.includes("__hdnea__")) {
    cookieStr = "__hdnea__" + streamUrl.split("__hdnea__")[1];
  }

  const licenseHeaders: Record<string, string> = {
    "User-Agent": "PlayTV/1.0",
    appName: jio.APP_NAME,
    "x-platform": jio.OS,
    os: jio.OS,
    devicetype: jio.DEVICE_TYPE,
    osVersion: "13",
    srno: randomUUID(),
    channelid: channelId,
    usergroup: "tvYR7NSNn7rymo3F",
    versionCode: "389",
    "Content-Type": "application/octet-stream",
    Accept: "*/*",
    ssoToken: auth.ssoToken,
    Accesstoken: auth.authToken,
    userId: auth.userId,
    uniqueId: auth.uniqueId,
    crmid: auth.crmid,
    deviceid: auth.deviceId,
  };
  if (cookieStr) licenseHeaders["Cookie"] = cookieStr;

  const streamHeaders: Record<string, string> = {
    "User-Agent": "plaYtv/7.1.5 (Linux;Android 9) ExoPlayerLib/2.11.7",
    ssoToken: auth.ssoToken,
    userId: auth.userId,
    uniqueId: auth.uniqueId,
    crmid: auth.crmid,
    deviceid: auth.deviceId,
    devicetype: jio.DEVICE_TYPE,
    os: "B2G",
    osversion: "2.5",
    versioncode: "353",
  };
  if (cookieStr) streamHeaders["Cookie"] = cookieStr;

  return { streamUrl, licenseUrl, isMpd, streamHeaders, licenseHeaders };
}
