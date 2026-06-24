#pragma once

#include <QMainWindow>
#include <QStackedWidget>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLineEdit>
#include <QComboBox>
#include <QCheckBox>
#include <QSpinBox>
#include <QListWidget>
#include <QProgressBar>
#include <QTreeWidget>
#include <QTextEdit>
#include <QPushButton>
#include <QLabel>
#include <QFrame>
#include <QTimer>

#include <atomic>
#include <thread>
#include <filesystem>

#include "keeply/Config.hpp"

namespace keeply {

class KeeplyMainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit KeeplyMainWindow(std::filesystem::path configPath, QWidget* parent = nullptr);
    ~KeeplyMainWindow() override;

signals:
    void logMessage(const QString& msg);
    void requestRefresh();
    void backupProgress(qint64 done, qint64 total, qint64 bytes, const QString& file);
    void restoreProgress(qint64 done, qint64 total, qint64 bytes, const QString& file);

private slots:
    void onNavClicked(int index);
    void appendLog(const QString& msg);

    // Backup
    void onPickBackupSource();
    void onRunBackup();
    void onBackupProgress(qint64 done, qint64 total, qint64 bytes, const QString& file);

    // Restore
    void onPickRestoreTarget();
    void onRunRestore();
    void onRunVerify();
    void onRefreshRestoreSnapshots();
    void onRestoreProgress(qint64 done, qint64 total, qint64 bytes, const QString& file);

    // Snapshots
    void onRefreshSnapshots();
    void onPrune();
    void onLoadFiles();
    void onSnapshotSelectionChanged();

    // Jobs
    void onAddJob();
    void onRemoveJob();
    void onRunJob();
    void onStartAgent();
    void onStopAgent();

    // Config
    void onLoadConfig();
    void onSaveConfig();
    void onInitLocal();
    void onInitS3();

    void onRefresh();

private:
    // ─── Builders ──────────────────────
    QWidget* buildSidebar();
    QWidget* buildBackupPage();
    QWidget* buildRestorePage();
    QWidget* buildSnapshotsPage();
    QWidget* buildJobsPage();
    QWidget* buildConfigPage();
    QWidget* buildLogPanel();

    // ─── Helpers ───────────────────────
    QFrame* makeCard(QLayout* inner);
    QWidget* makePageHeader(const QString& title, const QString& subtitle);
    QPushButton* makePrimary(const QString& text);
    QPushButton* makeSecondary(const QString& text);
    QPushButton* makeDanger(const QString& text);
    QLabel* makeFieldLabel(const QString& text);
    QFrame* makeSeparator();

    void fillUi();
    void refreshJobs();
    void refreshSnapshots();
    Config uiConfig();
    bool ensureDefaultConfig(const QString& label);
    void saveConfig();
    void loadConfig();

    template <class Fn>
    void runAsync(const QString& label, Fn fn);

    // ─── State ─────────────────────────
    std::filesystem::path m_configPath;
    Config m_cfg;
    bool m_loadedDefaultConfig{false};
    std::atomic_bool m_agentStop{true};
    std::thread m_agentThread;

    // ─── UI ────────────────────────────
    QVector<QPushButton*> m_navButtons;
    QStackedWidget* m_contentStack = nullptr;
    QTextEdit* m_logText = nullptr;

    // Backup page
    QLineEdit* m_backupSource = nullptr;
    QProgressBar* m_backupBar = nullptr;
    QLabel* m_backupProgressLabel = nullptr;
    QLabel* m_backupCurrentFile = nullptr;

    // Restore page
    QListWidget* m_restoreSnapList = nullptr;
    QLineEdit* m_restoreSnapshot = nullptr;
    QLineEdit* m_restoreTarget = nullptr;
    QProgressBar* m_restoreBar = nullptr;
    QLabel* m_restoreProgressLabel = nullptr;
    QLabel* m_restoreCurrentFile = nullptr;

    // Snapshots page
    QListWidget* m_snapList = nullptr;
    QLineEdit* m_filesSnapshot = nullptr;
    QTreeWidget* m_filesTree = nullptr;

    // Jobs page
    QLineEdit* m_jobName = nullptr;
    QLineEdit* m_jobSource = nullptr;
    QSpinBox* m_jobInterval = nullptr;
    QSpinBox* m_jobKeep = nullptr;
    QSpinBox* m_pollSeconds = nullptr;
    QListWidget* m_jobList = nullptr;
    QLabel* m_agentStatus = nullptr;

    // Config page
    QLineEdit* m_configFile = nullptr;
    QLineEdit* m_dbPath = nullptr;
    QComboBox* m_storageType = nullptr;
    QLineEdit* m_localPath = nullptr;
    QLineEdit* m_endpoint = nullptr;
    QLineEdit* m_bucket = nullptr;
    QLineEdit* m_accessKey = nullptr;
    QLineEdit* m_secretKey = nullptr;
    QLineEdit* m_region = nullptr;
    QLineEdit* m_prefix = nullptr;
    QCheckBox* m_encryption = nullptr;
    QLineEdit* m_encKey = nullptr;
};

} // namespace keeply
