package com.storetd.play.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val width by animateDpAsState(if (isMenuFocused) 180.dp else 60.dp)

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF000000).copy(alpha = 0.6f))
            .padding(vertical = 32.dp)
            .onFocusChanged { isMenuFocused = it.hasFocus },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MenuButton("🏠", "Inicio", currentRoute == Routes.Home, isMenuFocused) { navController.navigate(Routes.Home) { launchSingleTop = true } }
        MenuButton("📺", "TV Live", currentRoute == Routes.LiveTv, isMenuFocused) { navController.navigate(Routes.LiveTv) { launchSingleTop = true } }
        MenuButton("🎬", "Películas", currentRoute == Routes.Movies, isMenuFocused) { navController.navigate(Routes.Movies) { launchSingleTop = true } }
        MenuButton("🍿", "Series", currentRoute == Routes.Series, isMenuFocused) { navController.navigate(Routes.Series) { launchSingleTop = true } }
        Spacer(modifier = Modifier.weight(1f))
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
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .scale(scale)
            .background(bgColor, RoundedCornerShape(8.dp))
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = if (isFocused || isSelected) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
