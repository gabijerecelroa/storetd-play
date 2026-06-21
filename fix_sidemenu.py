import os

path = "/root/storetd-play/android/app/src/main/java/com/storetd/play/ui/components/PremiumSideMenu.kt"

new_content = """package com.storetd.play.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.storetd.play.navigation.Routes

@Composable
fun PremiumSideMenu(navController: NavController, currentRoute: String?) {
    var isMenuFocused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (isMenuFocused) 220.dp else 65.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF07111B).copy(alpha = 0.95f)) // Fondo Canvas StreamVault
            .padding(vertical = 24.dp)
            .onFocusChanged { isMenuFocused = it.hasFocus }
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MenuButton("🏠", "Inicio", currentRoute == Routes.Home, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Home) { launchSingleTop = true } }
        MenuButton("▶", "TV en vivo", currentRoute == Routes.LiveTv, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.LiveTv) { launchSingleTop = true } }
        MenuButton("★", "Películas", currentRoute == Routes.Movies, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Movies) { launchSingleTop = true } }
        MenuButton("≡", "Series", currentRoute == Routes.Series, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Series) { launchSingleTop = true } }

        Spacer(modifier = Modifier.height(12.dp))

        if (isMenuFocused) Text("MI CONTENIDO", color = Color(0xFF7F8DA5), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, bottom = 4.dp))
        MenuButton("📑", "Guía EPG", currentRoute == Routes.Epg, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Epg) { launchSingleTop = true } }
        MenuButton("❤️", "Favoritos", currentRoute == Routes.Favorites, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Favorites) { launchSingleTop = true } }
        MenuButton("⏱️", "Historial", currentRoute == Routes.History, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.History) { launchSingleTop = true } }

        Spacer(modifier = Modifier.height(12.dp))

        if (isMenuFocused) Text("SISTEMA", color = Color(0xFF7F8DA5), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, bottom = 4.dp))
        MenuButton("👤", "Mi Cuenta", currentRoute == Routes.Account, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Account) { launchSingleTop = true } }
        MenuButton("🎧", "Soporte", currentRoute == Routes.Support, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Support) { launchSingleTop = true } }
        MenuButton("⚙️", "Ajustes", currentRoute == Routes.Settings, isMenuFocused, { isMenuFocused = true }) { navController.navigate(Routes.Settings) { launchSingleTop = true } }
    }
}

@Composable
fun MenuButton(icon: String, title: String, isSelected: Boolean, isExpanded: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)

    // Colores Oficiales StreamVault
    val bgColor = if (isFocused) Color(0xFF69A8FF) else if (isSelected) Color(0xFF162338) else Color.Transparent
    val contentColor = if (isFocused) Color(0xFF07111B) else if (isSelected) Color.White else Color(0xFF7F8DA5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50)) // Forma de Píldora
            .background(bgColor)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Text(icon, color = contentColor, fontSize = 16.sp)
        }
        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
"""

if os.path.exists(path):
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
    print("✅ ¡Adiós Casita Roja! Menú lateral StreamVault inyectado con éxito.")
else:
    print("⚠️ No se encontró PremiumSideMenu.kt")
