package com.example.visionbridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.R
import com.example.visionbridge.ui.theme.*

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String
)

val SUPPORTED_LANGUAGES = listOf(
    LanguageOption(code = "en", nativeName = "English", englishName = "English"),
    LanguageOption(code = "hi", nativeName = "हिन्दी", englishName = "Hindi"),
    LanguageOption(code = "mr", nativeName = "मराठी", englishName = "Marathi")
)

/**
 * Compact, accessible language selector component.
 * Displays a clean pill button in the top bar and opens a high-contrast modal dialog
 * with large touch targets (min 56dp) when clicked.
 */
@Composable
fun LanguageSelector(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    val currentLang = SUPPORTED_LANGUAGES.find { it.code == currentLanguageCode }
        ?: SUPPORTED_LANGUAGES.first()

    // Compact trigger button
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        border = BorderStroke(1.dp, Border),
        modifier = modifier
            .height(40.dp)
            .clickable(
                onClick = { showDialog = true },
                onClickLabel = "Change application language"
            )
            .semantics {
                contentDescription = "Language: ${currentLang.nativeName}. Tap to change."
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentLang.nativeName,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.language_label),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SUPPORTED_LANGUAGES.forEach { lang ->
                        val isSelected = lang.code == currentLanguageCode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) AccentDim else BgSecondary,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Accent else Border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .clickable {
                                    onLanguageSelected(lang.code)
                                    showDialog = false
                                }
                                .semantics {
                                    contentDescription = "${lang.nativeName}, ${lang.englishName}${if (isSelected) ", Selected" else ""}"
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        color = if (isSelected) Accent else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = lang.englishName,
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
