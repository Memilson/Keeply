#include "KeeplyMainWindow.hpp"
#include "KeeplyStyle.hpp"

#include "keeply/BackupEngine.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/PruneEngine.hpp"
#include "keeply/RestoreEngine.hpp"
#include "keeply/Util.hpp"
#include "keeply/VerifyEngine.hpp"

#include <QApplication>
#include <QFileDialog>
#include <QGridLayout>
#include <QGroupBox>
#include <QHeaderView>
#include <QIcon>
#include <QMessageBox>
#include <QProgressBar>
#include <QScrollArea>
#include <QSplitter>
#include <algorithm>
#include <map>
#include <stdexcept>

namespace keeply {

// ════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════

QFrame* KeeplyMainWindow::makeCard(QLayout* inner) {
    auto* card = new QFrame;
    card->setProperty("class", "card");
    card->setLayout(inner);
    inner->setContentsMargins(20, 18, 20, 18);
    return card;
}

QWidget* KeeplyMainWindow::makePageHeader(const QString& title, const QString& subtitle) {
    auto* header = new QWidget;
    header->setObjectName("pageHeader");
    auto* lay = new QVBoxLayout(header);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(0);
    auto* t = new QLabel(title);
    t->setObjectName("pageTitle");
    auto* s = new QLabel(subtitle);
    s->setObjectName("pageSubtitle");
    lay->addWidget(t);
    lay->addWidget(s);
    return header;
}

QPushButton* KeeplyMainWindow::makePrimary(const QString& text) {
    auto* btn = new QPushButton(text);
    btn->setProperty("class", "primary");
    btn->setCursor(Qt::PointingHandCursor);
    return btn;
}

QPushButton* KeeplyMainWindow::makeSecondary(const QString& text) {
    auto* btn = new QPushButton(text);
    btn->setProperty("class", "secondary");
    btn->setCursor(Qt::PointingHandCursor);
    return btn;
}

QPushButton* KeeplyMainWindow::makeDanger(const QString& text) {
    auto* btn = new QPushButton(text);
    btn->setProperty("class", "danger");
    btn->setCursor(Qt::PointingHandCursor);
    return btn;
}

QLabel* KeeplyMainWindow::makeFieldLabel(const QString& text) {
    auto* lbl = new QLabel(text);
    lbl->setProperty("class", "fieldLabel");
    return lbl;
}

QFrame* KeeplyMainWindow::makeSeparator() {
    auto* sep = new QFrame;
    sep->setProperty("class", "separator");
    sep->setFrameShape(QFrame::HLine);
    sep->setFixedHeight(1);
    return sep;
}

// ════════════════════════════════════════════════════════
//  Constructor / Destructor
// ════════════════════════════════════════════════════════

KeeplyMainWindow::KeeplyMainWindow(std::filesystem::path configPath, QWidget* parent)
    : QMainWindow(parent), m_configPath(std::move(configPath)) {
    setWindowTitle("Keeply");
    setMinimumSize(style::MIN_WINDOW_W, style::MIN_WINDOW_H);
    resize(1100, 680);

    // Load config
    try {
        m_cfg = Config::load(m_configPath);
    } catch (...) {
        m_cfg = Config{};
        m_loadedDefaultConfig = true;
        try {
            if (!m_configPath.empty()) {
                m_cfg.save(m_configPath);
                LocalDb db(m_cfg.dbPath);
                db.migrate();
            }
        } catch (...) {
        }
    }

    // ── Main layout ─────────────────────
    auto* central = new QWidget;
    central->setObjectName("appRoot");
    auto* root = new QHBoxLayout(central);
    root->setContentsMargins(0, 0, 0, 0);
    root->setSpacing(0);

    // Sidebar
    root->addWidget(buildSidebar());

    // Right side: content + log
    auto* rightCol = new QVBoxLayout;
    rightCol->setContentsMargins(0, 0, 0, 0);
    rightCol->setSpacing(0);

    m_contentStack = new QStackedWidget;
    m_contentStack->setObjectName("contentStack");
    m_contentStack->addWidget(buildBackupPage());
    m_contentStack->addWidget(buildRestorePage());
    m_contentStack->addWidget(buildJobsPage());
    m_contentStack->addWidget(buildConfigPage());

    rightCol->addWidget(m_contentStack, 1);
    rightCol->addWidget(buildLogPanel());

    root->addLayout(rightCol);
    setCentralWidget(central);

    // Signals
    connect(this, &KeeplyMainWindow::logMessage, this, &KeeplyMainWindow::appendLog, Qt::QueuedConnection);
    connect(this, &KeeplyMainWindow::requestRefresh, this, &KeeplyMainWindow::onRefresh, Qt::QueuedConnection);

    // Init
    fillUi();
    onNavClicked(0);

    if (m_loadedDefaultConfig) {
        emit logMessage("Config padrão local criada");
    } else {
        emit logMessage(m_configPath.empty() ? "Keeply pronto" : QString("Config carregada: %1").arg(QString::fromStdString(m_configPath.string())));
    }
}

KeeplyMainWindow::~KeeplyMainWindow() {
    m_agentStop = true;
    if (m_agentThread.joinable()) m_agentThread.join();
}

// ════════════════════════════════════════════════════════
//  Sidebar
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildSidebar() {
    auto* sidebar = new QWidget;
    sidebar->setObjectName("sidebar");
    sidebar->setFixedWidth(style::SIDEBAR_WIDTH);

    auto* layout = new QVBoxLayout(sidebar);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);

    // Logo + tagline
    auto* logo = new QLabel("KEEPLY");
    logo->setObjectName("sidebarLogo");
    layout->addWidget(logo);
    auto* tagline = new QLabel("Backup & Recovery");
    tagline->setObjectName("sidebarTagline");
    layout->addWidget(tagline);

    // Nav items
    struct NavItem { QString iconPath; QString label; };
    const NavItem items[] = {
        {":/icons/nav-backup.svg",    "  BACKUP"},
        {":/icons/nav-restore.svg",   "  RESTAURAR"},
        {":/icons/nav-jobs.svg",      "  PROTEÇÃO"},
        {":/icons/nav-settings.svg",  "  CONFIGURAÇÕES"},
    };

    for (int i = 0; i < 4; ++i) {
        auto* btn = new QPushButton(items[i].label);
        btn->setIcon(QIcon(items[i].iconPath));
        btn->setIconSize(QSize(17, 17));
        btn->setProperty("class", "navItem");
        btn->setCheckable(true);
        btn->setCursor(Qt::PointingHandCursor);
        connect(btn, &QPushButton::clicked, this, [this, i]() { onNavClicked(i); });
        layout->addWidget(btn);
        m_navButtons.append(btn);
    }

    layout->addStretch();

    // Version label at bottom
    auto* ver = new QLabel("v0.1.0");
    ver->setStyleSheet("color: #4A6078; font-size: 11px; padding: 12px 24px;");
    layout->addWidget(ver);

    return sidebar;
}

void KeeplyMainWindow::onNavClicked(int index) {
    for (int i = 0; i < m_navButtons.size(); ++i) {
        m_navButtons[i]->setChecked(i == index);
        m_navButtons[i]->setProperty("active", i == index);
        m_navButtons[i]->style()->unpolish(m_navButtons[i]);
        m_navButtons[i]->style()->polish(m_navButtons[i]);
    }
    m_contentStack->setCurrentIndex(index);
}

// ════════════════════════════════════════════════════════
//  Backup Page
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildBackupPage() {
    auto* page = new QWidget;
    auto* outer = new QVBoxLayout(page);
    outer->setContentsMargins(0, 0, 0, 0);
    outer->setSpacing(0);

    outer->addWidget(makePageHeader("Backup",
        "Selecione uma pasta de origem e execute o backup para o storage configurado."));

    // ── Scroll area ────────
    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* content = new QWidget;
    auto* lay = new QVBoxLayout(content);
    lay->setContentsMargins(style::CONTENT_PADDING, 8, style::CONTENT_PADDING, style::CONTENT_PADDING);
    lay->setSpacing(16);

    // Card: Source
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(8);

        auto* cardTitle = new QLabel("Origem do Backup");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* desc = new QLabel("Escolha a pasta com os arquivos que deseja proteger.");
        desc->setProperty("class", "cardDescription");
        inner->addWidget(desc);

        inner->addSpacing(4);

        auto* row = new QHBoxLayout;
        m_backupSource = new QLineEdit;
        m_backupSource->setPlaceholderText("C:\\Users\\...\\Documents");
        row->addWidget(m_backupSource, 1);

        auto* pick = makeSecondary("Selecionar pasta");
        pick->setIcon(QIcon(":/icons/act-folder.svg"));
        pick->setIconSize(QSize(15, 15));
        connect(pick, &QPushButton::clicked, this, &KeeplyMainWindow::onPickBackupSource);
        row->addWidget(pick);
        inner->addLayout(row);

        lay->addWidget(makeCard(inner));
    }

    // Card: Execute
    {
        auto* inner = new QHBoxLayout;
        auto* btn = makePrimary("Executar Backup");
        btn->setIcon(QIcon(":/icons/act-play.svg"));
        btn->setIconSize(QSize(16, 16));
        btn->setMinimumWidth(200);
        connect(btn, &QPushButton::clicked, this, &KeeplyMainWindow::onRunBackup);
        inner->addWidget(btn);
        inner->addStretch();

        auto* info = new QLabel("O backup será enviado para o storage ativo (local ou MinIO/S3).");
        info->setProperty("class", "cardDescription");
        info->setWordWrap(true);
        inner->addWidget(info, 1);

        lay->addWidget(makeCard(inner));
    }

    // Card: Progresso do Backup
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Progresso do Backup");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        m_backupBar = new QProgressBar;
        m_backupBar->setRange(0, 100);
        m_backupBar->setValue(0);
        m_backupBar->setTextVisible(false);
        m_backupBar->setFixedHeight(8);
        inner->addWidget(m_backupBar);

        m_backupProgressLabel = new QLabel("Nenhum backup em andamento");
        m_backupProgressLabel->setProperty("class", "cardDescription");
        inner->addWidget(m_backupProgressLabel);

        m_backupCurrentFile = new QLabel("");
        m_backupCurrentFile->setProperty("class", "restoreCurrentFile");
        m_backupCurrentFile->setWordWrap(true);
        inner->addWidget(m_backupCurrentFile);

        lay->addWidget(makeCard(inner));

        connect(this, &KeeplyMainWindow::backupProgress,
                this, &KeeplyMainWindow::onBackupProgress,
                Qt::QueuedConnection);
    }

    lay->addStretch();
    scroll->setWidget(content);
    outer->addWidget(scroll, 1);

    return page;
}

void KeeplyMainWindow::onPickBackupSource() {
    auto dir = QFileDialog::getExistingDirectory(this, "Selecione a pasta de origem");
    if (!dir.isEmpty()) m_backupSource->setText(dir);
}

void KeeplyMainWindow::onRunBackup() {
    auto cfg = uiConfig();
    auto source = m_backupSource->text().toStdString();
    if (source.empty()) {
        QMessageBox::warning(this, "Aviso", "Selecione uma pasta de origem.");
        return;
    }
    m_backupBar->setValue(0);
    m_backupProgressLabel->setText("Escaneando arquivos...");
    m_backupCurrentFile->setText("");
    runAsync("Backup", [this, cfg, source]() {
        BackupEngine b(cfg);
        auto r = b.run(source, [this](std::int64_t done, std::int64_t total, std::int64_t bytes, const std::string& file) {
            emit backupProgress(done, total, bytes, QString::fromStdString(file));
        });
        emit backupProgress(r.files, r.files, r.bytes, "");
        emit logMessage(QString("Snapshot criado: %1  (%2 arq, %3 chunks novos, %4 reutilizados)")
            .arg(QString::fromStdString(r.snapshotId))
            .arg(r.files).arg(r.uploadedChunks).arg(r.reusedChunks));
    });
}

void KeeplyMainWindow::onBackupProgress(qint64 done, qint64 total, qint64 bytes, const QString& file) {
    int pct = (total > 0) ? static_cast<int>(done * 100 / total) : 0;
    m_backupBar->setValue(pct);
    if (!file.isEmpty()) {
        m_backupProgressLabel->setText(
            QString("%1 / %2 arquivos  (%3%)  —  %4 bytes")
                .arg(done).arg(total).arg(pct).arg(bytes));
        m_backupCurrentFile->setText(file);
    } else {
        m_backupProgressLabel->setText(
            QString("Concluído  —  %1 arquivo(s), %2 bytes").arg(done).arg(bytes));
        m_backupCurrentFile->setText("");
    }
}

// ════════════════════════════════════════════════════════
//  Restore Page
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildRestorePage() {
    auto* page = new QWidget;
    auto* outer = new QVBoxLayout(page);
    outer->setContentsMargins(0, 0, 0, 0);
    outer->setSpacing(0);

    outer->addWidget(makePageHeader("Restaurar",
        "Restaure arquivos de um snapshot ou verifique a integridade dos dados."));

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* content = new QWidget;
    auto* lay = new QVBoxLayout(content);
    lay->setContentsMargins(style::CONTENT_PADDING, 8, style::CONTENT_PADDING, style::CONTENT_PADDING);
    lay->setSpacing(16);

    // Card: Snapshots disponíveis
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* headerRow = new QHBoxLayout;
        auto* cardTitle = new QLabel("Snapshots Disponíveis");
        cardTitle->setProperty("class", "cardTitle");
        headerRow->addWidget(cardTitle, 1);

        auto* refreshBtn = makeSecondary("Atualizar");
        refreshBtn->setIcon(QIcon(":/icons/act-refresh.svg"));
        refreshBtn->setIconSize(QSize(14, 14));
        connect(refreshBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRefreshRestoreSnapshots);
        headerRow->addWidget(refreshBtn);
        inner->addLayout(headerRow);

        m_restoreSnapList = new QListWidget;
        m_restoreSnapList->setFixedHeight(130);
        m_restoreSnapList->setSelectionMode(QAbstractItemView::SingleSelection);
        connect(m_restoreSnapList, &QListWidget::currentRowChanged, this, [this](int) {
            auto* item = m_restoreSnapList->currentItem();
            if (!item) return;
            auto row = item->text().toStdString();
            auto p = row.find("  |");
            auto id = (p == std::string::npos) ? row : row.substr(0, p);
            m_restoreSnapshot->setText(QString::fromStdString(id));
        });
        inner->addWidget(m_restoreSnapList);

        lay->addWidget(makeCard(inner));
    }

    // Card: Configuração da Restauração
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Configuração da Restauração");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* grid = new QGridLayout;
        grid->setHorizontalSpacing(12);
        grid->setVerticalSpacing(10);

        grid->addWidget(makeFieldLabel("Snapshot ID"), 0, 0);
        m_restoreSnapshot = new QLineEdit;
        m_restoreSnapshot->setPlaceholderText("latest");
        grid->addWidget(m_restoreSnapshot, 0, 1);

        grid->addWidget(makeFieldLabel("Pasta de destino"), 1, 0);
        auto* targetRow = new QHBoxLayout;
        m_restoreTarget = new QLineEdit;
        m_restoreTarget->setPlaceholderText("C:\\Users\\...\\Restore");
        targetRow->addWidget(m_restoreTarget, 1);
        auto* pick = makeSecondary("Selecionar");
        pick->setIcon(QIcon(":/icons/act-folder.svg"));
        pick->setIconSize(QSize(15, 15));
        connect(pick, &QPushButton::clicked, this, &KeeplyMainWindow::onPickRestoreTarget);
        targetRow->addWidget(pick);
        grid->addLayout(targetRow, 1, 1);

        grid->setColumnStretch(1, 1);
        inner->addLayout(grid);

        inner->addSpacing(4);

        auto* btnRow = new QHBoxLayout;
        auto* restoreBtn = makePrimary("Restaurar");
        restoreBtn->setIcon(QIcon(":/icons/act-download.svg"));
        restoreBtn->setIconSize(QSize(16, 16));
        connect(restoreBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRunRestore);
        btnRow->addWidget(restoreBtn);

        auto* verifyBtn = makeSecondary("Verificar integridade");
        verifyBtn->setIcon(QIcon(":/icons/act-check.svg"));
        verifyBtn->setIconSize(QSize(15, 15));
        connect(verifyBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRunVerify);
        btnRow->addWidget(verifyBtn);
        btnRow->addStretch();

        inner->addLayout(btnRow);
        lay->addWidget(makeCard(inner));
    }

    // Card: Progresso da Restauração
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Progresso da Restauração");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        m_restoreBar = new QProgressBar;
        m_restoreBar->setRange(0, 100);
        m_restoreBar->setValue(0);
        m_restoreBar->setTextVisible(false);
        m_restoreBar->setFixedHeight(8);
        inner->addWidget(m_restoreBar);

        m_restoreProgressLabel = new QLabel("Nenhuma restauração em andamento");
        m_restoreProgressLabel->setProperty("class", "cardDescription");
        inner->addWidget(m_restoreProgressLabel);

        m_restoreCurrentFile = new QLabel("");
        m_restoreCurrentFile->setProperty("class", "restoreCurrentFile");
        m_restoreCurrentFile->setWordWrap(true);
        inner->addWidget(m_restoreCurrentFile);

        lay->addWidget(makeCard(inner));

        connect(this, &KeeplyMainWindow::restoreProgress,
                this, &KeeplyMainWindow::onRestoreProgress,
                Qt::QueuedConnection);
    }

    lay->addStretch();
    scroll->setWidget(content);
    outer->addWidget(scroll, 1);

    return page;
}

void KeeplyMainWindow::onPickRestoreTarget() {
    auto dir = QFileDialog::getExistingDirectory(this, "Selecione a pasta de destino");
    if (!dir.isEmpty()) m_restoreTarget->setText(dir);
}

void KeeplyMainWindow::onRunRestore() {
    auto cfg = uiConfig();
    auto snap = m_restoreSnapshot->text().toStdString();
    auto target = m_restoreTarget->text().toStdString();
    if (snap.empty()) snap = "latest";
    if (target.empty()) {
        QMessageBox::warning(this, "Aviso", "Selecione uma pasta de destino.");
        return;
    }
    m_restoreBar->setValue(0);
    m_restoreProgressLabel->setText("Iniciando restauração...");
    m_restoreCurrentFile->setText("");
    runAsync("Restore", [this, cfg, snap, target]() {
        RestoreEngine r(cfg);
        auto out = r.run(snap, target, [this](std::int64_t done, std::int64_t total, std::int64_t bytes, const std::string& file) {
            emit restoreProgress(done, total, bytes, QString::fromStdString(file));
        });
        emit restoreProgress(out.files, out.files, out.bytes, "");
        emit logMessage(QString("Restaurados %1 arquivos").arg(out.files));
    });
}

void KeeplyMainWindow::onRestoreProgress(qint64 done, qint64 total, qint64 bytes, const QString& file) {
    int pct = (total > 0) ? static_cast<int>(done * 100 / total) : 0;
    m_restoreBar->setValue(pct);
    if (!file.isEmpty()) {
        m_restoreProgressLabel->setText(
            QString("%1 / %2 arquivos  (%3%)  —  %4 bytes")
                .arg(done).arg(total).arg(pct).arg(bytes));
        m_restoreCurrentFile->setText(file);
    } else {
        m_restoreProgressLabel->setText(
            QString("Concluído  —  %1 arquivo(s), %2 bytes").arg(done).arg(bytes));
        m_restoreCurrentFile->setText("");
    }
}

void KeeplyMainWindow::onRefreshRestoreSnapshots() {
    if (!m_restoreSnapList) return;
    m_restoreSnapList->clear();
    try {
        LocalDb db(m_cfg.dbPath);
        db.migrate();
        bool first = true;
        for (const auto& s : db.listSnapshots()) {
            QString row = QString("%1  |  %2  |  %3 arq  |  %4 bytes")
                .arg(QString::fromStdString(s.id))
                .arg(QString::fromStdString(s.createdAt))
                .arg(s.totalFiles)
                .arg(s.totalBytes);
            m_restoreSnapList->addItem(row);
            if (first) {
                m_restoreSnapList->setCurrentRow(0);
                first = false;
            }
        }
    } catch (const std::exception& e) {
        emit logMessage(QString("Snapshots (restore): %1").arg(e.what()));
    }
}

void KeeplyMainWindow::onRunVerify() {
    auto cfg = uiConfig();
    auto snap = m_restoreSnapshot->text().toStdString();
    if (snap.empty()) snap = "latest";
    runAsync("Verify", [this, cfg, snap]() {
        VerifyEngine v(cfg);
        auto r = v.run(snap);
        emit logMessage(QString("Verificação concluída — %1 erro(s)").arg(r.errors));
    });
}

// ════════════════════════════════════════════════════════
//  Snapshots Page
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildSnapshotsPage() {
    auto* page = new QWidget;
    auto* outer = new QVBoxLayout(page);
    outer->setContentsMargins(0, 0, 0, 0);
    outer->setSpacing(0);

    outer->addWidget(makePageHeader("Snapshots",
        "Visualize, verifique e gerencie seus snapshots de backup."));

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* content = new QWidget;
    auto* lay = new QVBoxLayout(content);
    lay->setContentsMargins(style::CONTENT_PADDING, 8, style::CONTENT_PADDING, style::CONTENT_PADDING);
    lay->setSpacing(16);

    // Card: Snapshot list
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* headerRow = new QHBoxLayout;
        auto* cardTitle = new QLabel("Lista de Snapshots");
        cardTitle->setProperty("class", "cardTitle");
        headerRow->addWidget(cardTitle);
        headerRow->addStretch();

        auto* refreshBtn = makeSecondary("Atualizar");
        refreshBtn->setIcon(QIcon(":/icons/act-refresh.svg"));
        refreshBtn->setIconSize(QSize(15, 15));
        connect(refreshBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRefreshSnapshots);
        headerRow->addWidget(refreshBtn);

        auto* pruneBtn = makeDanger("Prune");
        pruneBtn->setIcon(QIcon(":/icons/act-trash.svg"));
        pruneBtn->setIconSize(QSize(15, 15));
        connect(pruneBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onPrune);
        headerRow->addWidget(pruneBtn);

        inner->addLayout(headerRow);

        m_snapList = new QListWidget;
        m_snapList->setMinimumHeight(140);
        connect(m_snapList, &QListWidget::currentRowChanged, this, &KeeplyMainWindow::onSnapshotSelectionChanged);
        inner->addWidget(m_snapList);

        lay->addWidget(makeCard(inner));
    }

    // Card: File browser
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* headerRow = new QHBoxLayout;
        auto* cardTitle = new QLabel("Arquivos do Snapshot");
        cardTitle->setProperty("class", "cardTitle");
        headerRow->addWidget(cardTitle);
        headerRow->addStretch();

        m_filesSnapshot = new QLineEdit;
        m_filesSnapshot->setPlaceholderText("Snapshot ID");
        m_filesSnapshot->setMaximumWidth(340);
        headerRow->addWidget(m_filesSnapshot);

        auto* loadBtn = makePrimary("Carregar");
        connect(loadBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onLoadFiles);
        headerRow->addWidget(loadBtn);

        inner->addLayout(headerRow);

        m_filesTree = new QTreeWidget;
        m_filesTree->setHeaderLabels({"Nome", "Tamanho"});
        m_filesTree->header()->setStretchLastSection(false);
        m_filesTree->header()->setSectionResizeMode(0, QHeaderView::Stretch);
        m_filesTree->header()->setSectionResizeMode(1, QHeaderView::ResizeToContents);
        m_filesTree->setMinimumHeight(200);
        inner->addWidget(m_filesTree);

        lay->addWidget(makeCard(inner));
    }

    lay->addStretch();
    scroll->setWidget(content);
    outer->addWidget(scroll, 1);

    return page;
}

void KeeplyMainWindow::onRefreshSnapshots() {
    refreshSnapshots();
    emit logMessage("Snapshots atualizados");
}

void KeeplyMainWindow::onPrune() {
    auto cfg = uiConfig();
    int keep = m_jobKeep ? m_jobKeep->value() : 10;
    auto reply = QMessageBox::question(this, "Confirmar Prune",
        QString("Manter os últimos %1 snapshots e remover o resto?").arg(keep),
        QMessageBox::Yes | QMessageBox::No);
    if (reply != QMessageBox::Yes) return;
    runAsync("Prune", [this, cfg, keep]() {
        PruneEngine p(cfg);
        auto r = p.keepLast(keep);
        emit logMessage(QString("Prune: %1 snapshots, %2 chunks removidos").arg(r.snapshotsDeleted).arg(r.chunksDeleted));
    });
}

void KeeplyMainWindow::onSnapshotSelectionChanged() {
    auto* item = m_snapList->currentItem();
    if (!item) return;
    auto row = item->text().toStdString();
    auto p = row.find(" | ");
    auto id = (p == std::string::npos) ? row : row.substr(0, p);
    m_restoreSnapshot->setText(QString::fromStdString(id));
    if (m_filesSnapshot) m_filesSnapshot->setText(QString::fromStdString(id));
}

void KeeplyMainWindow::onLoadFiles() {
    try {
        auto snap = m_filesSnapshot->text().toStdString();
        if (snap.empty()) snap = "latest";

        LocalDb db(m_cfg.dbPath);
        db.migrate();
        if (snap == "latest") {
            auto latest = db.getSnapshot("latest");
            if (!latest) throw std::runtime_error("snapshot não encontrada");
            snap = latest->id;
        }
        auto files = db.listSnapshotFiles(snap);
        m_filesSnapshot->setText(QString::fromStdString(snap));
        m_filesTree->clear();

        auto* root = new QTreeWidgetItem(m_filesTree);
        root->setText(0, QString("%1  (%2 arquivos)").arg(QString::fromStdString(snap)).arg(files.size()));
        root->setExpanded(true);

        std::map<std::string, QTreeWidgetItem*> folders;

        auto splitPath = [](std::string path) -> std::vector<std::string> {
            std::replace(path.begin(), path.end(), '\\', '/');
            std::vector<std::string> out;
            std::size_t start = 0;
            while (start < path.size()) {
                auto end = path.find('/', start);
                auto part = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
                if (!part.empty()) out.push_back(part);
                if (end == std::string::npos) break;
                start = end + 1;
            }
            return out;
        };

        for (const auto& file : files) {
            auto parts = splitPath(file.path);
            if (parts.empty()) continue;
            QTreeWidgetItem* parent = root;
            std::string prefix;
            for (std::size_t i = 0; i + 1 < parts.size(); ++i) {
                if (!prefix.empty()) prefix += "/";
                prefix += parts[i];
                auto it = folders.find(prefix);
                if (it == folders.end()) {
                    auto* item = new QTreeWidgetItem(parent);
                    item->setText(0, QString::fromStdString(parts[i]));
                    item->setText(1, "");
                    folders[prefix] = item;
                    parent = item;
                } else {
                    parent = it->second;
                }
            }
            auto* leaf = new QTreeWidgetItem(parent);
            leaf->setText(0, QString::fromStdString(parts.back()));
            leaf->setText(1, QString("%1 bytes").arg(file.size));
        }

        emit logMessage(QString("Arquivos carregados: %1").arg(files.size()));
    } catch (const std::exception& e) {
        emit logMessage(QString("Erro ao carregar arquivos: %1").arg(e.what()));
    }
}

// ════════════════════════════════════════════════════════
//  Jobs Page
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildJobsPage() {
    auto* page = new QWidget;
    auto* outer = new QVBoxLayout(page);
    outer->setContentsMargins(0, 0, 0, 0);
    outer->setSpacing(0);

    outer->addWidget(makePageHeader("Proteção",
        "Gerencie agente, jobs automáticos e criptografia."));

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* content = new QWidget;
    auto* lay = new QVBoxLayout(content);
    lay->setContentsMargins(style::CONTENT_PADDING, 8, style::CONTENT_PADDING, style::CONTENT_PADDING);
    lay->setSpacing(16);

    // Card: Agent status
    {
        auto* inner = new QHBoxLayout;

        auto* iconLabel = new QLabel;
        iconLabel->setFixedSize(48, 48);
        iconLabel->setAlignment(Qt::AlignCenter);
        iconLabel->setPixmap(QIcon(":/icons/act-shield.svg").pixmap(28, 28));
        iconLabel->setStyleSheet("background: #EBF4FC; border-radius: 24px;");
        inner->addWidget(iconLabel);
        inner->addSpacing(16);

        auto* infoCol = new QVBoxLayout;
        infoCol->setSpacing(2);
        auto* agentTitle = new QLabel("Agente de Proteção");
        agentTitle->setProperty("class", "cardTitle");
        infoCol->addWidget(agentTitle);

        m_agentStatus = new QLabel("Inativo");
        m_agentStatus->setProperty("class", "badgeInactive");
        m_agentStatus->setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
        auto* badgeRow = new QHBoxLayout;
        badgeRow->setContentsMargins(0, 0, 0, 0);
        badgeRow->addWidget(m_agentStatus);
        badgeRow->addStretch();
        infoCol->addLayout(badgeRow);
        inner->addLayout(infoCol, 1);

        auto* startBtn = makePrimary("Iniciar");
        startBtn->setIcon(QIcon(":/icons/act-play.svg"));
        startBtn->setIconSize(QSize(15, 15));
        connect(startBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onStartAgent);
        inner->addWidget(startBtn);

        auto* stopBtn = makeDanger("Parar");
        stopBtn->setIcon(QIcon(":/icons/act-stop.svg"));
        stopBtn->setIconSize(QSize(15, 15));
        connect(stopBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onStopAgent);
        inner->addWidget(stopBtn);

        lay->addWidget(makeCard(inner));
    }

    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);
        auto* cardTitle = new QLabel("Criptografia");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);
        auto* row = new QHBoxLayout;
        m_encryption = new QCheckBox("Habilitar criptografia");
        row->addWidget(m_encryption);
        row->addSpacing(16);
        row->addWidget(makeFieldLabel("Chave (hex):"));
        m_encKey = new QLineEdit;
        m_encKey->setEchoMode(QLineEdit::Password);
        row->addWidget(m_encKey, 1);
        inner->addLayout(row);
        lay->addWidget(makeCard(inner));
    }

    // Card: Add job
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Novo Job");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* grid = new QGridLayout;
        grid->setHorizontalSpacing(16);
        grid->setVerticalSpacing(8);

        grid->addWidget(makeFieldLabel("Nome"), 0, 0);
        m_jobName = new QLineEdit;
        m_jobName->setPlaceholderText("ex: documentos");
        grid->addWidget(m_jobName, 0, 1);

        grid->addWidget(makeFieldLabel("Origem"), 0, 2);
        m_jobSource = new QLineEdit;
        m_jobSource->setPlaceholderText("C:\\Users\\...\\Documents");
        grid->addWidget(m_jobSource, 0, 3);

        grid->addWidget(makeFieldLabel("Intervalo (min)"), 1, 0);
        m_jobInterval = new QSpinBox;
        m_jobInterval->setRange(1, 99999);
        m_jobInterval->setValue(60);
        grid->addWidget(m_jobInterval, 1, 1);

        grid->addWidget(makeFieldLabel("Retenção (keep)"), 1, 2);
        m_jobKeep = new QSpinBox;
        m_jobKeep->setRange(0, 9999);
        m_jobKeep->setValue(10);
        grid->addWidget(m_jobKeep, 1, 3);

        grid->setColumnStretch(1, 1);
        grid->setColumnStretch(3, 2);
        inner->addLayout(grid);

        auto* btnRow = new QHBoxLayout;
        auto* addBtn = makePrimary("Salvar Job");
        addBtn->setIcon(QIcon(":/icons/act-plus.svg"));
        addBtn->setIconSize(QSize(15, 15));
        connect(addBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onAddJob);
        btnRow->addWidget(addBtn);
        btnRow->addStretch();
        inner->addLayout(btnRow);

        lay->addWidget(makeCard(inner));
    }

    // Card: Job list
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* headerRow = new QHBoxLayout;
        auto* cardTitle = new QLabel("Jobs Configurados");
        cardTitle->setProperty("class", "cardTitle");
        headerRow->addWidget(cardTitle);
        headerRow->addStretch();

        auto* pollLabel = makeFieldLabel("Poll (seg):");
        headerRow->addWidget(pollLabel);
        m_pollSeconds = new QSpinBox;
        m_pollSeconds->setRange(5, 3600);
        m_pollSeconds->setValue(30);
        m_pollSeconds->setMaximumWidth(80);
        headerRow->addWidget(m_pollSeconds);

        inner->addLayout(headerRow);

        m_jobList = new QListWidget;
        m_jobList->setMinimumHeight(100);
        inner->addWidget(m_jobList);

        auto* btnRow = new QHBoxLayout;
        auto* runBtn = makePrimary("Executar");
        runBtn->setIcon(QIcon(":/icons/act-play.svg"));
        runBtn->setIconSize(QSize(15, 15));
        connect(runBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRunJob);
        btnRow->addWidget(runBtn);

        auto* removeBtn = makeDanger("Remover");
        removeBtn->setIcon(QIcon(":/icons/act-trash.svg"));
        removeBtn->setIconSize(QSize(15, 15));
        connect(removeBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onRemoveJob);
        btnRow->addWidget(removeBtn);
        btnRow->addStretch();

        inner->addLayout(btnRow);
        lay->addWidget(makeCard(inner));
    }

    lay->addStretch();
    scroll->setWidget(content);
    outer->addWidget(scroll, 1);

    return page;
}

// ════════════════════════════════════════════════════════
//  Config Page
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildConfigPage() {
    auto* page = new QWidget;
    auto* outer = new QVBoxLayout(page);
    outer->setContentsMargins(0, 0, 0, 0);
    outer->setSpacing(0);

    outer->addWidget(makePageHeader("Configurações",
        "Configure arquivo, banco de dados e storage do backup."));

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* content = new QWidget;
    auto* lay = new QVBoxLayout(content);
    lay->setContentsMargins(style::CONTENT_PADDING, 8, style::CONTENT_PADDING, style::CONTENT_PADDING);
    lay->setSpacing(16);

    // Card: Config file
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Arquivo de Configuração");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* row = new QHBoxLayout;
        m_configFile = new QLineEdit;
        row->addWidget(m_configFile, 1);

        auto* loadBtn = makeSecondary("Carregar");
        connect(loadBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onLoadConfig);
        row->addWidget(loadBtn);

        auto* saveBtn = makePrimary("Salvar");
        saveBtn->setIcon(QIcon(":/icons/act-save.svg"));
        saveBtn->setIconSize(QSize(15, 15));
        connect(saveBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onSaveConfig);
        row->addWidget(saveBtn);

        inner->addLayout(row);

        auto* initRow = new QHBoxLayout;
        auto* initLocalBtn = makeSecondary("Inicializar Local");
        connect(initLocalBtn, &QPushButton::clicked, this, &KeeplyMainWindow::onInitLocal);
        initRow->addWidget(initLocalBtn);

        auto* initS3Btn = makeSecondary("Inicializar MinIO/S3");
        connect(initS3Btn, &QPushButton::clicked, this, &KeeplyMainWindow::onInitS3);
        initRow->addWidget(initS3Btn);
        initRow->addStretch();

        inner->addLayout(initRow);
        lay->addWidget(makeCard(inner));
    }

    // Card: Armazenamento Geral
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Armazenamento Geral");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* grid = new QGridLayout;
        grid->setHorizontalSpacing(16);
        grid->setVerticalSpacing(8);

        grid->addWidget(makeFieldLabel("Banco de dados"), 0, 0);
        m_dbPath = new QLineEdit;
        grid->addWidget(m_dbPath, 0, 1);

        grid->addWidget(makeFieldLabel("Tipo de storage"), 1, 0);
        m_storageType = new QComboBox;
        m_storageType->addItems({"local", "s3"});
        grid->addWidget(m_storageType, 1, 1);

        grid->addWidget(makeFieldLabel("Caminho local"), 2, 0);
        m_localPath = new QLineEdit;
        m_localPath->setPlaceholderText("./repo");
        grid->addWidget(m_localPath, 2, 1);

        grid->setColumnStretch(1, 1);
        inner->addLayout(grid);

        lay->addWidget(makeCard(inner));
    }

    // Card: Amazon S3 / MinIO
    {
        auto* inner = new QVBoxLayout;
        inner->setSpacing(10);

        auto* cardTitle = new QLabel("Amazon S3 / MinIO");
        cardTitle->setProperty("class", "cardTitle");
        inner->addWidget(cardTitle);

        auto* desc = new QLabel("Configure quando o tipo de storage for \"s3\".");
        desc->setProperty("class", "cardDescription");
        inner->addWidget(desc);

        auto* grid = new QGridLayout;
        grid->setHorizontalSpacing(16);
        grid->setVerticalSpacing(8);

        grid->addWidget(makeFieldLabel("Endpoint"), 0, 0);
        m_endpoint = new QLineEdit;
        m_endpoint->setPlaceholderText("https://s3.amazonaws.com  ou  http://localhost:9000");
        grid->addWidget(m_endpoint, 0, 1, 1, 3);

        grid->addWidget(makeFieldLabel("Bucket"), 1, 0);
        m_bucket = new QLineEdit;
        m_bucket->setPlaceholderText("meu-bucket");
        grid->addWidget(m_bucket, 1, 1);

        grid->addWidget(makeFieldLabel("Prefix"), 1, 2);
        m_prefix = new QLineEdit;
        m_prefix->setPlaceholderText("repos/default");
        grid->addWidget(m_prefix, 1, 3);

        grid->addWidget(makeFieldLabel("Access Key ID"), 2, 0);
        m_accessKey = new QLineEdit;
        m_accessKey->setPlaceholderText("AKIAIOSFODNN7EXAMPLE");
        grid->addWidget(m_accessKey, 2, 1);

        grid->addWidget(makeFieldLabel("Secret Access Key"), 2, 2);
        m_secretKey = new QLineEdit;
        m_secretKey->setEchoMode(QLineEdit::Password);
        m_secretKey->setPlaceholderText("••••••••••••••••••••••••••••");
        grid->addWidget(m_secretKey, 2, 3);

        grid->addWidget(makeFieldLabel("Region"), 3, 0);
        m_region = new QLineEdit;
        m_region->setPlaceholderText("us-east-1");
        grid->addWidget(m_region, 3, 1);

        grid->setColumnStretch(1, 1);
        grid->setColumnStretch(3, 1);
        inner->addLayout(grid);

        lay->addWidget(makeCard(inner));
    }


    lay->addStretch();
    scroll->setWidget(content);
    outer->addWidget(scroll, 1);

    return page;
}

// ════════════════════════════════════════════════════════
//  Log Panel
// ════════════════════════════════════════════════════════

QWidget* KeeplyMainWindow::buildLogPanel() {
    auto* panel = new QWidget;
    panel->setObjectName("logPanel");
    panel->setFixedHeight(style::LOG_HEIGHT);

    auto* lay = new QVBoxLayout(panel);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(0);

    auto* title = new QLabel("EVENTOS");
    title->setObjectName("logTitle");
    lay->addWidget(title);

    m_logText = new QTextEdit;
    m_logText->setObjectName("logText");
    m_logText->setReadOnly(true);
    lay->addWidget(m_logText);

    return panel;
}

void KeeplyMainWindow::appendLog(const QString& msg) {
    auto ts = QDateTime::currentDateTime().toString("HH:mm:ss");
    m_logText->append(QString("[%1] %2").arg(ts, msg));
}

// ════════════════════════════════════════════════════════
//  Jobs Logic
// ════════════════════════════════════════════════════════

void KeeplyMainWindow::onAddJob() {
    m_cfg = uiConfig();
    JobConfig job;
    job.name = m_jobName->text().toStdString();
    job.source = m_jobSource->text().toStdString();
    job.intervalMinutes = m_jobInterval->value();
    job.retentionKeepLast = m_jobKeep->value();
    if (job.name.empty() || job.source.empty()) {
        QMessageBox::warning(this, "Aviso", "Job precisa de nome e origem.");
        return;
    }
    auto it = std::find_if(m_cfg.jobs.begin(), m_cfg.jobs.end(),
        [&](const JobConfig& j) { return j.name == job.name; });
    if (it == m_cfg.jobs.end()) m_cfg.jobs.push_back(job);
    else *it = job;
    saveConfig();
    emit logMessage(QString("Job salvo: %1").arg(m_jobName->text()));
}

void KeeplyMainWindow::onRemoveJob() {
    int idx = m_jobList->currentRow();
    if (idx < 0 || idx >= static_cast<int>(m_cfg.jobs.size())) {
        QMessageBox::warning(this, "Aviso", "Selecione um job para remover.");
        return;
    }
    auto name = QString::fromStdString(m_cfg.jobs[static_cast<std::size_t>(idx)].name);
    m_cfg.jobs.erase(m_cfg.jobs.begin() + idx);
    saveConfig();
    emit logMessage(QString("Job removido: %1").arg(name));
}

void KeeplyMainWindow::onRunJob() {
    int idx = m_jobList->currentRow();
    if (idx < 0 || idx >= static_cast<int>(m_cfg.jobs.size())) {
        QMessageBox::warning(this, "Aviso", "Selecione um job para executar.");
        return;
    }
    saveConfig();
    auto name = m_cfg.jobs[static_cast<std::size_t>(idx)].name;
    auto configPath = m_configPath;
    runAsync(QString("Job %1").arg(QString::fromStdString(name)), [this, configPath, name]() {
        auto cfg = Config::load(configPath);
        for (auto& job : cfg.jobs) {
            if (job.name != name) continue;
            BackupEngine backup(cfg);
            auto r = backup.run(job.source);
            job.lastRunAt = nowUtcIso();
            cfg.save(configPath);
            if (job.retentionKeepLast >= 0) {
                PruneEngine prune(cfg);
                prune.keepLast(job.retentionKeepLast, std::filesystem::absolute(job.source).string());
            }
            emit logMessage(QString("Job concluído — snapshot %1").arg(QString::fromStdString(r.snapshotId)));
            return;
        }
        throw std::runtime_error("job não encontrado");
    });
}

static bool isDue(const JobConfig& job) {
    if (job.lastRunAt.empty()) return true;
    auto last = utcIsoToUnix(job.lastRunAt);
    if (last <= 0) return true;
    return nowUnix() - last >= static_cast<std::int64_t>(std::max(1, job.intervalMinutes)) * 60;
}

void KeeplyMainWindow::onStartAgent() {
    if (!m_agentStop) return;
    saveConfig();
    m_agentStop = false;
    m_agentStatus->setText("Ativo");
    m_agentStatus->setProperty("class", "badgeSuccess");
    m_agentStatus->style()->unpolish(m_agentStatus);
    m_agentStatus->style()->polish(m_agentStatus);

    auto configPath = m_configPath;
    m_agentThread = std::thread([this, configPath]() {
        emit logMessage("Agente iniciado");
        while (!m_agentStop) {
            try {
                auto cfg = Config::load(configPath);
                bool ran = false;
                for (auto& job : cfg.jobs) {
                    if (m_agentStop) break;
                    if (!job.enabled || !isDue(job)) continue;
                    BackupEngine backup(cfg);
                    auto r = backup.run(job.source);
                    job.lastRunAt = nowUtcIso();
                    cfg.save(configPath);
                    if (job.retentionKeepLast >= 0) {
                        PruneEngine prune(cfg);
                        prune.keepLast(job.retentionKeepLast, std::filesystem::absolute(job.source).string());
                    }
                    emit logMessage(QString("Job %1 — snapshot %2")
                        .arg(QString::fromStdString(job.name))
                        .arg(QString::fromStdString(r.snapshotId)));
                    ran = true;
                }
                if (ran) emit requestRefresh();
                int sleepSec = std::max(5, cfg.agent.pollSeconds);
                for (int i = 0; i < sleepSec && !m_agentStop; ++i)
                    std::this_thread::sleep_for(std::chrono::seconds(1));
            } catch (const std::exception& e) {
                emit logMessage(QString("Agente erro: %1").arg(e.what()));
                for (int i = 0; i < 10 && !m_agentStop; ++i)
                    std::this_thread::sleep_for(std::chrono::seconds(1));
            }
        }
        emit logMessage("Agente parado");
    });
}

void KeeplyMainWindow::onStopAgent() {
    m_agentStop = true;
    if (m_agentThread.joinable()) m_agentThread.join();
    m_agentStatus->setText("Inativo");
    m_agentStatus->setProperty("class", "badgeInactive");
    m_agentStatus->style()->unpolish(m_agentStatus);
    m_agentStatus->style()->polish(m_agentStatus);
}

// ════════════════════════════════════════════════════════
//  Config Logic
// ════════════════════════════════════════════════════════

void KeeplyMainWindow::onLoadConfig() {
    loadConfig();
}

void KeeplyMainWindow::onSaveConfig() {
    saveConfig();
    emit logMessage("Configuração salva");
}

void KeeplyMainWindow::onInitLocal() {
    m_cfg = uiConfig();
    m_cfg.repository.type = "local";
    if (m_cfg.dbPath.empty()) m_cfg.dbPath = "./keeply.db";
    if (m_cfg.repository.path.empty()) m_cfg.repository.path = "./repo";
    m_cfg.save(m_configFile->text().toStdString());
    LocalDb db(m_cfg.dbPath);
    db.migrate();
    fillUi();
    emit logMessage("Storage local configurado");
}

void KeeplyMainWindow::onInitS3() {
    m_cfg = uiConfig();
    m_cfg.repository.type = "s3";
    if (m_cfg.dbPath.empty() || m_cfg.dbPath == "./keeply.db") m_cfg.dbPath = "./keeply.minio.db";
    if (m_cfg.repository.endpoint.empty()) m_cfg.repository.endpoint = "http://localhost:9000";
    if (m_cfg.repository.bucket.empty()) m_cfg.repository.bucket = "keeply";
    if (m_cfg.repository.accessKey.empty()) m_cfg.repository.accessKey = "keeply";
    if (m_cfg.repository.secretKey.empty()) m_cfg.repository.secretKey = "keeply123456";
    if (m_cfg.repository.region.empty()) m_cfg.repository.region = "us-east-1";
    if (m_cfg.repository.prefix.empty() || m_cfg.repository.prefix == "repos/default")
        m_cfg.repository.prefix = "repos/dev-machine";
    m_cfg.save(m_configFile->text().toStdString());
    LocalDb db(m_cfg.dbPath);
    db.migrate();
    fillUi();
    emit logMessage("MinIO/S3 configurado");
}

// ════════════════════════════════════════════════════════
//  Data Sync
// ════════════════════════════════════════════════════════

Config KeeplyMainWindow::uiConfig() {
    Config c = m_cfg;
    c.dbPath = m_dbPath->text().toStdString();
    c.repository.type = m_storageType->currentText().toStdString();
    c.repository.path = m_localPath->text().toStdString();
    c.repository.endpoint = m_endpoint->text().toStdString();
    c.repository.bucket = m_bucket->text().toStdString();
    c.repository.accessKey = m_accessKey->text().toStdString();
    c.repository.secretKey = m_secretKey->text().toStdString();
    c.repository.region = m_region->text().isEmpty() ? "us-east-1" : m_region->text().toStdString();
    c.repository.prefix = m_prefix->text().toStdString();
    c.encryption.enabled = m_encryption->isChecked();
    c.encryption.keyHex = m_encKey->text().toStdString();
    if (c.encryption.enabled && c.encryption.keyHex.empty())
        c.encryption.keyHex = generateEncryptionKeyHex();
    c.agent.pollSeconds = std::max(5, m_pollSeconds->value());
    return c;
}

void KeeplyMainWindow::fillUi() {
    m_configFile->setText(QString::fromStdString(m_configPath.string()));
    m_dbPath->setText(QString::fromStdString(m_cfg.dbPath));
    m_storageType->setCurrentText(QString::fromStdString(m_cfg.repository.type));
    m_localPath->setText(QString::fromStdString(m_cfg.repository.path));
    m_endpoint->setText(QString::fromStdString(m_cfg.repository.endpoint));
    m_bucket->setText(QString::fromStdString(m_cfg.repository.bucket));
    m_accessKey->setText(QString::fromStdString(m_cfg.repository.accessKey));
    m_secretKey->setText(QString::fromStdString(m_cfg.repository.secretKey));
    m_region->setText(QString::fromStdString(m_cfg.repository.region));
    m_prefix->setText(QString::fromStdString(m_cfg.repository.prefix));
    m_encryption->setChecked(m_cfg.encryption.enabled);
    m_encKey->setText(QString::fromStdString(m_cfg.encryption.keyHex));
    m_pollSeconds->setValue(m_cfg.agent.pollSeconds);
    refreshJobs();
    refreshSnapshots();
}

bool KeeplyMainWindow::ensureDefaultConfig(const QString& label) {
    m_cfg = Config{};
    if (m_configPath.empty()) {
        fillUi();
        emit logMessage(label + ": usando padrão local");
        return true;
    }
    try {
        m_cfg.save(m_configPath);
        LocalDb db(m_cfg.dbPath);
        db.migrate();
        fillUi();
        emit logMessage(label + ": padrão local criado");
        return true;
    } catch (const std::exception& e) {
        emit logMessage(label + ": " + QString(e.what()));
        return false;
    }
}

void KeeplyMainWindow::refreshJobs() {
    m_jobList->clear();
    for (const auto& job : m_cfg.jobs) {
        QString row = QString("%1  |  %2  |  %3 min  |  keep=%4  |  %5")
            .arg(QString::fromStdString(job.name))
            .arg(job.enabled ? "ativo" : "inativo")
            .arg(job.intervalMinutes)
            .arg(job.retentionKeepLast)
            .arg(QString::fromStdString(job.source));
        m_jobList->addItem(row);
    }
}

void KeeplyMainWindow::refreshSnapshots() {
    if (m_snapList) m_snapList->clear();
    if (m_restoreSnapList) m_restoreSnapList->clear();
    try {
        LocalDb db(m_cfg.dbPath);
        db.migrate();
        bool first = true;
        for (const auto& s : db.listSnapshots()) {
            QString snapRow = QString("%1  |  %2  |  %3  |  files=%4  |  %5 bytes")
                .arg(QString::fromStdString(s.id))
                .arg(QString::fromStdString(s.createdAt))
                .arg(QString::fromStdString(s.status))
                .arg(s.totalFiles)
                .arg(s.totalBytes);
            if (m_snapList) m_snapList->addItem(snapRow);

            if (m_restoreSnapList) {
                QString restoreRow = QString("%1  |  %2  |  %3 arq  |  %4 bytes")
                    .arg(QString::fromStdString(s.id))
                    .arg(QString::fromStdString(s.createdAt))
                    .arg(s.totalFiles)
                    .arg(s.totalBytes);
                m_restoreSnapList->addItem(restoreRow);
            }

            if (first) {
                if (m_snapList) m_snapList->setCurrentRow(0);
                if (m_restoreSnapList) m_restoreSnapList->setCurrentRow(0);
                m_restoreSnapshot->setText(QString::fromStdString(s.id));
                if (m_filesSnapshot) m_filesSnapshot->setText(QString::fromStdString(s.id));
                first = false;
            }
        }
    } catch (const std::exception& e) {
        emit logMessage(QString("Snapshots: %1").arg(e.what()));
    }
}

void KeeplyMainWindow::saveConfig() {
    m_configPath = m_configFile->text().toStdString();
    m_cfg = uiConfig();
    m_cfg.save(m_configPath);
    LocalDb db(m_cfg.dbPath);
    db.migrate();
    fillUi();
}

void KeeplyMainWindow::loadConfig() {
    try {
        m_configPath = m_configFile->text().toStdString();
        m_cfg = Config::load(m_configPath);
        fillUi();
        emit logMessage("Config carregada");
    } catch (const std::exception& e) {
        ensureDefaultConfig("Config");
    }
}

void KeeplyMainWindow::onRefresh() {
    try {
        m_cfg = Config::load(m_configPath);
        fillUi();
    } catch (const std::exception& e) {
        ensureDefaultConfig("Refresh");
    }
}

template <class Fn>
void KeeplyMainWindow::runAsync(const QString& label, Fn fn) {
    std::thread([this, label, fn = std::move(fn)]() mutable {
        try {
            emit logMessage(label + " iniciado...");
            fn();
            emit logMessage(label + " concluído");
            emit requestRefresh();
        } catch (const std::exception& e) {
            emit logMessage(label + " erro: " + QString(e.what()));
        }
    }).detach();
}

} // namespace keeply
