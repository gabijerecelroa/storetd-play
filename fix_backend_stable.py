import os

file_path = "backend/src/playlistContent.js"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Buscamos el punto exacto para inyectar la función anti-emojis
    if "function normalizeXtreamLiveItems(rows, categoryMap) {" in content and "function xtreamCategoryIds(row) {" in content:
        parts = content.split("function normalizeXtreamLiveItems(rows, categoryMap) {")
        before = parts[0]
        sub_parts = parts[1].split("function xtreamCategoryIds(row) {")
        after = "function xtreamCategoryIds(row) {" + sub_parts[1]

        new_func = """function normalizeXtreamLiveItems(rows, categoryMap) {
  if (!Array.isArray(rows)) return [];

  return rows
    .map((row) => {
      const streamId = xtreamNumber(row, "stream_id", "id");
      if (!streamId) return null;

      const categoryId = xtreamString(row, "category_id");
      const category = xtreamCategoryName(categoryMap, categoryId, "Sin Categoria");

      // 🔥 LA ADUANA V4: PURIFICADOR DE EMOJIS Y ACENTOS
      const cleanCat = String(category)
        .normalize("NFD").replace(/[\\u0300-\\u036f]/g, "")
        .replace(/[^a-zA-Z0-9 ]/g, " ")
        .toLowerCase()
        .replace(/\\s+/g, " ")
        .trim();

      const allowedKeywords = [
        "paraguay", "gran hermano", "argentina", "libertadores",
        "eventos", "espn", "fox", "movistar", "24 7", "cinema", "cine",
        "infantil", "musica", "latino", "ufc", "zona", "mundial",
        "d port", "dsport", "deporte", "pelicula", "premium"
      ];

      let isAllowed = false;
      for (const kw of allowedKeywords) {
        if (cleanCat.includes(kw)) {
          isAllowed = true;
          break;
        }
      }

      if (!isAllowed) return null;

      const ext = xtreamString(row, "container_extension") || "ts";

      return {
        id: String(streamId),
        name: xtreamString(row, "name", "title") || `Canal ${streamId}`,
        streamUrl: xtreamLiveUrl(streamId, ext),
        logoUrl: xtreamString(row, "stream_icon", "cover", "image") || null,
        group: xtreamGroupName("live", category), // El nombre con emojis se mantiene para la TV
        tvgId: xtreamString(row, "epg_channel_id", "tvg_id") || null,
        source: {
          provider: "xtream",
          streamId,
          categoryId
        }
      };
    })
    .filter(Boolean);
}

"""
        content = before + new_func + after

    # FIX PÓSTERS: Le damos a la app vieja la variable que pide (posterUrl)
    if "posterUrl: item.cover" not in content:
        content = content.replace(
            "logoUrl: item.cover || item.stream_icon || item.logoUrl || null,",
            "logoUrl: item.cover || item.stream_icon || item.logoUrl || null,\n        posterUrl: item.cover || item.stream_icon || item.logoUrl || null,"
        )
        content = content.replace(
            "folder.logoUrl = item.cover || item.stream_icon || item.logoUrl;",
            "folder.logoUrl = item.cover || item.stream_icon || item.logoUrl;\n      folder.posterUrl = item.cover || item.stream_icon || item.logoUrl;"
        )

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("\n✅ [ÉXITO] Servidor purificado. Aduana Anti-Emojis y Fix de Pósters instalados.")
    print("✅ CERO cambios en Android. Mantenemos la versión estable.\n")
else:
    print("\n❌ Error: No se encontró playlistContent.js\n")
