package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

data class ReaderUiState(
    val manga: Manga? = null,
    val viewerChapters: ViewerChapters? = null,
    val isLoadingAdjacentChapter: Boolean = false,
    val isLoadingPreviousChapter: Boolean = false,
    val isLoadingNextChapter: Boolean = false,
    val isNextChapterAvailable: Boolean = false,
    val isPreviousChapterAvailable: Boolean = false,
    val lastPage: Int? = null,
    val currentPage: String = "1",
    val totalPages: UInt = 1u,
    val isRTL: Boolean = true,
    val menuVisible: Boolean = true,
//    val isScrollingThroughPagesOrChapters: Boolean = true,
)
