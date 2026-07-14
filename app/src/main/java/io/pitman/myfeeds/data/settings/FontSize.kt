package io.pitman.myfeeds.data.settings

/**
 * Ordinal matches the raw int values the WinPhone app stored for ListFontSize/FeedListFontSize
 * (0/1/2), and the "30pt"/"35pt"/"40pt" strings it stored for ArticleFontSize.
 */
enum class FontSize {
    SMALL,
    NORMAL,
    LARGE,
}
