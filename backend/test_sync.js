require('dotenv').config();
const { refreshContentCacheForClient } = require("./src/playlistContent.js");

async function run() {
  try {
    console.log("Starting sync test for code 253698...");
    const result = await refreshContentCacheForClient("253698");
    console.log("Sync Result:", JSON.stringify(result, null, 2));
    process.exit(result.success ? 0 : 1);
  } catch (error) {
    console.error("Error during sync test:", error);
    process.exit(1);
  }
}

run();
