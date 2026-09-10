package com.example.kaspawallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun KaspaQrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    qrColor: Color = Color(0xFF091114),
    backgroundColor: Color = Color.White
) {
    val matrix = remember(content) {
        generateRealQrMatrix(content)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridCount = matrix.size
            if (gridCount > 0) {
                val cellSize = this.size.width / gridCount
                for (row in 0 until gridCount) {
                    for (col in 0 until gridCount) {
                        if (matrix[row][col]) {
                            drawRect(
                                color = qrColor,
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize + 0.5f, cellSize + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateRealQrMatrix(data: String): Array<BooleanArray> {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 29, 29, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val matrix = Array(height) { BooleanArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                matrix[y][x] = bitMatrix.get(x, y)
            }
        }
        matrix
    } catch (e: Exception) {
        Array(21) { BooleanArray(21) { false } }
    }
}
