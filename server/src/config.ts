import path from "node:path";

/** Runtime configuration, read once from the environment. */
export const config = {
  port: Number(process.env.PORT ?? 8080),
  httpsPort: Number(process.env.HTTPS_PORT ?? 8443),
  host: process.env.HOST ?? "0.0.0.0",
  serverToken: process.env.JTV_SERVER_TOKEN ?? "",
  adminPassword: process.env.ADMIN_PASSWORD ?? "",
  dataDir: path.resolve(process.env.DATA_DIR ?? "./data"),
};

/** Shared Jio API constants (mirrors the Android app's JioApiClient). */
export const jio = {
  USER_AGENT: "okhttp/4.2.2",
  APP_NAME: "RJIL_JioTV",
  OS: "android",
  DEVICE_TYPE: "phone",
  HOST: "jiotvapi.media.jio.com",
};
