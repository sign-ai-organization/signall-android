package com.signall.app.ui.components

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
import com.signall.app.domain.Character
import com.signall.app.ui.designsystem.components.SignAllDropdown
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.theme.SignAllTheme

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
    SignAllDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        maxHeight = 260.dp,
        trigger = {
            Surface(
                onClick = { expanded = !expanded },
                enabled = enabled,
                shape = SignAllShapes.cornerTile,
                color = SignAllTheme.colors.background,
                border = BorderStroke(
                    1.dp,
                    if (expanded) SignAllTheme.colors.strong else SignAllTheme.colors.outline,
                ),
                modifier = Modifier
                    .width(SignAllSizes.characterTriggerWidth)
                    .height(SignAllSizes.characterTriggerHeight)
                    .alpha(if (enabled) 1f else 0.45f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SignAllSizes.characterTriggerHeight)
                        .padding(start = 14.dp, end = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SignAllText(
                        text = current.label,
                        style = SignAllTheme.typography.body2,
                        color = SignAllTheme.colors.text,
                    )
                    SignAllIcon(
                        imageVector = SignAllIcons.CaretDown,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAllTheme.colors.muted,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        },
    ) {
        Character.entries.forEach { character ->
            val selected = character == current
            val menuShape = SignAllShapes.cornerMenu
            val ringColor = SignAllTheme.colors.background
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(menuShape)
                    .background(
                        if (selected) SignAllTheme.colors.soft else Color.Transparent,
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
                SignAllText(
                    text = character.label,
                    style = SignAllTheme.typography.body2,
                    color = SignAllTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    // `strong` (el acento azul) sobre `soft` da 2.3:1: la marca de
                    // selección tiene que verse, y para eso va el color del texto.
                    SignAllIcon(
                        imageVector = SignAllIcons.Check,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAllTheme.colors.text,
                    )
                }
            }
        }
    }
}
