package eu.kanade.tachiyomi.ui.reader.chapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReadMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.databinding.ReaderChaptersSheetBinding
import eu.kanade.tachiyomi.ui.common.IconButtonWithTooltip
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderUiState
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ReaderChapterSheetIcon(
    val tooltipText: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ReaderChapterSheet(
    viewModel: ReaderViewModel,
    readerUiState: ReaderUiState,
    chapterSheetIcons: List<ReaderChapterSheetIcon>,
    modifier: Modifier = Modifier,
) {
    // Get local density from composable
    val localDensity = LocalDensity.current

    val scaffoldState = rememberBottomSheetScaffoldState()
    var lazyRowHeight by remember { mutableStateOf(0.dp) }
    val coroutineScope = rememberCoroutineScope()
    var chapterItems = emptyList<Chapter>()

    BottomSheetScaffold(
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight + lazyRowHeight,
        sheetContent = {
            Column {
                LazyRow(
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        lazyRowHeight = with(localDensity) { layoutCoordinates.size.height.toDp() }
                    },
                ) {
                    items(chapterSheetIcons) { item ->
                        IconButtonWithTooltip(
                            tooltipText = item.tooltipText,
                            icon = item.icon,
                            onClick = item.onClick,
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        chapterItems = viewModel.getChaptersCompose()
                    }
                }

                LazyColumn {
                    items(chapterItems) { chapter ->
                        ReaderChapterItem(chapter, readerUiState.manga, chapter.id == readerUiState.viewerChapters?.currChapter?.chapter?.id)
                    }
                }
            }
        },
        scaffoldState = scaffoldState,
        modifier = modifier,
    ) {
    }
}

@Composable
@Preview
fun ReaderChapterSheetPreview() {
    val viewModel: ReaderViewModel = viewModel()
    ReaderChapterSheet(
        viewModel = viewModel,
        readerUiState = viewModel.state.collectAsState().value,
        chapterSheetIcons = listOf(
            ReaderChapterSheetIcon(
                stringResource(R.string.view_chapters),
                icon = Icons.Default.FormatListNumbered,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.open_in_webview),
                icon = Icons.Default.Public,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.reading_mode),
                icon = Icons.Default.PhonelinkSetup,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.rotation),
                icon = Icons.Default.ScreenRotation,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.crop_borders),
                icon = Icons.Default.Fullscreen,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.double_pages),
                icon = Icons.AutoMirrored.Filled.MenuBook,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.shift_one_page_over),
                icon = Icons.AutoMirrored.Filled.ReadMore,
            ) {},
            ReaderChapterSheetIcon(
                stringResource(R.string.display_options),
                icon = Icons.Default.Tune,
            ) {},
        ),
    )
}

class ReaderChapterSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    var sheetBehavior: BottomSheetBehavior<View>? = null
    lateinit var viewModel: ReaderViewModel
    var adapter: FastAdapter<ReaderChapterItem>? = null
    private val itemAdapter = ItemAdapter<ReaderChapterItem>()
    var selectedChapterId = -1L

    var loadingPos = 0
    lateinit var binding: ReaderChaptersSheetBinding
    var lastScale = 1f

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = ReaderChaptersSheetBinding.bind(this)
    }

    fun setup(activity: ReaderActivity) {
        viewModel = activity.viewModel
        val fullPrimary = activity.getResourceColor(R.attr.colorSurface)

        val primary = ColorUtils.setAlphaComponent(fullPrimary, 200)

        val hasLightNav =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || activity.isInNightMode()
        val navPrimary = ColorUtils.setAlphaComponent(
            if (hasLightNav) {
                fullPrimary
            } else {
                Color.BLACK
            },
            200,
        )
        sheetBehavior = BottomSheetBehavior.from(this)
        binding.chaptersButton.setOnClickListener {
            if (sheetBehavior.isExpanded()) {
                sheetBehavior?.collapse()
            } else {
                sheetBehavior?.expand()
            }
        }

        post {
            binding.chapterRecycler.alpha = if (sheetBehavior.isExpanded()) 1f else 0f
            binding.chapterRecycler.isClickable = sheetBehavior.isExpanded()
            binding.chapterRecycler.isFocusable = sheetBehavior.isExpanded()
            val canShowNav = viewModel.getCurrentChapter()?.pages?.size ?: 1 > 1
            if (canShowNav) {
                activity.binding.readerNav.root.isVisible = sheetBehavior.isCollapsed()
            }
        }

        sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    binding.root.isVisible = true
                    binding.pill.alpha = (1 - max(0f, progress)) * 0.25f
                    val trueProgress = max(progress, 0f)
                    activity.binding.readerNav.root.alpha = (1 - abs(progress)).coerceIn(0f, 1f)
                    backgroundTintList =
                        ColorStateList.valueOf(lerpColor(primary, fullPrimary, trueProgress))
                    binding.chapterRecycler.alpha = trueProgress
                    if (activity.sheetManageNavColor && progress > 0f) {
                        activity.window.navigationBarColor =
                            lerpColor(
                                ColorUtils.setAlphaComponent(
                                    navPrimary,
                                    if (hasLightNav) 0 else 179,
                                ),
                                navPrimary,
                                trueProgress,
                            )
                    }
                    if (lastScale != 1f && scaleY != 1f) {
                        val scaleProgress = ((1f - progress) * (1f - lastScale)) + lastScale
                        scaleX = scaleProgress
                        scaleY = scaleProgress
                        for (i in 0 until childCount) {
                            val childView = getChildAt(i)
                            childView.scaleY = scaleProgress
                        }
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    val canShowNav = (viewModel.getCurrentChapter()?.pages?.size ?: 1) > 1
                    if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                        sheetBehavior?.isHideable = false
                        (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                            adapter?.getPosition(viewModel.getCurrentChapter()?.chapter?.id ?: 0L)
                                ?: 0,
                            binding.chapterRecycler.height / 2 - 30.dpToPx,
                        )
                        if (canShowNav) {
                            activity.binding.readerNav.root.isVisible = true
                        }
                        activity.binding.readerNav.root.alpha = 1f
                    }
                    if (state == BottomSheetBehavior.STATE_DRAGGING || state == BottomSheetBehavior.STATE_SETTLING) {
                        if (canShowNav) {
                            activity.binding.readerNav.root.isVisible = true
                        }
                    }
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        if (canShowNav) {
                            activity.binding.readerNav.root.isInvisible = true
                        }
                        activity.binding.readerNav.root.alpha = 0f
                        binding.chapterRecycler.alpha = 1f
                        if (activity.sheetManageNavColor) {
                            activity.window.navigationBarColor =
                                navPrimary
                        }
                    }
                    if (state == BottomSheetBehavior.STATE_HIDDEN) {
                        activity.binding.readerNav.root.alpha = 0f
                        if (canShowNav) {
                            activity.binding.readerNav.root.isInvisible = true
                        }
                        binding.root.isInvisible = true
                    } else if (binding.root.isVisible) {
                        binding.root.isVisible = true
                    }
                    binding.chapterRecycler.isClickable =
                        state == BottomSheetBehavior.STATE_EXPANDED
                    binding.chapterRecycler.isFocusable =
                        state == BottomSheetBehavior.STATE_EXPANDED
                    activity.reEnableBackPressedCallBack()

                    if ((
                        state == BottomSheetBehavior.STATE_COLLAPSED ||
                            state == BottomSheetBehavior.STATE_EXPANDED ||
                            state == BottomSheetBehavior.STATE_HIDDEN
                        ) &&
                        scaleY != 1f
                    ) {
                        scaleX = 1f
                        scaleY = 1f
                        pivotY = 0f
                        translationX = 0f
                        for (i in 0 until childCount) {
                            val childView = getChildAt(i)
                            childView.scaleY = 1f
                        }
                        lastScale = 1f
                    }
                }
            },
        )

        adapter = FastAdapter.with(itemAdapter)
        binding.chapterRecycler.adapter = adapter
        adapter?.onClickListener = { _, _, item, position ->
            if (!sheetBehavior.isExpanded() || activity.isLoading) {
                false
            } else {
                if (item.chapter.id != viewModel.getCurrentChapter()?.chapter?.id) {
                    activity.binding.readerNav.leftChapter.isInvisible = true
                    activity.binding.readerNav.rightChapter.isInvisible = true
                    activity.isScrollingThroughPagesOrChapters = true

                    loadingPos = position
                    val itemView =
                        (binding.chapterRecycler.findViewHolderForAdapterPosition(position) as? ReaderChapterItem.ViewHolder)?.binding
                    itemView?.bookmarkImage?.isVisible = false
                    itemView?.progress?.isVisible = true
                    activity.lifecycleScope.launch {
                        activity.loadChapter(item.chapter)
                    }
                }
                true
            }
        }
        adapter?.addEventHook(
            object : ClickEventHook<ReaderChapterItem>() {
                override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                    return if (viewHolder is ReaderChapterItem.ViewHolder) {
                        viewHolder.binding.bookmarkButton
                    } else {
                        null
                    }
                }

                override fun onClick(
                    v: View,
                    position: Int,
                    fastAdapter: FastAdapter<ReaderChapterItem>,
                    item: ReaderChapterItem,
                ) {
                    if (!activity.isLoading && sheetBehavior.isExpanded()) {
                        viewModel.toggleBookmark(item.chapter)
                        refreshList()
                    }
                }
            },
        )

        backgroundTintList = ColorStateList.valueOf(
            if (!sheetBehavior.isExpanded()) {
                primary
            } else {
                fullPrimary
            },
        )

        binding.chapterRecycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING
                    ) {
                        sheetBehavior?.isDraggable = true
                    } else {
                        sheetBehavior?.isDraggable = !recyclerView.canScrollVertically(-1)
                    }
                }
            },
        )

        binding.chapterRecycler.layoutManager = LinearLayoutManager(context)
        refreshList()
    }

    fun resetChapter() {
        val itemView =
            (binding.chapterRecycler.findViewHolderForAdapterPosition(loadingPos) as? ReaderChapterItem.ViewHolder)?.binding
        itemView?.bookmarkImage?.isVisible = true
        itemView?.progress?.isVisible = false
    }

    fun refreshList() {
        launchUI {
            val chapters = viewModel.getChapters()

            selectedChapterId = chapters.find { it.isCurrent }?.chapter?.id ?: -1L
            itemAdapter.clear()
            itemAdapter.add(chapters)

            (binding.chapterRecycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                adapter?.getPosition(viewModel.getCurrentChapter()?.chapter?.id ?: 0L) ?: 0,
                binding.chapterRecycler.height / 2 - 30.dpToPx,
            )
        }
    }

    fun lerpColor(colorStart: Int, colorEnd: Int, percent: Float): Int {
        val perc = (percent * 100).roundToInt()
        return Color.argb(
            lerpColorCalc(Color.alpha(colorStart), Color.alpha(colorEnd), perc),
            lerpColorCalc(Color.red(colorStart), Color.red(colorEnd), perc),
            lerpColorCalc(Color.green(colorStart), Color.green(colorEnd), perc),
            lerpColorCalc(Color.blue(colorStart), Color.blue(colorEnd), perc),
        )
    }

    fun lerpColorCalc(colorStart: Int, colorEnd: Int, percent: Int): Int {
        return (
            min(colorStart, colorEnd) * (100 - percent) + max(
                colorStart,
                colorEnd,
            ) * percent
            ) / 100
    }
}
