package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.icon.DeleteIcon
import com.maxixa.lemon.ui.icon.DuplicateIcon
import com.maxixa.lemon.ui.icon.QuantizeIcon
import com.maxixa.lemon.ui.theme.*

@Composable
fun PianoRollActionBar(
    selectedCount: Int,
    hasClipboard: Boolean,
    onQuantize: () -> Unit,
    onTransform: () -> Unit,
    onGroove: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(BgRaised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedCount > 0) {
            Text(
                "$selectedCount",
                color = AccentCyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            ActionButton(label = "1/4", onClick = onQuantize) {
                QuantizeIcon(TextPrimary, 16.dp)
            }

            ActionButton(label = "T", onClick = onTransform) {
                Text("\u21c4", color = AccentOrange, fontSize = 16.sp,
                     fontWeight = FontWeight.Bold)
            }

            ActionButton(label = "G", onClick = onGroove) {
                Text("\u266b", color = AccentAmber, fontSize = 16.sp,
                     fontWeight = FontWeight.Bold)
            }

            ActionButton(label = "Dup", onClick = onDuplicate) {
                DuplicateIcon(TextPrimary, 16.dp)
            }

            ActionButton(label = "Copy", onClick = onCopy) {
                Text("\u2398", color = AccentCyan, fontSize = 16.sp,
                     fontWeight = FontWeight.Bold)
            }

            ActionButton(label = "Del", onClick = onDelete) {
                DeleteIcon(AccentRed, 16.dp)
            }
        }

        if (hasClipboard) {
            ActionButton(label = "Paste", onClick = onPaste) {
                Text("\u2397", color = AccentAmber, fontSize = 16.sp,
                     fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(36.dp).clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Text("\u00d7", color = TextMuted, fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.height(36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            color = TextMuted, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
