const { getMovieCategoryByKey, getXtreamEpisodesForSeriesFolder } = require('./src/playlistContent.js');

async function test() {
    const playlistUrl = "http://tv.m3uts.xyz/player_api.php?username=m&password=m";
    try {
        console.log("Testing getMovieCategoryByKey...");
        // Assuming category key could be '1' or similar, but the function might fetch all categories first?
        // Wait, maybe we just call the functions directly.
        // If we don't know the parameters, just testing fetchXtreamJson with the action is enough.
        const { fetchXtreamJson } = require('./src/playlistContent.js');
        
        console.log("Testing get_vod_streams...");
        const vod = await fetchXtreamJson(playlistUrl, "get_vod_streams");
        console.log("get_vod_streams returned items:", vod.length || typeof vod);

        console.log("Testing get_series_info...");
        const series = await fetchXtreamJson(playlistUrl, "get_series_info", { series_id: 1 });
        console.log("get_series_info returned items:", Object.keys(series).length);

        console.log("SUCCESS! No 404 errors.");
    } catch (e) {
        console.error("ERROR:", e.message);
    }
}
test();
