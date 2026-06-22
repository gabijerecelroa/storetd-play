import os

print(">> 1. BLOQUEANDO EL FOCO DEL REPRODUCTOR (Candado activado)...")
tv_path = "android/app/src/main/java/com/storetd/play/feature/live/LiveTvScreen.kt"
if os.path.exists(tv_path):
    with open(tv_path, "r", encoding="utf-8") as f: tv = f.read()

    viejo_control = "useController = true // Controles sutiles activados"
    nuevo_control = """useController = false // Sin controles
                isFocusable = false
                isClickable = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS"""
    
    if viejo_control in tv:
        tv = tv.replace(viejo_control, nuevo_control)
        with open(tv_path, "w", encoding="utf-8") as f: f.write(tv)
        print("   ✅ Reproductor blindado contra robos de foco.")
