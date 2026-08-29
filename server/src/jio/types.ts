/** The credentials returned by Jio login and consumed by the app / stream endpoints. */
export interface AuthData {
  ssoToken: string;
  authToken: string;
  refreshToken: string;
  crmid: string;
  uniqueId: string;
  deviceId: string;
  userId: string;
}

export const EMPTY_AUTH: AuthData = {
  ssoToken: "",
  authToken: "",
  refreshToken: "",
  crmid: "",
  uniqueId: "",
  deviceId: "",
  userId: "",
};
