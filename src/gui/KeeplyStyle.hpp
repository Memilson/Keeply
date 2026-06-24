#pragma once
#include <QString>

namespace keeply::style {

#define CLR_SIDEBAR_BG        "#1A2B3C"
#define CLR_SIDEBAR_HOVER     "#233547"
#define CLR_SIDEBAR_ACTIVE    "#2D4860"
#define CLR_SIDEBAR_TEXT      "#9BB1C8"
#define CLR_SIDEBAR_TEXT_A    "#FFFFFF"
#define CLR_SIDEBAR_ACCENT    "#007FFF"
#define CLR_CONTENT_BG        "#F5F7FA"
#define CLR_CARD_BG           "#FFFFFF"
#define CLR_CARD_BORDER       "#E1E4E8"
#define CLR_TEXT_PRIMARY      "#1A2332"
#define CLR_TEXT_SECONDARY    "#6B7A8D"
#define CLR_TEXT_MUTED        "#9CA8B8"
#define CLR_ACCENT            "#007FFF"
#define CLR_ACCENT_HOVER      "#006FE6"
#define CLR_ACCENT_PRESS      "#005BC2"
#define CLR_ACCENT_LIGHT      "#E6F5FC"
#define CLR_SUCCESS           "#107C10"
#define CLR_WARNING           "#F7630C"
#define CLR_DANGER            "#C50F1F"
#define CLR_DANGER_HOVER      "#A4262C"

constexpr int SIDEBAR_WIDTH       = 220;
constexpr int SIDEBAR_ITEM_H      = 46;
constexpr int SIDEBAR_LOGO_H      = 68;
constexpr int HEADER_HEIGHT       = 64;
constexpr int LOG_HEIGHT          = 130;
constexpr int CARD_RADIUS         = 6;
constexpr int BTN_RADIUS          = 4;
constexpr int CONTENT_PADDING     = 32;
constexpr int MIN_WINDOW_W        = 980;
constexpr int MIN_WINDOW_H        = 620;

inline QString globalStylesheet() {
    return QStringLiteral(R"(
* {
    font-family: "Segoe UI", "Roboto", "Helvetica Neue", sans-serif;
    outline: none;
}

QMainWindow {
    background: )" CLR_CONTENT_BG R"(;
}

/* ── Sidebar ───────────────────────────────────────── */
#sidebar {
    background: )" CLR_SIDEBAR_BG R"(;
    min-width: 220px;
    max-width: 220px;
}

#sidebarLogo {
    color: #FFFFFF;
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 3px;
    padding: 24px 24px 4px 24px;
}

#sidebarTagline {
    color: #5A7A9A;
    font-size: 10px;
    font-weight: 400;
    letter-spacing: 0.5px;
    padding: 0 24px 18px 24px;
}

QPushButton.navItem {
    background: transparent;
    border: none;
    border-left: 3px solid transparent;
    color: )" CLR_SIDEBAR_TEXT R"(;
    font-size: 12px;
    font-weight: 500;
    text-align: left;
    padding: 11px 20px 11px 20px;
    min-height: 40px;
}

QPushButton.navItem:hover {
    background: )" CLR_SIDEBAR_HOVER R"(;
    color: #C8D9EA;
}

QPushButton.navItem:checked,
QPushButton.navItem[active="true"] {
    background: )" CLR_SIDEBAR_ACTIVE R"(;
    border-left: 3px solid )" CLR_SIDEBAR_ACCENT R"(;
    color: )" CLR_SIDEBAR_TEXT_A R"(;
    font-weight: 600;
}

/* ── Page Header ───────────────────────────────────── */
#pageHeader {
    background: #FFFFFF;
    border-bottom: 1px solid )" CLR_CARD_BORDER R"(;
}

#pageTitle {
    color: )" CLR_TEXT_PRIMARY R"(;
    font-size: 20px;
    font-weight: 600;
    padding: 20px 32px 4px 32px;
}

#pageSubtitle {
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 12px;
    padding: 0 32px 16px 32px;
}

/* ── Content area ──────────────────────────────────── */
#contentStack {
    background: )" CLR_CONTENT_BG R"(;
}

/* ── Scroll areas — force light background ─────────── */
QScrollArea {
    background: )" CLR_CONTENT_BG R"(;
    border: none;
}

QScrollArea > QWidget > QWidget {
    background: )" CLR_CONTENT_BG R"(;
}

/* ── Tree/List header ──────────────────────────────── */
QHeaderView {
    background: #F0F3F7;
    border: none;
}

QHeaderView::section {
    background: #F0F3F7;
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.5px;
    padding: 6px 10px;
    border: none;
    border-bottom: 1px solid )" CLR_CARD_BORDER R"(;
    border-right: 1px solid )" CLR_CARD_BORDER R"(;
}

QHeaderView::section:last {
    border-right: none;
}

/* ── Cards ─────────────────────────────────────────── */
QFrame.card {
    background: )" CLR_CARD_BG R"(;
    border: none;
    border-radius: 6px;
}

QLabel.cardTitle {
    color: )" CLR_TEXT_PRIMARY R"(;
    font-size: 14px;
    font-weight: 600;
    padding-bottom: 2px;
}

QLabel.cardDescription {
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 12px;
}

QLabel.restoreCurrentFile {
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 11px;
    font-family: "Consolas", "Courier New", monospace;
}

/* ── Progress bar ──────────────────────────────────── */
QProgressBar {
    background: #E8EDF3;
    border: none;
    border-radius: 4px;
    min-height: 8px;
    max-height: 8px;
}

QProgressBar::chunk {
    background: )" CLR_ACCENT R"(;
    border-radius: 4px;
}

/* ── Status badges ─────────────────────────────────── */
QLabel.badgeSuccess {
    background: #DFF6DD;
    color: )" CLR_SUCCESS R"(;
    border-radius: 10px;
    padding: 2px 10px;
    font-size: 12px;
    font-weight: 600;
}

QLabel.badgeInactive {
    background: #F0F0F0;
    color: )" CLR_TEXT_SECONDARY R"(;
    border-radius: 10px;
    padding: 2px 10px;
    font-size: 12px;
    font-weight: 600;
}

/* kept for any remaining uses */
QLabel.statusActive {
    color: )" CLR_SUCCESS R"(;
    font-size: 13px;
    font-weight: 600;
}

QLabel.statusInactive {
    color: )" CLR_TEXT_MUTED R"(;
    font-size: 13px;
    font-weight: 600;
}

/* ── Buttons ───────────────────────────────────────── */
QPushButton.primary {
    background: )" CLR_ACCENT R"(;
    color: #FFFFFF;
    border: none;
    border-radius: 4px;
    padding: 8px 20px;
    font-size: 13px;
    font-weight: 600;
    min-height: 32px;
}

QPushButton.primary:hover {
    background: )" CLR_ACCENT_HOVER R"(;
}

QPushButton.primary:pressed {
    background: )" CLR_ACCENT_PRESS R"(;
}

QPushButton.primary:disabled {
    background: #BDBDBD;
    color: #FFFFFF;
}

QPushButton.secondary {
    background: transparent;
    color: )" CLR_ACCENT R"(;
    border: 1px solid )" CLR_ACCENT R"(;
    border-radius: 4px;
    padding: 8px 20px;
    font-size: 13px;
    font-weight: 600;
    min-height: 32px;
}

QPushButton.secondary:hover {
    background: )" CLR_ACCENT_LIGHT R"(;
}

QPushButton.secondary:pressed {
    background: #D0E8F8;
}

QPushButton.danger {
    background: )" CLR_DANGER R"(;
    color: #FFFFFF;
    border: none;
    border-radius: 4px;
    padding: 8px 20px;
    font-size: 13px;
    font-weight: 600;
    min-height: 32px;
}

QPushButton.danger:hover {
    background: )" CLR_DANGER_HOVER R"(;
}

/* ── Form inputs ───────────────────────────────────── */
QLineEdit, QComboBox {
    background: #FFFFFF;
    border: 1px solid #D0D5DD;
    border-radius: 4px;
    padding: 7px 12px;
    font-size: 13px;
    color: )" CLR_TEXT_PRIMARY R"(;
    min-height: 20px;
}

QLineEdit:focus, QComboBox:focus {
    border: 1px solid )" CLR_ACCENT R"(;
}

QLineEdit:disabled {
    background: #F5F7FA;
    color: )" CLR_TEXT_MUTED R"(;
}

QComboBox::drop-down {
    border: none;
    padding-right: 8px;
}

QComboBox::down-arrow {
    image: none;
    border-left: 4px solid transparent;
    border-right: 4px solid transparent;
    border-top: 5px solid )" CLR_TEXT_SECONDARY R"(;
    margin-right: 8px;
}

QComboBox QAbstractItemView {
    background: #FFFFFF;
    border: 1px solid #D0D5DD;
    selection-background-color: )" CLR_ACCENT_LIGHT R"(;
    selection-color: )" CLR_TEXT_PRIMARY R"(;
    padding: 4px;
}

/* ── Checkbox ──────────────────────────────────────── */
QCheckBox {
    font-size: 13px;
    color: )" CLR_TEXT_PRIMARY R"(;
    spacing: 8px;
}

QCheckBox::indicator {
    width: 17px;
    height: 17px;
    border: 1.5px solid #B0BEC5;
    border-radius: 3px;
    background: #FFFFFF;
}

QCheckBox::indicator:checked {
    background: )" CLR_ACCENT R"(;
    border-color: )" CLR_ACCENT R"(;
}

/* ── Lists & Trees ─────────────────────────────────── */
QListWidget {
    background: #FFFFFF;
    border: 1px solid )" CLR_CARD_BORDER R"(;
    border-radius: 4px;
    padding: 4px;
    font-size: 12px;
    color: )" CLR_TEXT_PRIMARY R"(;
}

QListWidget::item {
    padding: 7px 12px;
    border-radius: 3px;
}

QListWidget::item:selected {
    background: )" CLR_ACCENT_LIGHT R"(;
    color: )" CLR_ACCENT_PRESS R"(;
}

QListWidget::item:hover {
    background: #F0F4F8;
}

QTreeWidget {
    background: #FFFFFF;
    border: 1px solid )" CLR_CARD_BORDER R"(;
    border-radius: 4px;
    padding: 4px;
    font-size: 12px;
    color: )" CLR_TEXT_PRIMARY R"(;
}

QTreeWidget::item {
    padding: 4px 8px;
}

QTreeWidget::item:selected {
    background: )" CLR_ACCENT_LIGHT R"(;
    color: )" CLR_ACCENT_PRESS R"(;
}

/* ── Log panel ─────────────────────────────────────── */
#logPanel {
    background: )" CLR_CARD_BG R"(;
    border-top: 1px solid )" CLR_CARD_BORDER R"(;
}

#logTitle {
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    padding: 8px 14px 4px 14px;
}

QTextEdit#logText {
    background: transparent;
    border: none;
    font-family: "Cascadia Code", "Consolas", monospace;
    font-size: 11px;
    color: )" CLR_TEXT_SECONDARY R"(;
    padding: 0 14px;
}

/* ── Scrollbars ────────────────────────────────────── */
QScrollBar:vertical {
    background: transparent;
    width: 7px;
    margin: 0;
}

QScrollBar::handle:vertical {
    background: #CDD1D8;
    border-radius: 3px;
    min-height: 30px;
}

QScrollBar::handle:vertical:hover {
    background: #A8AEB8;
}

QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
    height: 0;
}

QScrollBar:horizontal {
    background: transparent;
    height: 7px;
    margin: 0;
}

QScrollBar::handle:horizontal {
    background: #CDD1D8;
    border-radius: 3px;
    min-width: 30px;
}

QScrollBar::handle:horizontal:hover {
    background: #A8AEB8;
}

QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {
    width: 0;
}

/* ── Form labels ───────────────────────────────────── */
QLabel.fieldLabel {
    color: )" CLR_TEXT_SECONDARY R"(;
    font-size: 12px;
    font-weight: 500;
    padding-bottom: 3px;
}

QLabel.sectionTitle {
    color: )" CLR_TEXT_PRIMARY R"(;
    font-size: 13px;
    font-weight: 600;
    padding: 8px 0 4px 0;
}

QFrame.separator {
    background: )" CLR_CARD_BORDER R"(;
    max-height: 1px;
    min-height: 1px;
}

/* ── SpinBox ───────────────────────────────────────── */
QSpinBox {
    background: #FFFFFF;
    border: 1px solid #D0D5DD;
    border-radius: 4px;
    padding: 7px 12px;
    font-size: 13px;
    color: )" CLR_TEXT_PRIMARY R"(;
    min-height: 20px;
}

QSpinBox:focus {
    border: 1px solid )" CLR_ACCENT R"(;
}

QSpinBox::up-button, QSpinBox::down-button {
    width: 22px;
    background: #F5F7FA;
    border: none;
    border-left: 1px solid #D0D5DD;
}

QSpinBox::up-button:hover, QSpinBox::down-button:hover {
    background: )" CLR_ACCENT_LIGHT R"(;
}
)");
}

}
