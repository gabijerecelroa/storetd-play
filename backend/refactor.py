import re

with open("src/playlistContent.js", "r") as f:
    code = f.read()

# 1. Update xtreamConfig
code = re.sub(
    r'function xtreamConfig\(\) \{',
    r'''function xtreamConfig(playlistUrl) {
  if (playlistUrl) {
    try {
      const url = new URL(playlistUrl);
      const baseUrl = url.origin;
      const username = url.searchParams.get("username") || "";
      const password = url.searchParams.get("password") || "";
      return { baseUrl, username, password };
    } catch(e) {}
  }''',
    code
)

# 2. Update xtreamSourceUrlMasked
code = re.sub(
    r'function xtreamSourceUrlMasked\(\) \{',
    r'function xtreamSourceUrlMasked(playlistUrl) {',
    code
)
code = re.sub(
    r'const \{ baseUrl, username \} = xtreamConfig\(\);',
    r'const { baseUrl, username } = xtreamConfig(playlistUrl);',
    code
)

# 3. Update xtreamBuildUrl
code = re.sub(
    r'function xtreamBuildUrl\(action, extra = \{\}\) \{',
    r'function xtreamBuildUrl(playlistUrl, action, extra = {}) {',
    code
)
code = re.sub(
    r'const \{ baseUrl, username, password \} = xtreamConfig\(\);',
    r'const { baseUrl, username, password } = xtreamConfig(playlistUrl);',
    code
)

# 4. Update fetchXtreamJson
code = re.sub(
    r'async function fetchXtreamJson\(action, extra = \{\}\) \{',
    r'async function fetchXtreamJson(playlistUrl, action, extra = {}) {',
    code
)
code = re.sub(
    r'const url = xtreamBuildUrl\(action, extra\);',
    r'const url = xtreamBuildUrl(playlistUrl, action, extra);',
    code
)

# 5. Update fetchXtreamJson calls in getXtreamEpisodesForSeriesFolder
code = re.sub(
    r'async function getXtreamEpisodesForSeriesFolder\(folder\) \{',
    r'async function getXtreamEpisodesForSeriesFolder(folder, playlistUrl) {',
    code
)
code = re.sub(
    r'info = await fetchXtreamJson\("get_series_info", \{ series_id: seriesId \}\);',
    r'info = await fetchXtreamJson(playlistUrl, "get_series_info", { series_id: seriesId });',
    code
)

# 6. Update refreshXtreamContentCacheForClient
code = re.sub(
    r'async function refreshXtreamContentCacheForClient\(\{ activationCode, refreshSection, shouldRefresh \}\) \{',
    r'async function refreshXtreamContentCacheForClient({ activationCode, refreshSection, shouldRefresh, playlistUrl }) {',
    code
)
code = re.sub(
    r'var playlistUrl = xtreamSourceUrlMasked\(\);',
    r'var playlistUrlMasked = xtreamSourceUrlMasked(playlistUrl);',
    code
)
# Fix the reference to playlistUrl in saveSectionCache / saveRawPayloadCache (they need playlistUrlMasked now)
code = re.sub(
    r'playlistUrl,',
    r'playlistUrl: playlistUrlMasked,',
    code
)

code = re.sub(
    r'fetchXtreamJson\("get_live_categories"\)',
    r'fetchXtreamJson(playlistUrl, "get_live_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_live_streams"\)',
    r'fetchXtreamJson(playlistUrl, "get_live_streams")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_vod_categories"\)',
    r'fetchXtreamJson(playlistUrl, "get_vod_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_vod_streams"\)',
    r'fetchXtreamJson(playlistUrl, "get_vod_streams")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_series_categories"\)',
    r'fetchXtreamJson(playlistUrl, "get_series_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_series"\)',
    r'fetchXtreamJson(playlistUrl, "get_series")',
    code
)

# 7. Update refreshContentCacheForClient
code = re.sub(
    r'shouldRefresh\n  \}\);',
    r'shouldRefresh,\n    playlistUrl: client.playlist_url\n  });',
    code
)

# 8. Update getSeriesFolderByKey
code = re.sub(
    r'episodes = await getXtreamEpisodesForSeriesFolder\(folder\);',
    r'''const client = await getClientByActivationCode(activationCode);
    episodes = await getXtreamEpisodesForSeriesFolder(folder, client.playlist_url);''',
    code
)

# 9. Update getMovieCategoryByKey
code = re.sub(
    r'const movieRows = await fetchXtreamJson\("get_vod_streams"\);',
    r'''const client = await getClientByActivationCode(activationCode);
    const movieRows = await fetchXtreamJson(client.playlist_url, "get_vod_streams");''',
    code
)

# 10. Update generateSmartoneM3u
code = re.sub(
    r'fetchXtreamJson\("get_live_categories"\)',
    r'fetchXtreamJson(client.playlist_url, "get_live_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_live_streams"\)',
    r'fetchXtreamJson(client.playlist_url, "get_live_streams")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_vod_categories"\)',
    r'fetchXtreamJson(client.playlist_url, "get_vod_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_vod_streams"\)',
    r'fetchXtreamJson(client.playlist_url, "get_vod_streams")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_series_categories"\)',
    r'fetchXtreamJson(client.playlist_url, "get_series_categories")',
    code
)
code = re.sub(
    r'fetchXtreamJson\("get_series"\)',
    r'fetchXtreamJson(client.playlist_url, "get_series")',
    code
)


with open("src/playlistContent.js", "w") as f:
    f.write(code)
print("Refactoring complete.")
