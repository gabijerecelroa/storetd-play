const { createClient } = require("@supabase/supabase-js");

const supabaseUrl = process.env.SUPABASE_URL || "";
const supabaseServiceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY || "";

function isDatabaseConfigured() {
  return Boolean(supabaseUrl && supabaseServiceRoleKey);
}

const supabase = isDatabaseConfigured()
  ? createClient(supabaseUrl, supabaseServiceRoleKey)
  : null;

module.exports = {
  supabase,
  isDatabaseConfigured
};
