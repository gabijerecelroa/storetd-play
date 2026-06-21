package com.storetd.play.ui.streamvault.theme

import com.storetd.play.ui.streamvault.design.AppSpacing
import com.storetd.play.ui.streamvault.design.LocalAppSpacing

typealias Spacing = AppSpacing

val LocalSpacing = LocalAppSpacing

fun defaultSpacing(): Spacing = AppSpacing()
