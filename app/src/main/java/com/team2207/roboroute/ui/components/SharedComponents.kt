package com.team2207.roboroute.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.team2207.roboroute.R

@Composable
fun FullScreenImage(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.field_2026),
        contentDescription = "Full screen background image",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
