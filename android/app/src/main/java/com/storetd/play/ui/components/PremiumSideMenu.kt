package com.storetd.play.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.storetd.play.navigation.Routes

@Composable
fun PremiumSideMenu(navController: NavController, currentRoute: String?) {
    var isMenuFocused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (isMenuFocused) 210.dp else 65.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF000000).copy(alpha = 0.85f))
            .padding(vertical = 24.dp)
            .onFocusChanged { isMenuFocused = it.hasFocus }
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MenuButton("🏠", "Inicio", currentRoute == Routes.Home, isMenuFocused) { navController.navigate(Routes.Home) { launchSingleTop = true } }
        MenuButton("📺", "TV en vivo", currentRoute == Routes.LiveTv, isMenuFocused) { navController.navigate(Routes.LiveTv) { launchSingleTop = true } }
        MenuButton("🎬", "Películas", currentRoute == Routes.Movies, isMenuFocused) { navController.navigate(Routes.Movies) { launchSingleTop = true } }
        MenuButton("🍿", "Series", currentRoute == Routes.Series, isMenuFocused) { navController.navigate(Routes.Series) { launchSingleTop = true } }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (isMenuFocused) {
            Text("MI CONTENIDO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        }
        MenuButton("📑", "Guía EPG", currentRoute == Routes.Epg, isMenuFocused) { navController.navigate(Routes.Epg) { launchSingleTop = true } }
        MenuButton("❤️", "Favoritos", currentRoute == Routes.Favorites, isMenuFocused) { navController.navigate(Routes.Favorites) { launchSingleTop = true } }
        MenuButton("⏱️", "Historial", currentRoute == Routes.History, isMenuFocused) { navController.navigate(Routes.History) { launchSingleTop = true } }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (isMenuFocused) {
            Text("SISTEMA", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        }
        MenuButton("👤", "Mi Cuenta", currentRoute == Routes.Account, isMenuFocused) { navController.navigate(Routes.Account) { launchSingleTop = true } }
        MenuButton("🎧", "Soporte", currentRoute == Routes.Support, isMenuFocused) { navController.navigate(Routes.Support) { launchSingleTop = true } }
        MenuButton("⚙️", "Ajustes", currentRoute == Routes.Settings, isMenuFocused) { navController.navigate(Routes.Settings) { launchSingleTop = true } }
    }
}

@Composable
fun MenuButton(icon: String, title: String, isSelected: Boolean, isExpanded: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f)
    val bgColor = if (isFocused) Color.White.copy(alpha = 0.15f) else if (isSelected) Color(0xFFE50914).copy(alpha = 0.8f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp)
            .scale(scale)
            .background(bgColor, RoundedCornerShape(8.dp))
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 18.sp)
        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = if (isFocused || isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
