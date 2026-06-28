require('dotenv').config();
const { supabase } = require("./src/db.js");

async function check() {
  const { data, error } = await supabase.from('clients').select('*').eq('activation_code', '253698').single();
  console.log("Client:", data, "Error:", error);
}

check();
