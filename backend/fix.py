import re

with open("src/playlistContent.js", "r") as f:
    code = f.read()

# Reverse the bad regex replacement
code = code.replace("playlistUrl: playlistUrlMasked,", "playlistUrl,")

# Now re-apply only where needed
# saveSectionCache / saveRawPayloadCache calls inside refreshXtreamContentCacheForClient
code = re.sub(r'playlistUrl,\n        section: "live"', r'playlistUrl: playlistUrlMasked,\n        section: "live"', code)
code = re.sub(r'playlistUrl,\n        section: "movies"', r'playlistUrl: playlistUrlMasked,\n        section: "movies"', code)
code = re.sub(r'playlistUrl,\n        section: "movie-categories"', r'playlistUrl: playlistUrlMasked,\n        section: "movie-categories"', code)
code = re.sub(r'playlistUrl,\n        section: "series-folders"', r'playlistUrl: playlistUrlMasked,\n        section: "series-folders"', code)

with open("src/playlistContent.js", "w") as f:
    f.write(code)
print("Fixed.")
