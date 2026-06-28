import re

with open('/root/storetd-play/backend/src/playlistContent.js', 'r') as f:
    content = f.read()

# Delete functions that are for M3U parsing
functions_to_delete = [
    "normalizeText", "attr", "parseExtinfName", "parseM3u",
    "isAdult", "isMovie", "isSeries", "isLiveTvGroup", "sectionOf",
    "uniqueByUrl", "groupNames", "buildPayload", "fetchPlaylist", "splitSections",
    "cleanSeriesBaseText", "extractSeriesTitleBeforeEpisode", "cleanSeriesTitle",
    "looksLikeGenericSeriesGroup", "episodeSeason", "episodeNumber",
    "forceCleanSeriesEpisodeTitle", "finalCleanSeriesFolderTitle",
    "finalSeriesFolderTitleFromItem", "finalMergeSeriesTitleV9",
    "hardCleanGeneratedSeriesTitle", "hardCleanSeriesTitleV13",
    "mergeGeneratedSeriesFolders", "buildSeriesFoldersPayload",
    "buildMovieCategoriesPayload", "isXtreamContentMode"
]

for func in functions_to_delete:
    content = re.sub(r'async function ' + func + r'\b.*?\n}\n', '', content, flags=re.DOTALL)
    content = re.sub(r'function ' + func + r'\b.*?\n}\n', '', content, flags=re.DOTALL)

# Delete the adultWords, movieWords, seriesWords arrays
content = re.sub(r'const adultWords = \[.*?\];', '', content, flags=re.DOTALL)
content = re.sub(r'const movieWords = \[.*?\];', '', content, flags=re.DOTALL)
content = re.sub(r'const seriesWords = \[.*?\];', '', content, flags=re.DOTALL)

with open('/root/storetd-play/backend/src/playlistContent.js', 'w') as f:
    f.write(content)
