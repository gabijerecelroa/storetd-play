import re

with open("src/playlistContent.js", "r") as f:
    code = f.read()

# xtreamLiveUrl
code = re.sub(
    r'function xtreamLiveUrl\(streamId, ext = "ts"\) \{',
    r'function xtreamLiveUrl(playlistUrl, streamId, ext = "ts") {',
    code
)
# xtreamMovieUrl
code = re.sub(
    r'function xtreamMovieUrl\(streamId, ext = "mp4"\) \{',
    r'function xtreamMovieUrl(playlistUrl, streamId, ext = "mp4") {',
    code
)
# xtreamSeriesEpisodeUrl
code = re.sub(
    r'function xtreamSeriesEpisodeUrl\(episodeId, ext = "mp4"\) \{',
    r'function xtreamSeriesEpisodeUrl(playlistUrl, episodeId, ext = "mp4") {',
    code
)

# normalizeXtreamLiveItems
code = re.sub(
    r'function normalizeXtreamLiveItems\(rows, categoryMap\) \{',
    r'function normalizeXtreamLiveItems(playlistUrl, rows, categoryMap) {',
    code
)
code = re.sub(
    r'streamUrl: xtreamLiveUrl\(streamId, ext\),',
    r'streamUrl: xtreamLiveUrl(playlistUrl, streamId, ext),',
    code
)

# normalizeXtreamMovieItems
code = re.sub(
    r'function normalizeXtreamMovieItems\(rows, categoryMap\) \{',
    r'function normalizeXtreamMovieItems(playlistUrl, rows, categoryMap) {',
    code
)
code = re.sub(
    r'streamUrl: xtreamMovieUrl\(streamId, ext\),',
    r'streamUrl: xtreamMovieUrl(playlistUrl, streamId, ext),',
    code
)

# calls to normalization functions
code = re.sub(
    r'normalizeXtreamLiveItems\(liveRows,',
    r'normalizeXtreamLiveItems(playlistUrl, liveRows,',
    code
)
code = re.sub(
    r'normalizeXtreamMovieItems\(filteredRows,',
    r'normalizeXtreamMovieItems(playlistUrl, filteredRows,',
    code
)

# xtreamSeriesEpisodeUrl in getXtreamEpisodesForSeriesFolder
code = re.sub(
    r'streamUrl: xtreamSeriesEpisodeUrl\(episodeId, ext\),',
    r'streamUrl: xtreamSeriesEpisodeUrl(playlistUrl, episodeId, ext),',
    code
)

with open("src/playlistContent.js", "w") as f:
    f.write(code)
print("Fix 2 applied.")
