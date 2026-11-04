package com.signai.webview.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.signai.webview.domain.Character
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Character picker (spec `PER-*` / `SET-10`): trigger with a chevron that
 * rotates when open, and an anchored menu with a color dot per character and a
 * check on the selected option.
 */
@Composable
fun CharacterPicker(
    current: Character,
    onPick: (Character) -> Unit,
    /**
     * El avatar solo se puede cambiar en reposo absoluto (owner, 2026-10-24).
     *
     * Deshabilitado, el trigger no abre el menú: mostrarlo y dejar elegir una
     * opción que [onPick] va a rechazar sería peor que no ofrecerlo.
     */
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    if (!enabled && expanded) expanded = false
    SignAiDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        maxHeight = 260.dp,
        trigger = {
            Surface(
                onClick = { expanded = !expanded },
                enabled = enabled,
                shape = SignAiShapes.cornerTile,
                color = SignAiTheme.colors.background,
                border = BorderStroke(
                    1.dp,
                    if (expanded) SignAiTheme.colors.strong else SignAiTheme.colors.outline,
                ),
                modifier = Modifier
                    .width(SignAiSizes.characterTriggerWidth)
                    .height(SignAiSizes.characterTriggerHeight)
                    .alpha(if (enabled) 1f else 0.45f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SignAiSizes.characterTriggerHeight)
                        .padding(start = 14.dp, end = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SignAiText(
                        text = current.label,
                        style = SignAiTheme.typography.body2,
                        color = SignAiTheme.colors.text,
                    )
                    SignAiIcon(
                        imageVector = SignAiIcons.CaretDown,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAiTheme.colors.muted,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        },
    ) {
        Character.entries.forEach { character ->
            val selected = character == current
            val menuShape = SignAiShapes.cornerMenu
            val ringColor = SignAiTheme.colors.background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(menuShape)
                    .background(
                        if (selected) SignAiTheme.colors.soft else Color.Transparent,
                        menuShape,
                    )
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = {
                            onPick(character)
                            expanded = false
                        },
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .drawBehind {
                            // Prototype `.character-dot { box-shadow: 0 0 0 3px
                            // var(--background) }` — a 3dp ring, no layout cost.
                            drawCircle(
                                color = ringColor,
                                radius = size.minDimension / 2f + 3.dp.toPx(),
                            )
                        }
                        .background(Color(character.colorArgb), CircleShape),
                )
                SignAiText(
                    text = character.label,
                    style = SignAiTheme.typography.body2,
                    color = SignAiTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    // `strong` (el acento azul) sobre `soft` da 2.3:1: la marca de
                    // selección tiene que verse, y para eso va el color del texto.
                    SignAiIcon(
                        imageVector = SignAiIcons.Check,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAiTheme.colors.text,
                    )
                }
            }
        }
    }
}
