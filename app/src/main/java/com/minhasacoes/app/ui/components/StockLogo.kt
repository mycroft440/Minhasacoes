package com.minhasacoes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minhasacoes.app.ui.theme.AppTheme

/** Endereços de logo, em ordem de tentativa. */
fun logoUrls(symbol: String): List<String> {
    val s = symbol.trim().uppercase()
    return if (s.endsWith(".SA")) {
        val ticker = s.removeSuffix(".SA")
        listOf(
            "https://icons.brapi.dev/icons/$ticker.svg",
            "https://financialmodelingprep.com/image-stock/$s.png",
        )
    } else {
        listOf("https://financialmodelingprep.com/image-stock/$s.png")
    }
}

/**
 * Logo da empresa em um quadrado arredondado (como nas corretoras). Tenta as fontes em
 * sequência e, se nenhuma tiver o logo, mostra as letras iniciais do ticker.
 */
@Composable
fun StockLogo(symbol: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val urls = remember(symbol) { logoUrls(symbol) }
    var index by remember(symbol) { mutableIntStateOf(0) }
    var loaded by remember(symbol) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.28f)
    val context = LocalContext.current

    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (loaded) Color.White else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, AppTheme.market.cardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Initials(symbol, size)
        if (index < urls.size) {
            val request = remember(urls[index]) {
                ImageRequest.Builder(context)
                    .data(urls[index])
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = {
                    loaded = false
                    index += 1
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.12f),
            )
        }
    }
}

@Composable
private fun Initials(symbol: String, size: Dp) {
    val ticker = symbol.removeSuffix(".SA")
    val letters = ticker.filter { it.isLetter() }.take(2).ifEmpty { ticker.take(2) }
    Text(
        letters,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = (size.value * 0.3f).sp,
    )
}
