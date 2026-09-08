package dev.neekolor.appcanvasfaker.data.repository

interface SettingsRepository {
    var uiMode: String
    var checkUpdate: Boolean
    var themeMode: Int
    var miuixMonet: Boolean
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var enablePredictiveBack: Boolean
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var enableNavigationBadge: Boolean
    var pageScale: Float
    var ssaidEnabled: Boolean
    /** 行为预设下拉记忆位：-1 = 跟随开关派生，0/1/2 = 上次显式选择。 */
    var presetSelected: Int
}