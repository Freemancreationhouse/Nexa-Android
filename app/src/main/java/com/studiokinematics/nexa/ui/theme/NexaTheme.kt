package com.studiokinematics.nexa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NexaBg=Color(0xFF060810)
val NexaPanel=Color(0xFF101522)
val NexaPanel2=Color(0xFF171D2D)
val NexaPurple=Color(0xFFA855F7)
val NexaPink=Color(0xFFEC4899)
val NexaCyan=Color(0xFF22D3EE)
val NexaBright=Color(0xFFF8FAFC)
val NexaSoft=Color(0xFFD8DEEA)
val NexaMuted=Color(0xFFA8B1C3)
val NexaDanger=Color(0xFFFF7B8B)

private val colors=darkColorScheme(
    background=NexaBg,surface=NexaPanel,surfaceVariant=NexaPanel2,
    primary=NexaPurple,secondary=NexaCyan,tertiary=NexaPink,
    onBackground=NexaBright,onSurface=NexaBright,onPrimary=Color.White
)

@Composable fun NexaTheme(content:@Composable()->Unit)=MaterialTheme(colorScheme=colors,content=content)
