package eu.kanade.tachiyomi.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun IconButtonWithTooltip(
    tooltipText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Get local density from composable
    val localDensity = LocalDensity.current
    var buttonHeight by remember { mutableStateOf(0.dp) }

    val tooltipPosition =
        TooltipDefaults.rememberPlainTooltipPositionProvider(spacingBetweenTooltipAndAnchor = (buttonHeight))
    val tooltipState = rememberTooltipState(isPersistent = false)

    TooltipBox(
        tooltip = {
            PlainTooltip(
                containerColor = colorResource(R.color.surface_alpha),
                contentColor = colorResource(R.color.colorOnSurface),
                modifier = Modifier.padding(4.dp),
            ) { Text(tooltipText) }
        },
        positionProvider = tooltipPosition,
        state = tooltipState,
        modifier = modifier,
    ) {
        IconButton(
            onClick = { onClick() },
            modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                buttonHeight = with(localDensity) { layoutCoordinates.size.height.toDp() }
            },
        ) {
            Icon(imageVector = icon, contentDescription = tooltipText)
        }
    }
}

@Composable
@Preview
fun IconButtonWithTooltipPreview() {
    IconButtonWithTooltip("tooltipText", Icons.Default.Lens, {})
}
