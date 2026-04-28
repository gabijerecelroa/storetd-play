const { supabase, isDatabaseConfigured } = require("./db");

function defaultAppConfig() {
  return {
    appName: "StoreTD Play",
    welcomeTitle: "Reproductor profesional para contenido autorizado",
    welcomeMessage: "Carga tus listas M3U/M3U8 autorizadas, organiza canales, usa favoritos, historial y zapping.",
    maintenanceMode: false,
    maintenanceMessage: "Estamos realizando mantenimiento. Intenta nuevamente más tarde.",
    providerMessage: "Uso permitido solo con contenido autorizado.",
    supportWhatsApp: "",
    supportEmail: "",
    renewUrl: "",
    termsUrl: "",
    privacyUrl: "",
    allowUserPlaylistInput: true,
    forceAppUpdate: false,
    minimumAppVersion: "1.0.0",
    updatedAt: null
  };
}

function rowToConfig(row) {
  if (!row) return defaultAppConfig();

  return {
    appName: row.app_name || "StoreTD Play",
    welcomeTitle: row.welcome_title || "",
    welcomeMessage: row.welcome_message || "",
    maintenanceMode: Boolean(row.maintenance_mode),
    maintenanceMessage: row.maintenance_message || "",
    providerMessage: row.provider_message || "",
    supportWhatsApp: row.support_whatsapp || "",
    supportEmail: row.support_email || "",
    renewUrl: row.renew_url || "",
    termsUrl: row.terms_url || "",
    privacyUrl: row.privacy_url || "",
    allowUserPlaylistInput: Boolean(row.allow_user_playlist_input),
    forceAppUpdate: Boolean(row.force_app_update),
    minimumAppVersion: row.minimum_app_version || "1.0.0",
    updatedAt: row.updated_at || null
  };
}

function inputToRow(input) {
  const fallback = defaultAppConfig();

  return {
    id: 1,
    app_name: String(input.appName ?? fallback.appName).trim(),
    welcome_title: String(input.welcomeTitle ?? fallback.welcomeTitle).trim(),
    welcome_message: String(input.welcomeMessage ?? fallback.welcomeMessage).trim(),
    maintenance_mode: Boolean(input.maintenanceMode),
    maintenance_message: String(input.maintenanceMessage ?? fallback.maintenanceMessage).trim(),
    provider_message: String(input.providerMessage ?? fallback.providerMessage).trim(),
    support_whatsapp: String(input.supportWhatsApp ?? "").trim(),
    support_email: String(input.supportEmail ?? "").trim(),
    renew_url: String(input.renewUrl ?? "").trim(),
    terms_url: String(input.termsUrl ?? "").trim(),
    privacy_url: String(input.privacyUrl ?? "").trim(),
    allow_user_playlist_input: input.allowUserPlaylistInput !== false,
    force_app_update: Boolean(input.forceAppUpdate),
    minimum_app_version: String(input.minimumAppVersion ?? "1.0.0").trim(),
    updated_at: new Date().toISOString()
  };
}

async function getAppConfig() {
  if (!isDatabaseConfigured()) {
    return defaultAppConfig();
  }

  const { data, error } = await supabase
    .from("app_config")
    .select("*")
    .eq("id", 1)
    .maybeSingle();

  if (error) {
    throw error;
  }

  return rowToConfig(data);
}

async function updateAppConfig(input) {
  if (!isDatabaseConfigured()) {
    throw new Error("Base de datos no configurada.");
  }

  const row = inputToRow(input);

  const { data, error } = await supabase
    .from("app_config")
    .upsert(row, { onConflict: "id" })
    .select()
    .single();

  if (error) {
    throw error;
  }

  return rowToConfig(data);
}

module.exports = {
  defaultAppConfig,
  getAppConfig,
  updateAppConfig
};
