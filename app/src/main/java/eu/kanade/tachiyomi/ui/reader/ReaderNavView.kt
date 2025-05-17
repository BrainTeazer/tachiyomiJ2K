package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.common.IconButtonWithTooltip

@Composable
fun BoxedCircularProgressBar(height: Dp, width: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(height).width(width)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
fun ReaderNavView(
    readerUiState: ReaderUiState,
    onSliderInput: () -> Unit,
    onSliderInputStopped: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onValueChanged: (value: Int) -> Unit,
    // TODO: Remove once Material3 Theme is implemented in Compose
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentPage = readerUiState.currentPage
    val isRTL = readerUiState.isRTL
    val totalPages = readerUiState.totalPages

    // Get local density from composable
    val localDensity = LocalDensity.current
    var buttonHeight by remember { mutableStateOf(0.dp) }
    var buttonWidth by remember { mutableStateOf(0.dp) }

    /*
        HACK: Slider does not have a signal for when it is being
        interacted with. So interactionSource is used instead.
     */
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    if (isPressed || isDragged) {
        onSliderInput()
    }

    if ((readerUiState.menuVisible || isPressed || isDragged) && totalPages > 1u) {
        Box(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.rotate(if (isRTL) 180f else 0f),
            ) {
                if (readerUiState.isLoadingPreviousChapter) {
                    BoxedCircularProgressBar(buttonWidth, buttonHeight)
                } else {
                    IconButtonWithTooltip(
                        tooltipText = stringResource(R.string.previous_chapter),
                        icon = Icons.Default.SkipPrevious,
                        onClick = { if (isRTL) onNextChapter() else onPreviousChapter() },
                        enabled = readerUiState.isPreviousChapterAvailable,
                        modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                            buttonHeight =
                                with(localDensity) { layoutCoordinates.size.height.toDp() }
                            buttonWidth = with(localDensity) { layoutCoordinates.size.width.toDp() }
                        },
                    )
                }

                Text(
                    text = currentPage,
                    modifier = Modifier.rotate(if (isRTL) 180f else 0f),
                    color = foregroundColor,
                )

                /*
                    TODO(Add Haptics)
                    TODO(Add text label above thumb when changing value)
                    TODO(Fix steps for double page)
                 */
                Slider(
                    value = currentPage.substringBefore('-').toFloat(),
                    valueRange = 1f..totalPages.toFloat(),
                    steps = totalPages.toInt() - 2,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .weight(1f),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            thumbSize = DpSize(8.dp, 28.dp),
                        )
                    },
                    onValueChange = { onValueChanged(it.fastRoundToInt()) },
                    onValueChangeFinished = { onSliderInputStopped() },
                )

                Text(
                    text = totalPages.toString(),
                    modifier = Modifier.rotate(if (isRTL) 180f else 0f),
                    color = foregroundColor,
                )

                if (readerUiState.isLoadingNextChapter) {
                    BoxedCircularProgressBar(buttonWidth, buttonHeight)
                } else {
                    IconButtonWithTooltip(
                        tooltipText = stringResource(R.string.next_chapter),
                        icon = Icons.Default.SkipNext,
                        onClick = { if (isRTL) onPreviousChapter() else onNextChapter() },
                        enabled = readerUiState.isNextChapterAvailable,
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun ReaderNavViewPreview() {
    val viewModel: ReaderViewModel = viewModel()
    ReaderNavView(viewModel.state.collectAsState().value, {}, {}, {}, {}, {}, Color(2))
}

class ReaderNavView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {
    override fun canScrollVertically(direction: Int): Boolean {
        return true
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getScrollCaptureHint(): Int {
        return SCROLL_CAPTURE_HINT_EXCLUDE
    }
}
