package com.example.visionbridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionbridge.ui.theme.*

/**
 * Standardized, high-contrast action card designed specifically for low-vision accessibility.
 * Features a dedicated icon container, large touch targets (min 68dp height),
 * clear typography hierarchy, and TalkBack content descriptions.
 */
@Composable
fun ActionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEmergency: Boolean = false,
    isDestructive: Boolean = isEmergency,
    badgeText: String? = null,
    backgroundColor: Color = if (isEmergency || isDestructive) EmergencyDim else BgCard,
    containerColor: Color = backgroundColor,
    textColor: Color = if (isEmergency || isDestructive) Emergency else TextPrimary,
    contentColor: Color = textColor,
    borderColor: Color = if (isEmergency || isDestructive) Emergency else Border
) {
    val emergency = isEmergency || isDestructive
    val finalBg = if (containerColor != BgCard && containerColor != EmergencyDim) containerColor else if (emergency) EmergencyDim else BgCard
    val finalFg = if (contentColor != TextPrimary && contentColor != Emergency) contentColor else if (emergency) Emergency else TextPrimary
    val finalBorder = if (borderColor != Border && borderColor != Emergency) borderColor else if (emergency) Emergency else Border

    val talkbackDescription = androidx.compose.ui.res.stringResource(
        com.example.visionbridge.R.string.action_card_tap_to_open,
        title,
        subtitle
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = finalBg,
        border = BorderStroke(
            width = if (emergency) 2.dp else 1.dp,
            color = finalBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick,
                onClickLabel = title
            )
            .semantics {
                role = Role.Button
                contentDescription = talkbackDescription
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (emergency) Emergency.copy(alpha = 0.2f) else AccentDim,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = finalFg,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentDim
                        ) {
                            Text(
                                text = badgeText,
                                color = Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emergency) TextPrimary.copy(alpha = 0.9f) else TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Chevron indicator
            Text(
                text = "›",
                color = if (emergency) Emergency else TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
    }
}
