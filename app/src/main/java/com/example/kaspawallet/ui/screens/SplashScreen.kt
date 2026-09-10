package com.example.kaspawallet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaspawallet.R
import com.example.kaspawallet.ui.theme.KaspaBackground
import com.example.kaspawallet.ui.theme.KaspaTextPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // Logo Floating Wave Animation
    val infiniteTransition = rememberInfiniteTransition(label = "SplashWave")
    val logoOffsetY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoFloat"
    )

    // Hand Waving Animation
    val handRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HandWave"
    )

    // Typewriter Effect
    val fullText = "Hello "
    var displayedText by remember { mutableStateOf("") }
    var showHand by remember { mutableStateOf(false) }
    
    // Overall alpha fade in/out
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, tween(600))
        
        delay(200) // Small pause before typing
        
        // Typewriter effect
        for (i in fullText.indices) {
            displayedText = fullText.substring(0, i + 1)
            delay(90) // Typing speed
        }
        
        showHand = true
        
        delay(1600) // Hold screen to show animation
        
        // Fade out
        alphaAnim.animateTo(0f, tween(500))
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaspaBackground)
            .alpha(alphaAnim.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Logo
            Image(
                painter = painterResource(id = R.drawable.ic_kaspa_teal_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        translationY = logoOffsetY
                    }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.height(48.dp) // Fixed height to prevent jumping
            ) {
                // Typewriter Text (Italic / "Italian style")
                Text(
                    text = displayedText,
                    color = KaspaTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp
                )
                
                // Waving Hand (Only show when Hello is fully typed)
                if (showHand) {
                    Text(
                        text = "👋",
                        fontSize = 34.sp,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = handRotation
                            transformOrigin = TransformOrigin(0.7f, 0.9f) // Pivot near the wrist
                        }
                    )
                }
            }
        }
    }
}
