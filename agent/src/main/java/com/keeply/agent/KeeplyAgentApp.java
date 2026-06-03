package com.keeply.agent;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.config.AgentConfigReader;
import com.keeply.agent.config.AgentConfigWriter;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.BackupProgressListener;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.core.RestoreEngine;
import com.keeply.agent.core.RestoreEngine.OverwritePolicy;
import com.keeply.agent.daemon.AgentPaths;
import com.keeply.agent.daemon.DaemonLauncher;
import com.keeply.agent.daemon.DaemonLogStreamer;
import com.keeply.agent.model.DeviceSession;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.model.SnapshotSummary;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.util.Duration;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeeplyAgentApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(KeeplyAgentApp.class);
    private static final int VISIBLE_LOG_LIMIT_CHARS = 300_000;
    private static final int MAX_ACTIVITY_EVENTS = 2_500;
    private final StringBuilder logBuffer = new StringBuilder();
    private final AtomicBoolean logFlushScheduled = new AtomicBoolean(false);
    private BackendClient backend;
    private LocalDatabase db;
    private UUID deviceId;
    private DeviceAuthStore deviceAuthStore;
    private AgentConfigReader configReader;
    private AgentConfigWriter configWriter;
    private final TextArea logs = new TextArea();
    private final ObservableList<ActivityEvent> allActivityEvents = FXCollections.observableArrayList();
    private final FilteredList<ActivityEvent> filteredActivityEvents = new FilteredList<>(allActivityEvents, event -> true);
    private ListView<ActivityEvent> activityListView;
    private VBox activityBackupProgressCard;
    private Label activityBackupProgressTitle;
    private Label activityBackupProgressPercent;
    private Label activityBackupProgressDetail;
    private ProgressBar activityBackupProgressBar;

    private TextField backendUrl;
    private TextField email;
    private PasswordField password;
    private TextArea backupSourcesConfig;
    private Label status;
    private BorderPane appShell;
    private StackPane contentHost;
    private Label shellPageLabel;
    private com.keeply.agent.ui.MainShellController mainShellController;
    private com.keeply.agent.ui.DashboardController dashboardController;
    private final Map<String, Node> appViews = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private Runnable restoreRefresh = () -> {};
    private Runnable configRefresh = () -> {};
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        redirectSystemErrToTextArea();
        log.info("Iniciando Keeply Agent UI...");
        stage.initStyle(StageStyle.UNDECORATED);
        
        Path uiDbPath = AgentPaths.resolveUiDbPath();
        try {
            Files.createDirectories(uiDbPath.getParent());
            Files.createDirectories(AgentPaths.resolveLogPath().getParent());
            Files.createDirectories(AgentPaths.resolveConfigDir());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar diretórios do agente", e);
        }
        db = new LocalDatabase(uiDbPath.toString());
        deviceAuthStore = new DeviceAuthStore(AgentPaths.resolveDeviceAuthPath());
        configReader = new AgentConfigReader(AgentPaths.resolveDefaultConfigPath());
        configWriter = new AgentConfigWriter(AgentPaths.resolveDefaultConfigPath());

        
        String savedBackendUrl = "http://localhost:8080";
        try {
            Optional<AgentConfigReader.UiConfig> cfg = configReader.read();
            if (cfg.isPresent() && cfg.get().backendUrl() != null && !cfg.get().backendUrl().isBlank()) {
                savedBackendUrl = cfg.get().backendUrl();
            }
        } catch (Exception ignored) {}

        backendUrl = new TextField(savedBackendUrl);
        email = new TextField();
        password = new PasswordField();
        status = new Label("Desconectado");
        backupSourcesConfig = new TextArea();
        backupSourcesConfig.setPrefRowCount(4);
        backupSourcesConfig.setPromptText("Ex: /home/usuario/Documentos\n/home/usuario/Imagens");

        logs.setEditable(false);

        appViews.put("Login", loginView());
        appViews.put("Dashboard", dashboardView());
        appViews.put("Backup", configView(stage));
        appViews.put("Restore", restoreView(stage)); // Meus arquivos
        logs.getStyleClass().add("log-surface");
        Node activityView = buildActivityView();
        appViews.put("Atividade", activityView);
        appViews.put("Logs", activityView);
        appShell = buildAppShell(stage);

        // Tenta auto-login de forma assíncrona
        status.setText("Verificando credenciais locais...");
        Thread.startVirtualThread(() -> {
            try {
                Optional<DeviceSession> saved = deviceAuthStore.load();
                boolean authenticated = saved.isPresent();
                if (authenticated) {
                    DaemonLauncher.ensureRunning(this::log);
                }

                Platform.runLater(() -> {
                    if (saved.isPresent()) {
                        DeviceSession session = saved.get();
                        backend = new BackendClient(backendUrl.getText().trim(), deviceAuthStore);
                        backend.setSession(session);
                        deviceId = session.deviceId();
                        status.setText("Conectado. Device: " + deviceId);
                        if (mainShellController != null && session.email() != null) {
                            mainShellController.setProfile(session.email());
                        }
                    } else {
                        status.setText("Desconectado");
                    }
                    updateAuthenticationNavigation(authenticated);
                    showView(authenticated ? "Dashboard" : "Login");
                });
            } catch (IllegalStateException e) {
                log.error("Erro crítico ao carregar sessão: ", e);
                Platform.runLater(() -> {
                    status.setText("Erro de sessão");
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erro Crítico de Segurança");
                    alert.setHeaderText("Falha no Fallback de Criptografia");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();

                    updateAuthenticationNavigation(false);
                    showView("Login");
                });
            } catch (Exception e) {
                log.error("Erro desconhecido ao carregar sessão: ", e);
                Platform.runLater(() -> {
                    status.setText("Desconectado");
                    updateAuthenticationNavigation(false);
                    showView("Login");
                });
            }
        });

        Scene scene = new Scene(appShell, 1240, 760);
        scene.getStylesheets().add(getClass().getResource("/keeply-theme.css").toExternalForm());
        stage.setTitle("Keeply");
        applyAppIcon(stage);
        stage.setScene(scene);
        stage.show();
        this.primaryStage = stage;

        Thread.startVirtualThread(new DaemonLogStreamer(AgentPaths.resolveLogPath(), this::appendLogs));
    }

    private Pane loginView() {
        VBox box = box();
        box.getStyleClass().add("screen-root");
        box.setAlignment(Pos.CENTER);
        box.setSpacing(20);
        box.setPadding(new Insets(40));

        Label title = new Label("Bem-vindo ao Keeply");
        title.getStyleClass().add("page-title");

        GridPane grid = grid();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(15);

        backendUrl.setPromptText("URL do Servidor");
        email.setPromptText("Seu email");
        password.setPromptText("Sua senha");
        status.setWrapText(true);
        status.setMaxWidth(420);
        status.setStyle("-fx-text-fill: #6B6993;");

        Button loginBtn = new Button("ENTRAR NA CONTA");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.getStyleClass().addAll("btn-primary", "btn-wide");

        loginBtn.setOnAction(e -> {
            loginBtn.setDisable(true);
            status.setText("Validando credenciais...");
            status.setStyle("-fx-text-fill: #6B6993;");
            runAsync(() -> {
                String userEmail = email.getText().trim();
                String userPass = password.getText();

                log("event=ui.login status=started email=" + userEmail);
                try {
                    String hostname = InetAddress.getLocalHost().getHostName();
                    backend = new BackendClient(backendUrl.getText().trim(), deviceAuthStore);
                    String installationId = DeviceIdentity.getOrCreate();
                    DeviceSession session = backend.loginDevice(userEmail, userPass, installationId, hostname, System.getProperty("os.name"), "0.1.0");
                    deviceId = session.deviceId();
                    synchronizePlanAfterLogin();
                    if (backend.hasPersistedSession()) {
                        DaemonLauncher.ensureRunning(this::log, true);
                    } else {
                        log("event=daemon.start status=skipped reason=session_not_persisted");
                    }

                    ui(() -> {
                        status.setText("Conectado. Device: " + deviceId);
                        status.setStyle("-fx-text-fill: #047857;");
                        if (mainShellController != null && session.email() != null) {
                            mainShellController.setProfile(session.email());
                        }
                        updateAuthenticationNavigation(true);
                        showView("Dashboard");
                    });
                    log("event=ui.login status=completed device_id=" + deviceId);
                } catch (Exception ex) {
                    String userMessage = getErrorMessage(ex);
                    log("Erro no login: " + userMessage);
                    ui(() -> {
                        status.setText(userMessage);
                        status.setStyle("-fx-text-fill: #B91C1C;");
                        loginBtn.setDisable(false);
                    });
                    throw ex;
                }
            });
        });

        grid.addRow(0, new Label("E-mail:"), email);
        grid.addRow(1, new Label("Senha:"), password);
        grid.add(loginBtn, 1, 2);
        grid.add(status, 1, 3);

        Label serverLabel = new Label(backendUrl.getText() != null && !backendUrl.getText().isBlank()
                ? backendUrl.getText().trim()
                : "—");
        serverLabel.setWrapText(true);
        serverLabel.setMaxWidth(420);
        serverLabel.setStyle("-fx-text-fill: #6B6993;");

        VBox serverBox = new VBox(6);
        Label serverTitle = new Label("Servidor");
        serverTitle.getStyleClass().add("card-subtitle");
        serverBox.setAlignment(Pos.CENTER);
        serverBox.getChildren().addAll(serverTitle, serverLabel);

        box.getChildren().addAll(title, new Label("Faça login para sincronizar seus backups com a nuvem."), grid, serverBox);
        return box;
    }

    private BorderPane buildAppShell(Stage stage) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
            BorderPane shell = loader.load();
            this.mainShellController = loader.getController();
            this.mainShellController.setNavigationHandler(this::showView);
            this.mainShellController.setOnLogout(() -> {
                try {
                    deviceAuthStore.save(null); // Limpa sessão
                    backend.setSession(null);
                    deviceId = null;
                    updateAuthenticationNavigation(false);
                    showView("Login");
                    log.info("Usuário deslogado via menu de perfil.");
                } catch (Exception ex) {
                    log.error("Erro ao realizar logout: ", ex);
                }
            });
            this.mainShellController.bindWindow(stage);
            this.contentHost = this.mainShellController.getContentHost();
            if (this.contentHost == null) {
                 log("Erro: contentHost não encontrado no MainShell.fxml");
                 this.contentHost = new StackPane();
                 shell.setCenter(this.contentHost);
            }
            
            navButtons.put("Dashboard", (Button) shell.lookup("#btnInicio"));
            navButtons.put("Restore", (Button) shell.lookup("#btnMeusArquivos"));
            navButtons.put("Backup", (Button) shell.lookup("#btnBackups"));
            Button activityBtn = (Button) shell.lookup("#btnAtividade");
            navButtons.put("Atividade", activityBtn);
            navButtons.put("Logs", activityBtn);
            
            shellPageLabel = new Label();
            return shell;
        } catch (Exception e) {
            log.error("Falha ao carregar MainShell.fxml", e);
            log("ERRO ao carregar MainShell.fxml: " + formatException(e));
            VBox errorBox = new VBox(10);
            errorBox.setPadding(new Insets(48));
            Label title = new Label("Erro ao carregar MainShell.fxml");
            title.getStyleClass().add("card-title");
            TextArea details = new TextArea(formatException(e));
            details.setEditable(false);
            details.setWrapText(true);
            details.setPrefRowCount(10);
            errorBox.getChildren().addAll(title, details);
            return new BorderPane(errorBox);
        }
    }

    private Button navigationButton(String view, String label, String iconName) {
        Button button = new Button(label, createNavigationIcon(iconName));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> showView(view));
        navButtons.put(view, button);
        return button;
    }

    private void showView(String view) {
        Node content = appViews.get(view);
        if (content == null || contentHost == null) return;
        contentHost.getChildren().setAll(content);
        shellPageLabel.setText(view.equals("Restore") ? "Restauracao" : view);
        navButtons.forEach((name, button) -> {
            if (button != null) {
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), name.equals(view));
            }
        });
        if ("Restore".equals(view)) {
            restoreRefresh.run();
        }
        if ("Dashboard".equals(view)) {
            refreshDashboard();
        }
        if ("Backup".equals(view)) {
            configRefresh.run();
        }
    }

    private void updateAuthenticationNavigation(boolean authenticated) {
        navButtons.forEach((view, button) -> {
            if (button != null) {
                boolean shown = authenticated ? !"Login".equals(view) : "Login".equals(view);
                button.setVisible(shown);
                button.setManaged(shown);
            }
        });
    }

    private Pane dashboardView() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Dashboard.fxml"));
            Pane root = loader.load();
            dashboardController = loader.getController();
            dashboardController.setOnNavigate(this::showView);
            dashboardController.setOnBackupNow(() -> {
                if (backend == null || deviceId == null) {
                    log("Faça login primeiro para iniciar o backup manual.");
                    return;
                }
                dashboardController.setBackupInProgress(true);
                updateManualBackupProgress(0, "Preparando backup", null, BackupProgressState.RUNNING);
                runAsync(() -> {
                    boolean failed = false;
                    try {
                        log("Iniciando backup manual pelo Dashboard...");
                        var optPlan = backend.getDevicePlan(deviceId);
                        List<String> sources = optPlan.isPresent() ? optPlan.get().sources() : parseSources(backupSourcesConfig.getText());
                        if (sources == null || sources.isEmpty()) {
                            log("Nenhuma pasta configurada para backup.");
                            ui(() -> {
                                dashboardController.setBackupProgressVisible(false);
                                hideActivityBackupProgress();
                            });
                            return;
                        }
                        for (String sourcePath : sources) {
                            try {
                                Path source = Path.of(sourcePath);
                                BackupProgressListener progressListener = progress -> updateManualBackupProgress(
                                        progress.percent(), progress.message(), progress.sourceRoot(), BackupProgressState.RUNNING);
                                UUID snapshotId = new BackupEngine(backend, db, progressListener).backup(deviceId, source);
                                if (snapshotId != null) {
                                    log("event=ui.backup.manual status=completed source=" + sourcePath + " snapshot_id=" + snapshotId);
                                } else {
                                    log("event=ui.backup.manual status=skipped source=" + sourcePath + " reason=already_running");
                                    updateManualBackupProgress(-1, "Backup já em andamento", source, BackupProgressState.FAILED);
                                }
                            } catch (Exception ex) {
                                failed = true;
                                log("event=ui.backup.manual status=failed source=" + sourcePath + " message=" + getErrorMessage(ex));
                                updateManualBackupProgress(-1, "Backup falhou", Path.of(sourcePath), BackupProgressState.FAILED);
                            }
                        }
                        refreshDashboard();
                        if (failed) {
                            updateManualBackupProgress(-1, "Backup finalizado com falhas", null, BackupProgressState.FAILED);
                            log("event=ui.backup.manual status=finished_with_errors");
                        } else {
                            updateManualBackupProgress(100, "Backup concluído", null, BackupProgressState.COMPLETED);
                            log("event=ui.backup.manual status=all_complete");
                        }
                    } finally {
                        ui(() -> dashboardController.setBackupInProgress(false));
                    }
                });
            });
            return root;
        } catch (Exception e) {
            e.printStackTrace();
            return new VBox(new Label("Erro ao carregar Dashboard.fxml"));
        }
    }

    private Node buildActivityView() {
        VBox root = new VBox(12);
        root.getStyleClass().add("activity-root");
        root.setPadding(new Insets(12));

        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("activity-toolbar");
        ToggleGroup group = new ToggleGroup();
        toolbar.getChildren().addAll(
                createActivityFilter("Todos", "all", group, true),
                createActivityFilter("Backup", "backup", group, false),
                createActivityFilter("Daemon", "daemon", group, false),
                createActivityFilter("UI", "ui", group, false),
                createActivityFilter("Erros", "error", group, false)
        );

        activityListView = new ListView<>(filteredActivityEvents);
        activityListView.getStyleClass().add("activity-list");
        activityListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ActivityEvent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox wrapper = new VBox(6);

                boolean showDayHeader = getIndex() == activityListView.getItems().size() - 1
                        || getIndex() == 0
                        || !item.dateLabel().equals(activityListView.getItems().get(getIndex() - 1).dateLabel());
                if (showDayHeader) {
                    Label day = new Label(item.dateLabel());
                    day.getStyleClass().add("activity-day-header");
                    wrapper.getChildren().add(day);
                }

                StackPane marker = new StackPane();
                marker.getStyleClass().add("activity-marker");
                Region line = new Region();
                line.getStyleClass().add("activity-marker-line");
                Circle dot = new Circle(8);
                dot.getStyleClass().addAll("activity-dot", "activity-dot-" + item.state());
                Label check = new Label(item.state().equals("running") ? "" : "✓");
                check.getStyleClass().add("activity-dot-check");
                marker.getChildren().addAll(line, dot, check);

                Label headline = new Label(item.headline());
                headline.getStyleClass().add("activity-headline");
                Label detail = new Label(item.detail());
                detail.getStyleClass().add("activity-message");
                detail.setWrapText(true);

                HBox top = new HBox(8);
                Label tag = new Label(item.categoryLabel());
                tag.getStyleClass().addAll("activity-tag", "activity-tag-" + item.category().toLowerCase(Locale.ROOT));
                top.getChildren().addAll(headline, tag);

                VBox body = new VBox(3, top, detail);
                body.getStyleClass().add("activity-item");

                HBox row = new HBox(10, marker, body);
                row.getStyleClass().add("activity-row");
                HBox.setHgrow(body, Priority.ALWAYS);
                wrapper.getChildren().add(row);

                setGraphic(wrapper);
                setText(null);
            }
        });

        VBox.setVgrow(activityListView, Priority.ALWAYS);
        activityBackupProgressCard = createActivityBackupProgressCard();
        activityBackupProgressCard.setVisible(false);
        activityBackupProgressCard.setManaged(false);
        root.getChildren().addAll(toolbar, activityBackupProgressCard, activityListView);
        return root;
    }

    private VBox createActivityBackupProgressCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("activity-backup-progress-card");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane icon = new StackPane();
        icon.getStyleClass().add("activity-backup-progress-icon");
        SVGPath shield = new SVGPath();
        shield.setContent("M8 1 15 4.5V11L8 15 1 11V4.5z M8 2.5v11 M2 5l6 3 6-3");
        shield.getStyleClass().add("activity-backup-progress-icon-shape");
        icon.getChildren().add(shield);

        activityBackupProgressTitle = new Label("Backup em andamento");
        activityBackupProgressTitle.getStyleClass().add("activity-backup-progress-title");
        activityBackupProgressDetail = new Label("Preparando backup");
        activityBackupProgressDetail.getStyleClass().add("activity-backup-progress-detail");
        activityBackupProgressDetail.setWrapText(true);

        VBox text = new VBox(3, activityBackupProgressTitle, activityBackupProgressDetail);
        HBox.setHgrow(text, Priority.ALWAYS);

        activityBackupProgressPercent = new Label("0%");
        activityBackupProgressPercent.getStyleClass().add("activity-backup-progress-percent");

        header.getChildren().addAll(icon, text, activityBackupProgressPercent);

        activityBackupProgressBar = new ProgressBar(0);
        activityBackupProgressBar.setMaxWidth(Double.MAX_VALUE);
        activityBackupProgressBar.getStyleClass().add("activity-backup-progress-bar");

        card.getChildren().addAll(header, activityBackupProgressBar);
        return card;
    }

    private void updateManualBackupProgress(int percent, String message, Path sourceRoot, BackupProgressState state) {
        ui(() -> {
            int clampedPercent = percent < 0 ? currentActivityBackupPercent() : Math.max(0, Math.min(100, percent));
            String shortMessage = message == null || message.isBlank() ? "Processando arquivos" : message;
            String displayMessage = shortMessage;
            if (sourceRoot != null) {
                displayMessage = displayMessage + ": " + sourceRoot.toAbsolutePath().normalize();
            }

            if (dashboardController != null) {
                dashboardController.setBackupProgressVisible(true);
                dashboardController.updateBackupProgress(clampedPercent, shortMessage);
            }

            if (activityBackupProgressCard != null) {
                activityBackupProgressCard.setVisible(true);
                activityBackupProgressCard.setManaged(true);
            }
            if (activityBackupProgressTitle != null) {
                activityBackupProgressTitle.setText(switch (state) {
                    case RUNNING -> "Backup em andamento";
                    case COMPLETED -> "Backup concluído";
                    case FAILED -> "Backup falhou";
                });
            }
            if (activityBackupProgressPercent != null) {
                activityBackupProgressPercent.setText(clampedPercent + "%");
            }
            if (activityBackupProgressDetail != null) {
                activityBackupProgressDetail.setText(displayMessage);
            }
            if (activityBackupProgressBar != null) {
                activityBackupProgressBar.setProgress(clampedPercent / 100.0);
            }
        });
    }

    private int currentActivityBackupPercent() {
        if (activityBackupProgressPercent == null) {
            return 0;
        }
        String text = activityBackupProgressPercent.getText();
        if (text == null || !text.endsWith("%")) {
            return 0;
        }
        try {
            return Integer.parseInt(text.substring(0, text.length() - 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void hideActivityBackupProgress() {
        if (activityBackupProgressCard != null) {
            activityBackupProgressCard.setVisible(false);
            activityBackupProgressCard.setManaged(false);
        }
    }

    private ToggleButton createActivityFilter(String label, String category, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(label);
        button.getStyleClass().add("activity-filter-btn");
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setOnAction(e -> filteredActivityEvents.setPredicate(event -> "all".equals(category) || event.category().equalsIgnoreCase(category)));
        return button;
    }

    private void refreshDashboard() {
        if (backend == null || deviceId == null || dashboardController == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                var snapshots = backend.listSnapshots();
                String lastDate = snapshots.isEmpty() ? "Nunca" : snapshots.getFirst().startedAt().toString().substring(0, 10);
                var optPlan = backend.getDevicePlan(deviceId);
                List<String> currentSources = optPlan.isPresent() ? optPlan.get().sources() : parseSources(backupSourcesConfig.getText());
                long usedBytes = backend.getStorageUsedBytes();
                String storageDisplay = formatStorageSize(usedBytes);
                double capacityBytes = 1024.0 * 1024 * 1024 * 1024;
                double storagePercent = Math.min(1.0, usedBytes / capacityBytes);

                ui(() -> {
                    dashboardController.updateStats(lastDate, String.valueOf(snapshots.size()), storageDisplay);
                    dashboardController.setFolders(currentSources);
                    dashboardController.setSnapshotsList(snapshots);
                    if (mainShellController != null) {
                        mainShellController.updateStorageInfo(storageDisplay, storagePercent);
                    }
                });
            } catch (Exception e) {
                log("Falha ao atualizar dashboard: " + e.getMessage());
            }
        });
    }

    private static String formatStorageSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = -1;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex]);
    }

    private Pane restoreView(Stage stage) {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().addAll("screen-root", "restore-shell");
        layout.setPadding(new Insets(16));

        // ── Main card (Acronis-style) ────────────────────────────────────────
        VBox card = new VBox(0);
        card.getStyleClass().add("settings-panel");
        BorderPane.setMargin(card, Insets.EMPTY);

        // ── Header ────────────────────────────────────────────────────────────
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("settings-header");

        SVGPath folderSvg = new SVGPath();
        folderSvg.setContent("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
        folderSvg.setStyle("-fx-fill: #6D47FF;");
        StackPane folderIconBox = new StackPane(folderSvg);
        folderIconBox.setPrefSize(20, 20); folderIconBox.setMinSize(20, 20); folderIconBox.setMaxSize(20, 20);

        Label headerTitle = new Label("Meus Arquivos");
        headerTitle.getStyleClass().add("settings-panel-title");

        Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Button backToSnapshots = new Button("← Voltar");
        backToSnapshots.getStyleClass().add("btn-outline-primary");
        backToSnapshots.setVisible(false);
        backToSnapshots.setManaged(false);

        Label currentPathLabel = new Label("Snapshots");
        currentPathLabel.getStyleClass().add("card-subtitle");

        headerBox.getChildren().addAll(folderIconBox, headerTitle, hSpacer, backToSnapshots, currentPathLabel);
        card.getChildren().addAll(headerBox, makeDivider());

        // ── Snapshots section ─────────────────────────────────────────────────
        VBox snapshotsPane = new VBox(0);
        VBox.setVgrow(snapshotsPane, Priority.ALWAYS);

        HBox sectionRow = new HBox();
        sectionRow.setAlignment(Pos.CENTER_LEFT);
        sectionRow.getStyleClass().add("settings-row");
        Label sectionLbl = new Label("PONTOS DE RESTAURAÇÃO");
        sectionLbl.getStyleClass().add("section-title");
        sectionRow.getChildren().add(sectionLbl);
        snapshotsPane.getChildren().add(sectionRow);

        ListView<SnapshotSummary> snapshotList = new ListView<>();
        snapshotList.getStyleClass().add("restore-snap-list");
        VBox.setVgrow(snapshotList, Priority.ALWAYS);
        snapshotsPane.getChildren().add(snapshotList);

        // ── File browser section ──────────────────────────────────────────────
        Label browserStatus = new Label("Selecione um snapshot concluído e clique em Carregar itens.");
        browserStatus.getStyleClass().add("card-subtitle");
        browserStatus.setPadding(new Insets(0, 20, 10, 20));

        Button actionRestore = new Button("Recuperar selecionados");
        actionRestore.getStyleClass().add("btn-primary");
        actionRestore.setDisable(true);

        VBox rightActions = new VBox(10, actionRestore);
        rightActions.setPadding(new Insets(0, 0, 0, 12));
        rightActions.setMinWidth(190);
        rightActions.setMaxWidth(190);

        TreeView<RestoreNode> fileTree = new TreeView<>();
        fileTree.getStyleClass().add("explorer-tree");
        fileTree.setShowRoot(true);
        VBox.setVgrow(fileTree, Priority.ALWAYS);

        BorderPane itemsPane = new BorderPane();
        itemsPane.setCenter(fileTree);
        itemsPane.setRight(rightActions);
        itemsPane.setPadding(new Insets(0, 20, 16, 20));

        VBox fileBrowser = new VBox(0, browserStatus, itemsPane);
        VBox.setVgrow(itemsPane, Priority.ALWAYS);
        fileBrowser.setVisible(false);
        fileBrowser.setManaged(false);

        SnapshotSummary[] selectedSnapshot = new SnapshotSummary[1];

        Runnable showSnapshotsList = () -> {
            fileBrowser.setVisible(false);
            fileBrowser.setManaged(false);
            snapshotsPane.setVisible(true);
            snapshotsPane.setManaged(true);
            backToSnapshots.setVisible(false);
            backToSnapshots.setManaged(false);
            currentPathLabel.setText("Snapshots");
            fileTree.setRoot(null);
            actionRestore.setDisable(true);
        };

        java.util.function.Consumer<SnapshotSummary> recoverSnapshot = snapshot -> {
            if (snapshot == null) return;
            runAsync(() -> new RestoreEngine(backend).restore(snapshot.id(), null, null, OverwritePolicy.ALWAYS));
        };

        java.util.function.Consumer<SnapshotSummary> loadItems = snapshot -> {
            if (snapshot == null) return;
            if (!"COMPLETED".equals(snapshot.status()))
                throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status() + ")");
            runAsync(() -> {
                ui(() -> {
                    browserStatus.setText("Carregando pastas...");
                    fileTree.setRoot(null);
                    snapshotsPane.setVisible(false);
                    snapshotsPane.setManaged(false);
                    fileBrowser.setVisible(true);
                    fileBrowser.setManaged(true);
                    backToSnapshots.setVisible(true);
                    backToSnapshots.setManaged(true);
                    currentPathLabel.setText("Itens > " + snapshot.id().toString().substring(0, 8));
                });
                LazyRestoreTreeItem rootItem = new LazyRestoreTreeItem(
                        new RestoreNode(rootLabelForSnapshot(snapshot.sourcePath()), "", true, 0L, null));
                ui(() -> { fileTree.setRoot(rootItem); rootItem.setExpanded(true); actionRestore.setDisable(true); });
                loadFolderChildren(snapshot, rootItem, browserStatus, () -> {
                    SelectedRestorePaths sel = collectCheckedSelectionsFromTree(fileTree.getRoot());
                    actionRestore.setDisable(sel.files().isEmpty() && sel.directories().isEmpty());
                });
                log("event=ui.restore.items_loaded snapshot_id=" + snapshot.id() + " prefix=/");
            });
        };

        // File tree cell factory (unchanged logic, same style)
        fileTree.setCellFactory(tv -> new TreeCell<>() {
            private final CheckBox check = new CheckBox();
            private final Label text = new Label();
            private final HBox row = new HBox(8);
            private CheckBoxTreeItem<RestoreNode> bound;
            { row.setAlignment(Pos.CENTER_LEFT); row.getChildren().addAll(check, createBootstrapFileIcon(false), text); }

            @Override
            protected void updateItem(RestoreNode item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) { check.selectedProperty().unbindBidirectional(bound.selectedProperty()); bound = null; }
                if (empty || item == null || !(getTreeItem() instanceof CheckBoxTreeItem<?> cbItem)) {
                    setText(null); setGraphic(null); return;
                }
                @SuppressWarnings("unchecked") CheckBoxTreeItem<RestoreNode> typed = (CheckBoxTreeItem<RestoreNode>) cbItem;
                bound = typed;
                check.selectedProperty().bindBidirectional(typed.selectedProperty());
                check.setAllowIndeterminate(false);
                row.getChildren().set(1, createBootstrapFileIcon(item.isDirectory));
                text.setText(item.label);
                text.getStyleClass().setAll(item.isDirectory ? "snapshot-item-title" : "item-muted");
                setText(null); setGraphic(row);
            }
        });
        fileTree.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot instanceof CheckBoxTreeItem<?> cbRoot) {
                @SuppressWarnings("unchecked") CheckBoxTreeItem<RestoreNode> typedRoot = (CheckBoxTreeItem<RestoreNode>) cbRoot;
                bindCheckboxListeners(typedRoot, () -> {
                    SelectedRestorePaths sel = collectCheckedSelectionsFromTree(fileTree.getRoot());
                    actionRestore.setDisable(sel.files().isEmpty() && sel.directories().isEmpty());
                });
            }
        });

        // Snapshot list cell factory — Acronis style
        snapshotList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }

                // Type badge
                String typeText = inferSnapshotType(snapshotList.getItems(), item);
                Label typeBadge = new Label(typeText);
                typeBadge.setStyle(
                    "-fx-background-color: " + ("COMPLETO".equals(typeText) ? "#EDE9FF" : "#EEF2FF") + ";" +
                    "-fx-text-fill: " + ("COMPLETO".equals(typeText) ? "#6D47FF" : "#4338CA") + ";" +
                    "-fx-font-size: 10px; -fx-font-weight: 700;" +
                    "-fx-background-radius: 999px; -fx-padding: 3 8;"
                );

                // Date
                Label dateLbl = new Label(formatSnapshotDateCompact(item.startedAt()));
                dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151; -fx-font-weight: 600;");

                // File count
                Label filesLbl = new Label(item.totalFiles() + " arq.");
                filesLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

                // Status badge
                String statusText = formatSnapshotStatus(item.status());
                String statusBg = switch (item.status() == null ? "" : item.status()) {
                    case "COMPLETED"   -> "#DCFCE7";
                    case "FAILED"      -> "#FEE2E2";
                    case "IN_PROGRESS" -> "#EDE9FF";
                    default            -> "#F1F5F9";
                };
                String statusFg = switch (item.status() == null ? "" : item.status()) {
                    case "COMPLETED"   -> "#16A34A";
                    case "FAILED"      -> "#DC2626";
                    case "IN_PROGRESS" -> "#6D47FF";
                    default            -> "#64748B";
                };
                Label statusBadge = new Label(statusText);
                statusBadge.setStyle(
                    "-fx-background-color: " + statusBg + ";" +
                    "-fx-text-fill: " + statusFg + ";" +
                    "-fx-font-size: 10px; -fx-font-weight: 700;" +
                    "-fx-background-radius: 999px; -fx-padding: 3 8;"
                );

                Region rowSpacer = new Region(); HBox.setHgrow(rowSpacer, Priority.ALWAYS);
                HBox infoRow = new HBox(10, createBootstrapPcIcon(), typeBadge, dateLbl, filesLbl, rowSpacer, statusBadge);
                infoRow.setAlignment(Pos.CENTER_LEFT);

                // Action buttons
                Button btnRecover = new Button("Recuperar snapshot");
                btnRecover.getStyleClass().addAll("btn-outline-primary");
                btnRecover.setOnAction(e -> { snapshotList.getSelectionModel().select(item); recoverSnapshot.accept(item); });

                Button btnLoad = new Button("Carregar itens →");
                btnLoad.getStyleClass().add("btn-primary");
                btnLoad.setDisable(!"COMPLETED".equals(item.status()));
                btnLoad.setOnAction(e -> { snapshotList.getSelectionModel().select(item); loadItems.accept(item); });

                Button btnDelete = new Button("×");
                btnDelete.setTooltip(new Tooltip("Apagar snapshot"));
                btnDelete.getStyleClass().add("snapshot-delete-btn");
                btnDelete.setOnAction(e -> {
                    e.consume();
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.initOwner(stage);
                    confirm.setTitle("Apagar snapshot");
                    confirm.setHeaderText("Apagar este snapshot?");
                    confirm.setContentText("Esta ação remove o snapshot e os arquivos relacionados do histórico.");
                    Optional<ButtonType> result = confirm.showAndWait();
                    if (result.isEmpty() || result.get() != ButtonType.OK) return;

                    runAsync(() -> {
                        backend.deleteSnapshot(item.id());
                        ui(() -> {
                            boolean wasSelected = selectedSnapshot[0] != null
                                    && selectedSnapshot[0].id().equals(item.id());
                            snapshotList.getItems().removeIf(snapshot -> snapshot.id().equals(item.id()));
                            if (wasSelected) {
                                snapshotList.getSelectionModel().clearSelection();
                                selectedSnapshot[0] = null;
                                showSnapshotsList.run();
                            }
                        });
                    });
                });

                Region actionsSpacer = new Region();
                HBox.setHgrow(actionsSpacer, Priority.ALWAYS);
                HBox actionsRow = new HBox(8, btnRecover, btnLoad, actionsSpacer, btnDelete);
                actionsRow.setAlignment(Pos.CENTER_LEFT);
                actionsRow.setPadding(new Insets(8, 0, 4, 0));

                VBox cell = new VBox(6, infoRow, actionsRow);
                cell.setPadding(new Insets(12, 20, 10, 20));
                setText(null);
                setGraphic(cell);
            }
        });

        // ── Wire up sections into card ────────────────────────────────────────
        card.getChildren().addAll(snapshotsPane, fileBrowser);
        VBox.setVgrow(snapshotsPane, Priority.ALWAYS);
        VBox.setVgrow(fileBrowser, Priority.ALWAYS);
        layout.setCenter(card);

        // ── Refresh ───────────────────────────────────────────────────────────
        Runnable refreshSnapshots = () -> {
            if (backend == null) return;
            runAsync(() -> {
                List<SnapshotSummary> snapshots = backend.listSnapshots();
                ui(() -> snapshotList.getItems().setAll(snapshots));
            });
        };
        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshSnapshots.run()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
        restoreRefresh = refreshSnapshots;

        snapshotList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedSnapshot[0] = newVal;
                currentPathLabel.setText("Snapshots > " + newVal.id().toString().substring(0, 8));
            } else {
                selectedSnapshot[0] = null;
                currentPathLabel.setText("Snapshots");
            }
        });

        actionRestore.setOnAction(e -> {
            SnapshotSummary snapshot = selectedSnapshot[0];
            if (snapshot == null || fileTree.getRoot() == null) return;
            SelectedRestorePaths selections = collectCheckedSelectionsFromTree(fileTree.getRoot());
            if (selections.files().isEmpty() && selections.directories().isEmpty()) return;
            Optional<RestoreDestinationMode> choice = showRestoreDestinationDialog(stage);
            if (choice.isEmpty()) return;
            Path destination = null;
            if (choice.get() == RestoreDestinationMode.CUSTOM) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Selecionar pasta de destino");
                java.io.File selected = chooser.showDialog(stage);
                if (selected == null) return;
                destination = selected.toPath();
            }
            Path finalDestination = destination;
            runAsync(() -> {
                Set<String> selectedPaths = resolveSelectedFiles(snapshot, selections);
                if (selectedPaths.isEmpty())
                    throw new IllegalStateException("Nenhum arquivo selecionado para recuperação");
                new RestoreEngine(backend).restore(snapshot.id(), finalDestination, selectedPaths, OverwritePolicy.ALWAYS);
            });
        });

        backToSnapshots.setOnAction(e -> {
            showSnapshotsList.run();
        });

        return layout;
    }

    private Optional<RestoreDestinationMode> showRestoreDestinationDialog(Stage owner) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initOwner(owner);
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        RestoreDestinationMode[] result = {null};

        // ── Card ──────────────────────────────────────────────────────────────
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-color: #E2E8F0;" +
            "-fx-border-radius: 12px;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.14), 24, 0.1, 0, 4);"
        );
        card.setPrefWidth(360);

        // Header
        HBox dHeader = new HBox(10);
        dHeader.setAlignment(Pos.CENTER_LEFT);
        dHeader.setPadding(new Insets(18, 20, 14, 20));
        dHeader.setStyle("-fx-border-color: #F1F5F9; -fx-border-width: 0 0 1 0;");

        SVGPath restoreIco = new SVGPath();
        restoreIco.setContent("M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9z");
        restoreIco.setStyle("-fx-fill: #6D47FF;");
        StackPane icoBox = new StackPane(restoreIco);
        icoBox.setPrefSize(20, 20); icoBox.setMinSize(20, 20); icoBox.setMaxSize(20, 20);

        Label dlgTitle = new Label("Recuperar arquivos");
        dlgTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");

        Region dlgSpacer = new Region(); HBox.setHgrow(dlgSpacer, Priority.ALWAYS);

        Button dlgClose = new Button("×");
        dlgClose.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #94A3B8;" +
            "-fx-font-size: 18px; -fx-padding: 0 4; -fx-cursor: hand;"
        );
        dlgClose.setOnAction(e -> dialog.close());

        dHeader.getChildren().addAll(icoBox, dlgTitle, dlgSpacer, dlgClose);

        // Body
        VBox body = new VBox(14);
        body.setPadding(new Insets(18, 20, 20, 20));

        Label destLbl = new Label("Destino da recuperação");
        destLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #374151;");

        ComboBox<String> destBox = new ComboBox<>();
        destBox.getItems().setAll("Caminho original", "Caminho personalizado");
        destBox.getSelectionModel().selectFirst();
        destBox.setMaxWidth(Double.MAX_VALUE);
        destBox.setStyle(
            "-fx-background-color: #F8FAFC;" +
            "-fx-border-color: #E2E8F0; -fx-border-radius: 8px; -fx-background-radius: 8px;" +
            "-fx-padding: 6 10; -fx-font-size: 13px; -fx-font-weight: 500;"
        );

        // Footer buttons
        Region btnSpacer = new Region(); HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        Button cancelDlg = new Button("Cancelar");
        cancelDlg.getStyleClass().add("btn-outline-primary");
        cancelDlg.setOnAction(e -> { result[0] = null; dialog.close(); });

        Button okDlg = new Button("Recuperar");
        okDlg.getStyleClass().add("btn-primary");
        okDlg.setOnAction(e -> {
            result[0] = "Caminho personalizado".equals(destBox.getValue())
                    ? RestoreDestinationMode.CUSTOM : RestoreDestinationMode.ORIGINAL;
            dialog.close();
        });

        HBox footer = new HBox(8, btnSpacer, cancelDlg, okDlg);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(4, 0, 0, 0));

        body.getChildren().addAll(destLbl, destBox, footer);
        card.getChildren().addAll(dHeader, body);

        // Wrap with outer padding for shadow visibility
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: transparent;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/keeply-theme.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    private SelectedRestorePaths collectCheckedSelectionsFromTree(TreeItem<RestoreNode> root) {
        Set<String> files = new HashSet<>();
        Set<String> directories = new HashSet<>();
        collectCheckedRec(root, files, directories);
        return new SelectedRestorePaths(files, directories);
    }

    private void updateSelectedPathsForVisibleTree(TreeItem<RestoreNode> root, Set<String> selected) {
        Set<String> visible = new HashSet<>();
        collectVisibleFilePaths(root, visible);
        selected.removeAll(visible);
        selected.addAll(collectCheckedSelectionsFromTree(root).files());
    }

    private void collectVisibleFilePaths(TreeItem<RestoreNode> node, Set<String> out) {
        if (node == null || node.getValue() == null) return;
        RestoreNode value = node.getValue();
        if (!value.isDirectory && value.fullPath != null && !value.fullPath.isBlank()) out.add(value.fullPath);
        for (TreeItem<RestoreNode> child : node.getChildren()) collectVisibleFilePaths(child, out);
    }

    private void applyCheckedPaths(CheckBoxTreeItem<RestoreNode> node, Set<String> selected) {
        if (node == null || node.getValue() == null) return;
        RestoreNode value = node.getValue();
        if (!value.isDirectory && selected.contains(value.fullPath)) node.setSelected(true);
        for (TreeItem<RestoreNode> child : node.getChildren()) {
            if (child instanceof CheckBoxTreeItem<?> checkBoxChild) {
                @SuppressWarnings("unchecked")
                CheckBoxTreeItem<RestoreNode> typed = (CheckBoxTreeItem<RestoreNode>) checkBoxChild;
                applyCheckedPaths(typed, selected);
            }
        }
    }

    private void bindCheckboxListeners(CheckBoxTreeItem<RestoreNode> node, Runnable onChange) {
        if (node == null) return;
        node.selectedProperty().addListener((obs, oldVal, newVal) -> onChange.run());
        for (TreeItem<RestoreNode> child : node.getChildren()) {
            if (child instanceof CheckBoxTreeItem<?> cbChild) {
                @SuppressWarnings("unchecked")
                CheckBoxTreeItem<RestoreNode> typed = (CheckBoxTreeItem<RestoreNode>) cbChild;
                bindCheckboxListeners(typed, onChange);
            }
        }
    }

    private void collectCheckedRec(TreeItem<RestoreNode> node, Set<String> files, Set<String> directories) {
        if (node == null || node.getValue() == null) return;
        if (node instanceof CheckBoxTreeItem<?> cbNode) {
            @SuppressWarnings("unchecked")
            CheckBoxTreeItem<RestoreNode> typedNode = (CheckBoxTreeItem<RestoreNode>) cbNode;
            RestoreNode value = typedNode.getValue();
            if (typedNode.isSelected() && value != null) {
                if (value.isDirectory) {
                    directories.add(value.fullPath == null ? "" : value.fullPath);
                } else if (value.fullPath != null && !value.fullPath.isBlank()) {
                    files.add(value.fullPath);
                }
            }
        }
        for (TreeItem<RestoreNode> child : node.getChildren()) {
            collectCheckedRec(child, files, directories);
        }
    }

    private CheckBoxTreeItem<RestoreNode> buildFolderTreeItem(RestoreNode root) {
        if (root == null) return null;
        CheckBoxTreeItem<RestoreNode> item = new CheckBoxTreeItem<>(root);
        item.setIndependent(false);
        for (RestoreNode child : root.children) {
            item.getChildren().add(buildFolderTreeItem(child));
        }
        return item;
    }

    private String inferSnapshotType(List<SnapshotSummary> snapshots, SnapshotSummary current) {
        long olderFromSameSource = snapshots.stream()
                .filter(s -> s.id() != null && !s.id().equals(current.id()))
                .filter(s -> Objects.equals(s.sourcePath(), current.sourcePath()))
                .filter(s -> s.startedAt() != null && current.startedAt() != null && s.startedAt().isBefore(current.startedAt()))
                .count();
        return olderFromSameSource == 0 ? "COMPLETO" : "INCREMENTAL";
    }

    private String formatSnapshotStatus(String status) {
        if (status == null || status.isBlank()) return "-";
        return switch (status) {
            case "IN_PROGRESS" -> "Em progresso";
            case "PROCESSING" -> "Processando";
            case "COMPLETED" -> "Concluído";
            case "FAILED" -> "Falhou";
            default -> status;
        };
    }

    private String formatSnapshotDateCompact(java.time.Instant startedAt) {
        if (startedAt == null) return "-";
        return DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .format(startedAt.atZone(ZoneId.systemDefault()));
    }

    private String rootLabelForSnapshot(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "Backup";
        }
        try {
            Path path = Path.of(sourcePath);
            Path name = path.getFileName();
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }
        } catch (Exception ignored) {
            // fallback para o valor bruto
        }
        return sourcePath;
    }

    private void loadFolderChildren(SnapshotSummary snapshot, LazyRestoreTreeItem parent, Label browserStatus, Runnable onSelectionChanged) {
        if (snapshot == null || parent == null || !parent.getValue().isDirectory || parent.loading || parent.loaded) {
            return;
        }
        parent.loading = true;
        String prefix = parent.getValue().fullPath == null ? "" : parent.getValue().fullPath;
        runAsync(() -> {
            List<BackendClient.SnapshotFileItem> files = backend.listAllSnapshotFiles(snapshot.id(), null, prefix);
            List<RestoreNode> children = buildImmediateChildren(prefix, files, null);
            ui(() -> {
                parent.getChildren().clear();
                for (RestoreNode child : children) {
                    LazyRestoreTreeItem childItem = new LazyRestoreTreeItem(child);
                    if (child.isDirectory) {
                        childItem.addPlaceholder();
                        childItem.expandedProperty().addListener((obs, oldVal, expanded) -> {
                            if (expanded) {
                                loadFolderChildren(snapshot, childItem, browserStatus, onSelectionChanged);
                            }
                        });
                    }
                    bindCheckboxListeners(childItem, onSelectionChanged);
                    parent.getChildren().add(childItem);
                }
                parent.loaded = true;
                parent.loading = false;
                String folder = prefix.isBlank() ? "/" : prefix;
                browserStatus.setText("Pasta: " + folder + " | itens: " + children.size());
                onSelectionChanged.run();
            });
            log("event=ui.restore.folder_loaded snapshot_id=" + snapshot.id() + " prefix=" + (prefix.isBlank() ? "/" : prefix) + " items=" + children.size());
        });
    }

    private List<RestoreNode> buildImmediateChildren(String prefix, List<BackendClient.SnapshotFileItem> files, String search) {
        String normalizedPrefix = prefix == null ? "" : prefix;
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Set<String> folderKeys = new HashSet<>();
        List<RestoreNode> children = new ArrayList<>();

        for (BackendClient.SnapshotFileItem file : files) {
            String path = file.path();
            if (path == null || path.isBlank() || !path.startsWith(normalizedPrefix)) continue;
            String relative = path.substring(normalizedPrefix.length());
            if (relative.isBlank()) continue;

            int slash = relative.indexOf('/');
            if (slash < 0) {
                if (!normalizedSearch.isBlank() && !relative.toLowerCase(Locale.ROOT).contains(normalizedSearch)) continue;
                children.add(new RestoreNode(relative, path, false, file.size(), file.lastModified()));
            } else {
                String folderName = relative.substring(0, slash);
                if (!normalizedSearch.isBlank() && !folderName.toLowerCase(Locale.ROOT).contains(normalizedSearch)) continue;
                String folderPrefix = normalizedPrefix + folderName + "/";
                if (folderKeys.add(folderPrefix)) {
                    children.add(new RestoreNode(folderName, folderPrefix, true, 0L, null));
                }
            }
        }

        children.sort((a, b) -> {
            if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return children;
    }

    private Node createBootstrapFileIcon(boolean directory) {
        SVGPath icon = new SVGPath();
        if (directory) {
            icon.setContent("M.5 3a2 2 0 0 1 2-2h2.586a1 1 0 0 1 .707.293L7.414 3H13.5a2 2 0 0 1 2 2v1H.5zM.5 7h15v6a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2z");
            icon.setStyle("-fx-fill: #F0BC3E;");
        } else {
            icon.setContent("M9.293 0H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4.707A1 1 0 0 0 13.707 4L10 .293A1 1 0 0 0 9.293 0M9.5 3.5v-2l3 3h-2a1 1 0 0 1-1-1M4.5 9a.5.5 0 0 1 0-1h7a.5.5 0 0 1 0 1zM4 10.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m.5 2.5a.5.5 0 0 1 0-1h4a.5.5 0 0 1 0 1z");
            icon.setStyle("-fx-fill: #6B7280;");
        }
        icon.setScaleX(0.95);
        icon.setScaleY(0.95);
        StackPane wrapper = new StackPane(icon);
        wrapper.setPrefSize(16, 16);
        wrapper.setMinSize(16, 16);
        wrapper.setMaxSize(16, 16);
        return wrapper;
    }

    private Node createNavigationIcon(String name) {
        SVGPath icon = new SVGPath();
        icon.setContent(switch (name) {
            case "home" -> "M8 .5 15.5 7v8.5h-5v-5h-5v5h-5V7z";
            case "backup" -> "M8 1 15 4.5V11L8 15 1 11V4.5z M8 2.5v11 M2 5l6 3 6-3";
            case "restore" -> "M8 2a6 6 0 1 1-5.7 4H.5L3.4 2.8 6.2 6H3.8A4.5 4.5 0 1 0 8 3.5z";
            case "settings" -> "M6.8 1h2.4l.4 2a5 5 0 0 1 1.2.5l1.7-1.1 1.7 1.7-1.1 1.7q.4.6.5 1.2l2 .4v2.4l-2 .4q-.1.6-.5 1.2l1.1 1.7-1.7 1.7-1.7-1.1q-.6.4-1.2.5l-.4 2H6.8l-.4-2a5 5 0 0 1-1.2-.5l-1.7 1.1-1.7-1.7 1.1-1.7a5 5 0 0 1-.5-1.2l-2-.4V7.2l2-.4q.1-.6.5-1.2L1.8 3.9l1.7-1.7 1.7 1.1q.6-.4 1.2-.5z M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5";
            case "activity" -> "M1 8h3l2-4 3.2 7L11 8h4";
            default -> "M8 1a4 4 0 1 1 0 8 4 4 0 0 1 0-8 M2 15a6 6 0 0 1 12 0";
        });
        icon.getStyleClass().add("nav-icon");
        StackPane wrapper = new StackPane(icon);
        wrapper.setPrefSize(17, 17);
        wrapper.setMinSize(17, 17);
        wrapper.setMaxSize(17, 17);
        return wrapper;
    }

    private Node createKeeplySparkIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M8 0l1.8 4.2L14 6l-4.2 1.8L8 12 6.2 7.8 2 6l4.2-1.8z");
        icon.setStyle("-fx-fill: #5D4FFF;");
        StackPane wrapper = new StackPane(icon);
        wrapper.setPrefSize(18, 18);
        wrapper.setMinSize(18, 18);
        wrapper.setMaxSize(18, 18);
        return wrapper;
    }

    private Node createBootstrapPcIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M0 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-4.5a.5.5 0 0 0-.5.5V15h1a.5.5 0 0 1 0 1H6a.5.5 0 0 1 0-1h1v-1.5a.5.5 0 0 0-.5-.5H2a2 2 0 0 1-2-2zm1.5 0a.5.5 0 0 0-.5.5V10h14V4.5a.5.5 0 0 0-.5-.5z");
        icon.setStyle("-fx-fill: #4f49c9;");
        StackPane wrapper = new StackPane(icon);
        wrapper.setPrefSize(16, 16);
        wrapper.setMinSize(16, 16);
        wrapper.setMaxSize(16, 16);
        return wrapper;
    }

    private RestoreNode buildRestoreTree(String sourcePath, List<com.keeply.agent.model.FileManifest> files) {
        String rootLabel = sourcePath == null || sourcePath.isBlank() ? "Backup" : Path.of(sourcePath).getFileName().toString();
        if (rootLabel == null || rootLabel.isBlank()) rootLabel = sourcePath;
        if (rootLabel == null || rootLabel.isBlank()) rootLabel = "Backup";
        RestoreNode root = new RestoreNode(rootLabel, "", true, 0L, null);
        if (files == null) return root;
        for (com.keeply.agent.model.FileManifest file : files) {
            if (file == null || file.path() == null || file.path().isBlank()) continue;
            String[] parts = file.path().split("/");
            RestoreNode current = root;
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isBlank()) continue;
                if (pathBuilder.length() > 0) pathBuilder.append("/");
                pathBuilder.append(part);
                boolean isLeaf = i == parts.length - 1;
                current = getOrCreateNode(current, part, pathBuilder.toString(), !isLeaf, file.size(), file.lastModified());
            }
        }
        return root;
    }

    private RestoreNode buildRestoreTreeItems(String sourcePath, List<com.keeply.agent.api.BackendClient.SnapshotFileItem> files) {
        List<com.keeply.agent.model.FileManifest> mapped = files.stream()
                .map(file -> new com.keeply.agent.model.FileManifest(file.path(), file.size(), file.lastModified(), "", List.of()))
                .toList();
        return buildRestoreTree(sourcePath, mapped);
    }

    private RestoreNode getOrCreateNode(RestoreNode parent, String label, String fullPath, boolean isDirectory, long size, java.time.Instant lastModified) {
        for (RestoreNode child : parent.children) {
            if (child.label.equals(label) && child.isDirectory == isDirectory) return child;
        }
        RestoreNode created = new RestoreNode(label, fullPath, isDirectory, isDirectory ? 0L : size, lastModified);
        parent.children.add(created);
        return created;
    }

    private static class RestoreNode {
        private final String label;
        private final String fullPath;
        private final boolean isDirectory;
        private final long size;
        private final java.time.Instant lastModified;
        private final List<RestoreNode> children = new ArrayList<>();

        private RestoreNode(String label, String fullPath, boolean isDirectory, long size, java.time.Instant lastModified) {
            this.label = label;
            this.fullPath = fullPath;
            this.isDirectory = isDirectory;
            this.size = size;
            this.lastModified = lastModified;
        }

        @Override
        public String toString() {
            return label;
        }

        private String displayPath() {
            return fullPath == null || fullPath.isBlank() ? label : fullPath.replace("/", " > ");
        }
    }

    private static class LazyRestoreTreeItem extends CheckBoxTreeItem<RestoreNode> {
        private boolean loaded;
        private boolean loading;

        private LazyRestoreTreeItem(RestoreNode value) {
            super(value);
            setIndependent(true);
        }

        private void addPlaceholder() {
            if (getChildren().isEmpty()) {
                getChildren().add(new TreeItem<>(new RestoreNode("...", "", false, 0L, null)));
            }
        }
    }

    private Set<String> resolveSelectedFiles(SnapshotSummary snapshot, SelectedRestorePaths selections) {
        if (snapshot == null || selections == null) return Set.of();
        if (selections.directories().isEmpty()) {
            return selections.files();
        }
        List<BackendClient.SnapshotFileItem> allFiles = backend.listAllSnapshotFiles(snapshot.id(), null);
        Set<String> resolved = new HashSet<>(selections.files());
        for (BackendClient.SnapshotFileItem file : allFiles) {
            String path = file.path();
            if (path == null || path.isBlank()) continue;
            for (String directory : selections.directories()) {
                String prefix = directory == null ? "" : directory;
                if (prefix.isBlank() || path.startsWith(prefix)) {
                    resolved.add(path);
                    break;
                }
            }
        }
        return resolved;
    }

    private record SelectedRestorePaths(Set<String> files, Set<String> directories) {
    }

    private enum RestoreDestinationMode {
        ORIGINAL,
        CUSTOM
    }

    private Path parseDestinationPath(String destinationText) {
        if (destinationText == null || destinationText.isBlank()) {
            log("Selecione uma pasta de destino.");
            return null;
        }
        try {
            return Path.of(destinationText);
        } catch (Exception e) {
            log("Destino inválido: " + destinationText);
            return null;
        }
    }

    private Pane configView(Stage stage) {
        BorderPane wrapper = new BorderPane();
        wrapper.getStyleClass().add("screen-root");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("config-scroll");

        VBox content = new VBox(0);
        content.setPadding(new Insets(24));
        content.setFillWidth(true);

        VBox panel = new VBox(0);
        panel.getStyleClass().add("settings-panel");

        // ── Header ──────────────────────────────────────────────────────────
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getStyleClass().add("settings-header");

        SVGPath shieldSvg = new SVGPath();
        shieldSvg.setContent("M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z");
        shieldSvg.setStyle("-fx-fill: #6D47FF;");
        StackPane shieldBox = new StackPane(shieldSvg);
        shieldBox.setPrefSize(20, 20); shieldBox.setMinSize(20, 20); shieldBox.setMaxSize(20, 20);

        Label panelTitle = new Label("Plano de Backup");
        panelTitle.getStyleClass().add("settings-panel-title");

        Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("btn-outline-primary");
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);

        Button saveAllBtn = new Button("Salvar");
        saveAllBtn.getStyleClass().add("btn-primary");
        saveAllBtn.setVisible(false);
        saveAllBtn.setManaged(false);

        ToggleButton planToggle = makeToggleSwitch();
        planToggle.setSelected(true);

        headerRow.getChildren().addAll(shieldBox, panelTitle, hSpacer, cancelBtn, saveAllBtn, planToggle);
        panel.getChildren().addAll(headerRow, makeDivider());

        // Dirty-state: shows Salvar/Cancelar only when user changes something
        boolean[] suppressing = {true}; // true during initial load / revert
        // Pending sources: null = no unsaved changes, non-null = user has edited locally
        java.util.concurrent.atomic.AtomicReference<List<String>> pendingSources = new java.util.concurrent.atomic.AtomicReference<>(null);

        Runnable markDirty = () -> {
            if (suppressing[0]) return;
            cancelBtn.setVisible(true);  cancelBtn.setManaged(true);
            saveAllBtn.setVisible(true); saveAllBtn.setManaged(true);
        };
        Runnable hideSaveBtns = () -> {
            cancelBtn.setVisible(false);  cancelBtn.setManaged(false);
            saveAllBtn.setVisible(false); saveAllBtn.setManaged(false);
        };

        // ── O que fazer backup ───────────────────────────────────────────────
        // Summary row
        HBox foldersSummaryRow = new HBox(10);
        foldersSummaryRow.setAlignment(Pos.CENTER_LEFT);
        foldersSummaryRow.getStyleClass().add("settings-row");
        Label foldersLbl = new Label("O que fazer backup");
        foldersLbl.getStyleClass().add("settings-row-label");
        Region fsp = new Region(); HBox.setHgrow(fsp, Priority.ALWAYS);
        Label foldersCountLbl = new Label("—");
        foldersCountLbl.getStyleClass().add("settings-row-value");
        Button addFolderBtn = new Button("+ Adicionar");
        addFolderBtn.getStyleClass().add("btn-outline-primary");
        foldersSummaryRow.getChildren().addAll(foldersLbl, fsp, foldersCountLbl, addFolderBtn);

        // Folder list (always visible below the summary row, compact)
        VBox sourcesContainer = new VBox(4);
        sourcesContainer.setPadding(new Insets(0, 20, 12, 20));
        Label emptyHint = new Label("Nenhuma pasta configurada.");
        emptyHint.getStyleClass().add("card-subtitle");
        sourcesContainer.getChildren().add(emptyHint);

        // Helper: re-render sources with pending-state remove buttons
        java.util.function.Consumer<List<String>> renderPendingSources = (sources) -> {
            sourcesContainer.getChildren().clear();
            if (sources == null || sources.isEmpty()) {
                sourcesContainer.getChildren().add(emptyHint);
                return;
            }
            for (String path : sources) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("source-item-row");
                SVGPath folderIcon = new SVGPath();
                folderIcon.setContent("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
                folderIcon.setStyle("-fx-fill: #F59E0B;");
                StackPane iconWrap = new StackPane(folderIcon);
                iconWrap.setPrefSize(20, 20); iconWrap.setMinSize(20, 20); iconWrap.setMaxSize(20, 20);
                Label pathLabel = new Label(path);
                pathLabel.getStyleClass().add("source-path-label");
                HBox.setHgrow(pathLabel, Priority.ALWAYS);
                pathLabel.setMaxWidth(Double.MAX_VALUE);
                Button removeBtn = new Button("×");
                removeBtn.getStyleClass().add("source-remove-btn");
                removeBtn.setOnAction(ev -> {
                    List<String> current = new ArrayList<>(pendingSources.get() != null
                            ? pendingSources.get() : parseSources(backupSourcesConfig.getText()));
                    current.remove(path);
                    pendingSources.set(current);
                    backupSourcesConfig.setText(String.join("\n", current));
                    // Re-render is called via the Consumer itself (captured via lambda ref trick below)
                    foldersCountLbl.setText(current.isEmpty() ? "—" : current.size() + " pasta(s)");
                    markDirty.run();
                    // trigger re-render by re-running this consumer
                    sourcesContainer.fireEvent(new javafx.event.ActionEvent());
                });
                row.getChildren().addAll(iconWrap, pathLabel, removeBtn);
                sourcesContainer.getChildren().add(row);
            }
        };
        sourcesContainer.addEventHandler(javafx.event.ActionEvent.ACTION, ev -> {
            List<String> cur = pendingSources.get() != null
                    ? pendingSources.get() : parseSources(backupSourcesConfig.getText());
            renderPendingSources.accept(cur);
        });
        addFolderBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Selecionar pasta para proteger");
            var dir = chooser.showDialog(stage);
            if (dir == null) return;
            String newPath = dir.toPath().toString();
            List<String> current = new ArrayList<>(pendingSources.get() != null
                    ? pendingSources.get() : parseSources(backupSourcesConfig.getText()));
            if (!current.contains(newPath)) {
                current.add(newPath);
                pendingSources.set(current);
                backupSourcesConfig.setText(String.join("\n", current));
                renderPendingSources.accept(current);
                foldersCountLbl.setText(current.size() + " pasta(s)");
                markDirty.run();
            }
        });
        panel.getChildren().addAll(foldersSummaryRow, sourcesContainer, makeDivider());
        // ── Proteção contínua (CDP) ──────────────────────────────────────────
        HBox cdpRow = new HBox(10);
        cdpRow.setAlignment(Pos.CENTER_LEFT);
        cdpRow.getStyleClass().add("settings-row");
        Label cdpLbl = new Label("Proteção contínua (CDP)");
        cdpLbl.getStyleClass().add("settings-row-label");
        Region cdpSp = new Region(); HBox.setHgrow(cdpSp, Priority.ALWAYS);
        ToggleButton cdpToggle = makeToggleSwitch();
        cdpToggle.setOnAction(e -> markDirty.run());
        cdpRow.getChildren().addAll(cdpLbl, cdpSp, cdpToggle);
        panel.getChildren().addAll(cdpRow, makeDivider());

        // ── Agendamento ──────────────────────────────────────────────────────
        // Summary row with edit button
        HBox scheduleSummaryRow = new HBox(10);
        scheduleSummaryRow.setAlignment(Pos.CENTER_LEFT);
        scheduleSummaryRow.getStyleClass().add("settings-row");
        Label scheduleLbl = new Label("Agendamento");
        scheduleLbl.getStyleClass().add("settings-row-label");
        Region ssp = new Region(); HBox.setHgrow(ssp, Priority.ALWAYS);
        Label scheduleSummaryLbl = new Label("Todos os dias às 02:00");
        scheduleSummaryLbl.getStyleClass().add("settings-row-value");
        SVGPath pencilSvg = new SVGPath();
        pencilSvg.setContent("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z");
        pencilSvg.setStyle("-fx-fill: #6D47FF;");
        pencilSvg.setScaleX(0.72); pencilSvg.setScaleY(0.72);
        Button scheduleEditBtn = new Button();
        scheduleEditBtn.setGraphic(new StackPane(pencilSvg));
        scheduleEditBtn.getStyleClass().add("btn-icon-inline");
        scheduleSummaryRow.getChildren().addAll(scheduleLbl, ssp, scheduleSummaryLbl, scheduleEditBtn);

        // Expandable edit area (hidden by default)
        VBox scheduleEditArea = new VBox(10);
        scheduleEditArea.setPadding(new Insets(0, 20, 14, 20));
        scheduleEditArea.setVisible(false);
        scheduleEditArea.setManaged(false);

        Label schedInfo = new Label("A tarefa agendada será executada na hora local da máquina.");
        schedInfo.getStyleClass().add("card-subtitle");

        HBox startRow = new HBox(10);
        startRow.setAlignment(Pos.CENTER_LEFT);
        Label startLbl = new Label("Hora:");
        startLbl.getStyleClass().add("settings-row-label");
        ComboBox<String> startTime = new ComboBox<>();
        for (int h = 0; h < 24; h++) {
            startTime.getItems().add(String.format(Locale.ROOT, "%02d:00", h));
        }
        startTime.setValue("02:00");
        startTime.getStyleClass().add("config-field");
        startTime.setPrefWidth(130);
        startTime.valueProperty().addListener((obs, o, n) -> markDirty.run());
        startRow.getChildren().addAll(startLbl, startTime);

        Label scheduleStatus = new Label();
        scheduleStatus.getStyleClass().add("card-subtitle");
        scheduleStatus.setWrapText(true);

        Button saveScheduleBtn = new Button("Salvar agendamento");
        saveScheduleBtn.getStyleClass().add("btn-primary");

        HBox saveRow = new HBox(10);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        saveRow.getChildren().addAll(saveScheduleBtn);
        scheduleEditArea.getChildren().addAll(schedInfo, startRow, saveRow, scheduleStatus);

        Runnable updateScheduleSummary = () -> {
            scheduleSummaryLbl.setText(buildScheduleSummaryDaily(startTime));
        };

        saveScheduleBtn.setOnAction(e -> runAsync(() -> {
            saveScheduleDaily(startTime, scheduleStatus);
            ui(() -> { updateScheduleSummary.run(); hideSaveBtns.run(); });
        }));

        scheduleEditBtn.setOnAction(e -> {
            boolean expanded = scheduleEditArea.isManaged();
            scheduleEditArea.setVisible(!expanded);
            scheduleEditArea.setManaged(!expanded);
        });

        panel.getChildren().addAll(scheduleSummaryRow, scheduleEditArea, makeDivider());

        // ── Quanto tempo manter ──────────────────────────────────────────────
        HBox retentionRow = new HBox(10);
        retentionRow.setAlignment(Pos.CENTER_LEFT);
        retentionRow.getStyleClass().add("settings-row");
        Label retentionLbl = new Label("Quanto tempo manter");
        retentionLbl.getStyleClass().add("settings-row-label");
        Region rsp = new Region(); HBox.setHgrow(rsp, Priority.ALWAYS);
        Label retentionVal = new Label("Manter todos os snapshots");
        retentionVal.getStyleClass().add("settings-row-value");
        retentionRow.getChildren().addAll(retentionLbl, rsp, retentionVal);
        panel.getChildren().addAll(retentionRow, makeDivider());

        // ── Criptografia ─────────────────────────────────────────────────────
        HBox encRow = new HBox(10);
        encRow.setAlignment(Pos.CENTER_LEFT);
        encRow.getStyleClass().add("settings-row");
        VBox encLabels = new VBox(2);
        Label encLbl = new Label("Criptografia");
        encLbl.getStyleClass().add("settings-row-label");
        Label encSub = new Label("AES-256 · SHA-256");
        encSub.getStyleClass().add("card-subtitle");
        encLabels.getChildren().addAll(encLbl, encSub);
        Region esp = new Region(); HBox.setHgrow(esp, Priority.ALWAYS);
        ToggleButton encToggle = makeToggleSwitch();

        // Expandable password section (shown when encryption is ON)
        VBox encPasswordBox = new VBox(8);
        encPasswordBox.setPadding(new Insets(0, 20, 14, 20));
        encPasswordBox.setVisible(false);
        encPasswordBox.setManaged(false);
        Label encPassLbl = new Label("Senha de criptografia");
        encPassLbl.getStyleClass().add("settings-row-label");
        PasswordField encPassField = new PasswordField();
        encPassField.setPromptText("Senha para AES-256");
        encPassField.getStyleClass().add("config-field");
        PasswordField encPassConfirm = new PasswordField();
        encPassConfirm.setPromptText("Confirmar senha");
        encPassConfirm.getStyleClass().add("config-field");
        Label encPassStatus = new Label();
        encPassStatus.getStyleClass().add("card-subtitle");
        Button encPassSave = new Button("Definir senha");
        encPassSave.getStyleClass().add("btn-primary");
        encPassSave.setOnAction(ev -> {
            String p = encPassField.getText();
            String c = encPassConfirm.getText();
            if (p.isBlank()) { encPassStatus.setText("A senha não pode ser vazia."); return; }
            if (p.length() < 8) { encPassStatus.setText("Use pelo menos 8 caracteres."); return; }
            if (!p.equals(c)) { encPassStatus.setText("As senhas não coincidem."); return; }
            runAsync(() -> {
                try {
                    configWriter.saveEncryptionPassword(p);
                    ui(() -> {
                        encPassField.clear(); encPassConfirm.clear();
                        encPassStatus.setText("Senha definida com sucesso.");
                        markDirty.run();
                    });
                } catch (Exception ex) {
                    ui(() -> encPassStatus.setText("Erro: " + ex.getMessage()));
                }
            });
        });
        // Pre-fill if password already exists
        try {
            var cfg = configReader.read();
            if (cfg.isPresent() && cfg.get().encryptionPassword() != null && !cfg.get().encryptionPassword().isBlank()) {
                encPassStatus.setText("Senha já configurada — redefina se necessário.");
            }
        } catch (Exception ignored) {}
        encPasswordBox.getChildren().addAll(encPassLbl, encPassField, encPassConfirm, encPassSave, encPassStatus);

        encToggle.setOnAction(e -> {
            boolean sel = encToggle.isSelected();
            if (sel) {
                encPasswordBox.setVisible(true);
                encPasswordBox.setManaged(true);
            } else {
                encPasswordBox.setVisible(false);
                encPasswordBox.setManaged(false);
                encPassField.clear(); encPassConfirm.clear(); encPassStatus.setText("");
                runAsync(() -> { try { configWriter.saveEncryptionEnabled(false); } catch (Exception ignored) {} });
            }
            markDirty.run();
        });
        encRow.getChildren().addAll(encLabels, esp, encToggle);
        panel.getChildren().addAll(encRow, encPasswordBox, makeDivider());

        // ── Informações do dispositivo ───────────────────────────────────────
        HBox devHeaderRow = new HBox(10);
        devHeaderRow.setAlignment(Pos.CENTER_LEFT);
        devHeaderRow.getStyleClass().add("settings-row");
        Label devLbl = new Label("Informações do dispositivo");
        devLbl.getStyleClass().add("settings-row-label");
        devHeaderRow.getChildren().add(devLbl);

        VBox devBody = new VBox(6);
        devBody.setPadding(new Insets(0, 20, 18, 20));
        HBox devIdRow = new HBox(16);
        devIdRow.setAlignment(Pos.CENTER_LEFT);
        Label devIdKey = new Label("ID do dispositivo");
        devIdKey.getStyleClass().add("device-info-key");
        Label devIdVal = new Label(deviceId == null ? "Registrando..." : deviceId.toString());
        devIdVal.getStyleClass().add("device-info-value");
        devIdRow.getChildren().addAll(devIdKey, devIdVal);

        HBox srvRow = new HBox(16);
        srvRow.setAlignment(Pos.CENTER_LEFT);
        Label srvKey = new Label("Servidor");
        srvKey.getStyleClass().add("device-info-key");
        Label srvVal = new Label(backendUrl.getText() != null && !backendUrl.getText().isBlank()
                ? backendUrl.getText() : "—");
        srvVal.getStyleClass().add("device-info-value");
        srvRow.getChildren().addAll(srvKey, srvVal);
        devBody.getChildren().addAll(devIdRow, srvRow);
        panel.getChildren().addAll(devHeaderRow, devBody);

        content.getChildren().add(panel);
        scroll.setContent(content);
        wrapper.setCenter(scroll);

        // ── Save all wiring ──────────────────────────────────────────────────
        saveAllBtn.setOnAction(e -> runAsync(() -> {
            saveScheduleDaily(startTime, scheduleStatus);
            if (backend != null && deviceId != null) {
                // Compute what to save
                List<String> toSave = pendingSources.get();
                if (toSave == null) {
                    var optPlan = backend.getDevicePlan(deviceId);
                    toSave = optPlan.map(ProtectionPlan::sources).orElse(parseSources(backupSourcesConfig.getText()));
                }
                List<String> finalSources = toSave.isEmpty()
                        ? List.of(java.nio.file.Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString())
                        : toSave;
                ProtectionPlan.PlanType type = toSave.size() == 1 && toSave.equals(finalSources) && pendingSources.get() == null
                        ? ProtectionPlan.PlanType.DEFAULT : ProtectionPlan.PlanType.CUSTOM;
                // Read current schedule cron from YAML (just saved above)
                String cron = null;
                try {
                    var cfg = configReader.read();
                    if (cfg.isPresent()) cron = cfg.get().cron();
                } catch (Exception ignored) {}
                boolean cdp = cdpToggle.isSelected();
                boolean enc = encToggle.isSelected();
                backend.upsertDevicePlan(deviceId, type, finalSources, cdp, enc, cron);
                pendingSources.set(null);
            }
            ui(() -> { updateScheduleSummary.run(); hideSaveBtns.run(); });
        }));

        cancelBtn.setOnAction(e -> {
            suppressing[0] = true;
            hideSaveBtns.run();
            pendingSources.set(null);
            runAsync(() -> {
                loadScheduleDaily(startTime, scheduleStatus);
                Optional<AgentConfigReader.UiConfig> cfgOpt;
                try { cfgOpt = configReader.read(); } catch (Exception ex) { cfgOpt = Optional.empty(); }
                boolean encFinal = cfgOpt.map(AgentConfigReader.UiConfig::encryptionEnabled).orElse(false);
                // Reload sources from backend on cancel
                List<String> originalSources = (backend != null && deviceId != null)
                        ? backend.getDevicePlan(deviceId).map(ProtectionPlan::sources).orElse(parseSources(backupSourcesConfig.getText()))
                        : parseSources(backupSourcesConfig.getText());
                ui(() -> {
                    backupSourcesConfig.setText(String.join("\n", originalSources));
                    renderPendingSources.accept(originalSources);
                    foldersCountLbl.setText(originalSources.isEmpty() ? "—" : originalSources.size() + " pasta(s)");
                    encToggle.setSelected(encFinal);
                    updateScheduleSummary.run();
                    suppressing[0] = false;
                });
            });
        });

        // ── Refresh ──────────────────────────────────────────────────────────
        Runnable refresh = () -> {
            if (backend == null || deviceId == null) {
                List<String> local = parseSources(backupSourcesConfig.getText());
                ui(() -> {
                    renderPendingSources.accept(local);
                    foldersCountLbl.setText(local.isEmpty() ? "—" : local.size() + " pasta(s)");
                });
                return;
            }
            runAsync(() -> {
                var optPlan = backend.getDevicePlan(deviceId);
                List<String> sources = optPlan.isPresent()
                        ? optPlan.get().sources() : parseSources(backupSourcesConfig.getText());
                ui(() -> {
                    pendingSources.set(null); // fresh load clears pending
                    backupSourcesConfig.setText(String.join("\n", sources));
                    renderPendingSources.accept(sources);
                    foldersCountLbl.setText(sources.isEmpty() ? "—" : sources.size() + " pasta(s)");
                    devIdVal.setText(deviceId != null ? deviceId.toString() : "—");
                    srvVal.setText(backendUrl.getText() != null && !backendUrl.getText().isBlank()
                            ? backendUrl.getText() : "—");
                });
            });
        };
        this.configRefresh = refresh;

        // Combined initial load: schedule + encryption, then release suppression
        runAsync(() -> {
            loadScheduleDaily(startTime, scheduleStatus);
            Optional<AgentConfigReader.UiConfig> cfgOpt;
            try { cfgOpt = configReader.read(); } catch (Exception ex) { cfgOpt = Optional.empty(); }
            boolean encFinal = cfgOpt.map(AgentConfigReader.UiConfig::encryptionEnabled).orElse(false);
            boolean hasEncPass = cfgOpt.map(c -> c.encryptionPassword() != null && !c.encryptionPassword().isBlank()).orElse(false);
            ui(() -> {
                encToggle.setSelected(encFinal);
                if (encFinal) {
                    encPasswordBox.setVisible(true);
                    encPasswordBox.setManaged(true);
                    if (hasEncPass) encPassStatus.setText("Senha já configurada — redefina se necessário.");
                }
                updateScheduleSummary.run();
                suppressing[0] = false;
            });
        });
        refresh.run();
        return wrapper;
    }

    private String buildScheduleSummaryDaily(ComboBox<String> startTime) {
        String time = startTime.getValue() == null || startTime.getValue().isBlank() ? "--:--" : startTime.getValue();
        return "Todos os dias às " + time;
    }

    private ToggleButton makeToggleSwitch() {
        ToggleButton btn = new ToggleButton();

        Region thumb = new Region();
        thumb.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 999px;" +
            "-fx-pref-width: 18px; -fx-pref-height: 18px;" +
            "-fx-min-width: 18px; -fx-min-height: 18px;" +
            "-fx-max-width: 18px; -fx-max-height: 18px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.20), 3, 0, 0, 1);"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox track = new HBox();
        track.setAlignment(Pos.CENTER_LEFT);
        track.setPadding(new Insets(3));
        track.setPrefSize(44, 24);
        track.setMinSize(44, 24);
        track.setMaxSize(44, 24);
        track.setStyle("-fx-background-color: #CBD5E1; -fx-background-radius: 999px;");
        track.getChildren().add(thumb);

        btn.setGraphic(track);
        btn.getStyleClass().add("toggle-switch-btn");

        btn.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                track.getChildren().setAll(spacer, thumb);
                track.setStyle("-fx-background-color: #6D47FF; -fx-background-radius: 999px;");
            } else {
                track.getChildren().setAll(thumb);
                track.setStyle("-fx-background-color: #CBD5E1; -fx-background-radius: 999px;");
            }
        });

        return btn;
    }

    private Region makeDivider() {
        Region div = new Region();
        div.getStyleClass().add("settings-divider");
        div.setMaxWidth(Double.MAX_VALUE);
        return div;
    }


    private void saveScheduleDaily(ComboBox<String> startTime, Label statusLabel) throws Exception {
        String selectedTime = startTime.getValue();
        if (selectedTime == null || selectedTime.isBlank()) {
            throw new IllegalStateException("Selecione um horário.");
        }

        LocalTime parsedTime;
        try {
            parsedTime = LocalTime.parse(selectedTime.trim(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Hora inválida. Use HH:mm, ex: 02:00");
        }

        String cron = "%d %d * * *".formatted(parsedTime.getMinute(), parsedTime.getHour());
        String backendValue = backendUrl.getText() != null ? backendUrl.getText().trim() : "";
        String emailValue = email.getText() != null ? email.getText().trim() : "";
        List<String> sources = parseSources(backupSourcesConfig.getText());
        configWriter.saveSchedule(backendValue, emailValue, sources, cron);
        DaemonLauncher.ensureRunning(this::log, true);
        ui(() -> statusLabel.setText("Agendamento salvo e daemon reiniciado em " + configWriter.path() + " | cron=" + cron));
        log("Agendamento salvo: " + cron);
    }

    private void synchronizePlanAfterLogin() {
        Optional<ProtectionPlan> maybePlan = backend.getDevicePlan(deviceId);
        ProtectionPlan plan = maybePlan.orElseGet(this::createPlanFromWizard);

        try {
            configWriter.savePlan(backendUrl.getText().trim(), email.getText().trim(), plan);
            ui(() -> backupSourcesConfig.setText(String.join("\n", plan.sources())));
            log("Plano sincronizado e agent.yaml configurado com sucesso.");
        } catch (Exception e) {
            log("Erro ao salvar configuração após login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ProtectionPlan createPlanFromWizard() {
        String home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString();

        // Mostra wizard na thread de UI e aguarda escolha
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> chosenType = new java.util.concurrent.atomic.AtomicReference<>("DEFAULT");
        java.util.concurrent.atomic.AtomicReference<List<String>> chosenSources = new java.util.concurrent.atomic.AtomicReference<>(List.of(home));

        Platform.runLater(() -> {
            try {
                javafx.stage.Stage wizard = new javafx.stage.Stage();
                wizard.initOwner(primaryStage);
                wizard.initModality(javafx.stage.Modality.WINDOW_MODAL);
                wizard.initStyle(StageStyle.UNDECORATED);
                wizard.setResizable(false);

                // ── Card ──────────────────────────────────────────────────────
                VBox card = new VBox(0);
                card.setStyle(
                    "-fx-background-color: #ffffff;" +
                    "-fx-background-radius: 14px;" +
                    "-fx-border-color: #E2E8F0;" +
                    "-fx-border-radius: 14px;" +
                    "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.16), 28, 0.1, 0, 6);"
                );
                card.setPrefWidth(420);

                // Header
                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                header.setPadding(new Insets(20, 22, 16, 22));
                header.setStyle("-fx-border-color: #F1F5F9; -fx-border-width: 0 0 1 0;");
                SVGPath shieldIco = new SVGPath();
                shieldIco.setContent("M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z");
                shieldIco.setStyle("-fx-fill: #6D47FF;");
                StackPane icoBox = new StackPane(shieldIco);
                icoBox.setPrefSize(22, 22); icoBox.setMinSize(22, 22); icoBox.setMaxSize(22, 22);
                Label hTitle = new Label("Configure sua proteção");
                hTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");
                header.getChildren().addAll(icoBox, hTitle);

                // Body
                VBox body = new VBox(12);
                body.setPadding(new Insets(20, 22, 8, 22));
                Label subtitle = new Label("Este é o seu primeiro acesso. Escolha como quer fazer backup dos seus arquivos:");
                subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");
                subtitle.setWrapText(true);
                subtitle.setMaxWidth(380);

                // Option cards
                ToggleGroup tg = new ToggleGroup();

                VBox optDefault = buildPlanOptionCard(
                    "Plano Padrão",
                    "Protege automaticamente sua pasta pessoal (" + home + ")",
                    "#6D47FF", tg, "DEFAULT"
                );

                VBox optCustom = buildPlanOptionCard(
                    "Personalizado",
                    "Escolha quais pastas deseja proteger",
                    "#0EA5E9", tg, "CUSTOM"
                );

                // Seleciona padrão por default
                tg.getToggles().get(0).setSelected(true);

                // Seletor de pastas (visível só no modo custom)
                VBox folderPicker = new VBox(8);
                folderPicker.setPadding(new Insets(4, 0, 0, 0));
                folderPicker.setVisible(false);
                folderPicker.setManaged(false);
                Label folderPickerTitle = new Label("Pastas selecionadas:");
                folderPickerTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #374151;");
                VBox folderList = new VBox(4);
                List<String> customSources = new ArrayList<>();
                Button addFolderBtnW = new Button("+ Adicionar pasta");
                addFolderBtnW.getStyleClass().add("btn-outline-primary");
                addFolderBtnW.setOnAction(ae -> {
                    javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                    dc.setTitle("Selecionar pasta para proteger");
                    java.io.File selected = dc.showDialog(wizard);
                    if (selected != null) {
                        String p = selected.toPath().toString();
                        if (!customSources.contains(p)) {
                            customSources.add(p);
                            Label lbl = new Label("• " + p);
                            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
                            folderList.getChildren().add(lbl);
                        }
                    }
                });
                folderPicker.getChildren().addAll(folderPickerTitle, folderList, addFolderBtnW);

                tg.selectedToggleProperty().addListener((obs, old, nw) -> {
                    if (nw == null) return;
                    boolean custom = "CUSTOM".equals(nw.getUserData());
                    folderPicker.setVisible(custom);
                    folderPicker.setManaged(custom);
                });

                body.getChildren().addAll(subtitle, optDefault, optCustom, folderPicker);

                // Footer
                HBox footer = new HBox(10);
                footer.setAlignment(Pos.CENTER_RIGHT);
                footer.setPadding(new Insets(16, 22, 20, 22));
                footer.setStyle("-fx-border-color: #F1F5F9; -fx-border-width: 1 0 0 0;");
                Button btnConfirm = new Button("Confirmar");
                btnConfirm.getStyleClass().add("btn-primary");
                btnConfirm.setPrefWidth(130);
                btnConfirm.setOnAction(ae -> {
                    Toggle sel = tg.getSelectedToggle();
                    if (sel != null && "CUSTOM".equals(sel.getUserData())) {
                        if (customSources.isEmpty()) {
                            customSources.add(home);
                        }
                        chosenType.set("CUSTOM");
                        chosenSources.set(List.copyOf(customSources));
                    } else {
                        chosenType.set("DEFAULT");
                        chosenSources.set(List.of(home));
                    }
                    wizard.close();
                });
                footer.getChildren().add(btnConfirm);

                card.getChildren().addAll(header, body, footer);
                StackPane root = new StackPane(card);
                root.setPadding(new Insets(10));
                root.setStyle("-fx-background-color: transparent;");

                javafx.scene.Scene wizScene = new javafx.scene.Scene(root);
                wizScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
                wizScene.getStylesheets().add(getClass().getResource("/keeply-theme.css").toExternalForm());
                wizard.setScene(wizScene);
                wizard.showAndWait();
            } finally {
                latch.countDown();
            }
        });

        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        ProtectionPlan.PlanType planType = "CUSTOM".equals(chosenType.get())
                ? ProtectionPlan.PlanType.CUSTOM : ProtectionPlan.PlanType.DEFAULT;
        ProtectionPlan plan = backend.upsertDevicePlan(deviceId, planType, chosenSources.get());
        log("event=plan.created type=" + chosenType.get() + " sources=" + chosenSources.get());
        return plan;
    }

    private VBox buildPlanOptionCard(String title, String description, String accentColor, ToggleGroup tg, String userData) {
        RadioButton radio = new RadioButton();
        radio.setToggleGroup(tg);
        radio.setUserData(userData);
        radio.setStyle("-fx-cursor: hand;");

        VBox textBox = new VBox(3);
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");
        Label descLbl = new Label(description);
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        descLbl.setWrapText(true);
        descLbl.setMaxWidth(310);
        textBox.getChildren().addAll(titleLbl, descLbl);

        HBox row = new HBox(14, radio, textBox);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setStyle(
            "-fx-background-color: #F8FAFC;" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: #E2E8F0;" +
            "-fx-border-radius: 10px;" +
            "-fx-cursor: hand;"
        );
        card.setOnMouseClicked(e -> radio.setSelected(true));
        radio.selectedProperty().addListener((obs, old, sel) -> card.setStyle(
            "-fx-background-color: " + (sel ? "#F3EFFF" : "#F8FAFC") + ";" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: " + (sel ? accentColor : "#E2E8F0") + ";" +
            "-fx-border-radius: 10px;" +
            "-fx-cursor: hand;"
        ));
        return card;
    }


    private void loadScheduleDaily(ComboBox<String> startTime, Label statusLabel) throws Exception {
        Optional<AgentConfigReader.UiConfig> loaded = configReader.read();
        if (loaded.isEmpty()) {
            ui(() -> {
                startTime.setValue("02:00");
                statusLabel.setText("Primeiro uso: clique em Salvar agendamento para criar " + configReader.path());
            });
            return;
        }
        AgentConfigReader.UiConfig config = loaded.get();
        ui(() -> {
            if (config.backendUrl() != null) backendUrl.setText(config.backendUrl());
            if (config.email() != null) email.setText(config.email());
            backupSourcesConfig.setText(String.join("\n", config.sources()));
        });
        String cron = config.cron();
        if (cron == null || cron.isBlank()) {
            ui(() -> {
                startTime.setValue("02:00");
                statusLabel.setText("Agendamento diário padrão carregado. Clique em Salvar agendamento para atualizar " + configReader.path());
            });
            return;
        }

        String[] parts = cron.trim().split("\\s+");
        if (parts.length != 5) {
            ui(() -> statusLabel.setText("Cron inválido no YAML: " + cron));
            return;
        }

        String minutePart = parts[0];
        String hourPart = parts[1];
        ui(() -> {
            try {
                int minute = Integer.parseInt(minutePart);
                int hour = Integer.parseInt(hourPart);
                startTime.setValue("%02d:%02d".formatted(hour, minute));
            } catch (NumberFormatException e) {
                startTime.setValue("02:00");
            }

            statusLabel.setText("Agendamento carregado: " + cron);
        });
    }

    private List<String> parseSources(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> sources = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                sources.add(trimmed);
            }
        }
        return sources;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(16));
        grid.setHgap(8);
        grid.setVgap(8);
        return grid;
    }

    private VBox box() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        return box;
    }


    private void runAsync(ThrowingRunnable task) {
        Thread.startVirtualThread(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                String userMessage = getErrorMessage(ex);
                if (isInvalidCredentialsError(ex)) {
                    log("ERRO: Credenciais inválidas");
                } else if (isRateLimitError(ex)) {
                    log("ERRO: Limite temporário de tentativas atingido");
                } else if (isBusinessError(ex)) {
                    log("ERRO: " + userMessage);
                } else {
                    ex.printStackTrace();
                    log("ERRO: " + formatException(ex));
                }
                ui(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erro na Operação");
                    alert.setHeaderText(null);
                    if (isInvalidCredentialsError(ex)) {
                        alert.setContentText("Credenciais inválidas");
                    } else {
                        alert.setContentText(userMessage);
                    }
                    alert.showAndWait();
                });
            }
        });
    }

    private boolean isInvalidCredentialsError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && (msg.toLowerCase().contains("credenciais inválidas") || msg.toLowerCase().contains("credenciais invalidas"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRateLimitError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase().contains("muitas tentativas")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isBusinessError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof IllegalStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String getErrorMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && (cause instanceof IllegalStateException || cause.getClass().getSimpleName().contains("RuntimeException"))) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private String formatException(Throwable throwable) {
        StringBuilder out = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (depth == 0) {
                out.append(current.getClass().getSimpleName())
                        .append(": ")
                        .append(current.getMessage());
            } else {
                out.append(" | causa ")
                        .append(depth)
                        .append(": ")
                        .append(current.getClass().getSimpleName())
                        .append(": ")
                        .append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        out.append("\n").append(sw);
        return out.toString();
    }

    private void log(String message) {
        appendLogs(message + "\n");
    }

    private void appendLogs(String text) {
        synchronized (logBuffer) {
            logBuffer.append(text);
        }
        scheduleLogFlush();
    }

    private void scheduleLogFlush() {
        if (!logFlushScheduled.compareAndSet(false, true)) return;
        ui(() -> {
            try {
                String chunk;
                synchronized (logBuffer) {
                    if (logBuffer.isEmpty()) return;
                    chunk = logBuffer.toString();
                    logBuffer.setLength(0);
                }
                logs.appendText(chunk);
                int excess = logs.getLength() - VISIBLE_LOG_LIMIT_CHARS;
                if (excess > 0) {
                    logs.deleteText(0, excess);
                }
                ingestActivityChunk(chunk);
            } finally {
                logFlushScheduled.set(false);
                synchronized (logBuffer) {
                    if (!logBuffer.isEmpty()) {
                        scheduleLogFlush();
                    }
                }
            }
        });
    }

    private void ui(Runnable r) {
        Platform.runLater(r);
    }

    private void applyAppIcon(Stage stage) {
        Image icon16 = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/keeply.png")), 16, 16, true, true);
        Image icon32 = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/keeply.png")), 32, 32, true, true);
        Image icon64 = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/keeply.png")), 64, 64, true, true);
        Image icon128 = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/keeply.png")), 128, 128, true, true);
        Image icon256 = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/keeply.png")), 256, 256, true, true);
        stage.getIcons().clear();
        stage.getIcons().addAll(icon16, icon32, icon64, icon128, icon256);
        try {
            // On Linux, force a stable app name that desktop shells can map to icon cache/launcher.
            System.setProperty("jdk.gtk.application.name", "Keeply");
        } catch (Exception ignored) {
            // Keep startup resilient if property isn't accepted by the runtime.
        }
    }

    private void ingestActivityChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) return;
        String[] lines = chunk.split("\\R");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;
            allActivityEvents.add(0, ActivityEvent.fromRaw(line));
        }
        while (allActivityEvents.size() > MAX_ACTIVITY_EVENTS) {
            allActivityEvents.remove(allActivityEvents.size() - 1);
        }
    }

    private void redirectSystemErrToTextArea() {
        OutputStream out = new TextAreaOutputStream(logs);
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final TextArea textArea;
        private final StringBuilder buffer = new StringBuilder();
        private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

        public TextAreaOutputStream(TextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public synchronized void write(int b) {
            buffer.append((char) b);
            scheduleFlush();
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            buffer.append(new String(b, off, len, StandardCharsets.UTF_8));
            scheduleFlush();
        }

        @Override
        public synchronized void flush() {
            scheduleFlush();
        }

        private synchronized String drainBuffer() {
            if (buffer.isEmpty()) return "";
            String out = buffer.toString();
            buffer.setLength(0);
            return out;
        }

        private void scheduleFlush() {
            if (!flushScheduled.compareAndSet(false, true)) return;
            Platform.runLater(() -> {
                try {
                    String chunk = drainBuffer();
                    if (!chunk.isEmpty()) {
                        textArea.appendText(chunk);
                        int excess = textArea.getLength() - VISIBLE_LOG_LIMIT_CHARS;
                        if (excess > 0) {
                            textArea.deleteText(0, excess);
                        }
                    }
                } finally {
                    flushScheduled.set(false);
                    synchronized (TextAreaOutputStream.this) {
                        if (buffer.length() > 0) {
                            scheduleFlush();
                        }
                    }
                }
            });
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private enum BackupProgressState {
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record ActivityEvent(String dateLabel, String headline, String detail,
                                 String category, String categoryLabel, String state) {
        static ActivityEvent fromRaw(String line) {
            String timestamp = "--:--:--";
            String message = line;
            String category = "ui";
            String categoryLabel = "UI";
            String state = "success";
            LocalDate date = LocalDate.now();

            if (line.length() >= 19 && Character.isDigit(line.charAt(0)) && line.charAt(4) == '-') {
                try {
                    LocalDateTime dt = LocalDateTime.parse(line.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    date = dt.toLocalDate();
                    timestamp = dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    message = line.substring(Math.min(line.length(), 20)).trim();
                } catch (Exception ignored) {
                    // keep fallback values
                }
            }

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("erro") || lower.contains("[error]") || lower.contains("exception") || lower.contains("failed")) {
                category = "error";
                categoryLabel = "ERRO";
                state = "error";
            } else if (lower.contains("[daemon]") || lower.contains("event=daemon")) {
                category = "daemon";
                categoryLabel = "DAEMON";
                state = lower.contains("status=started") ? "running" : "success";
            } else if (lower.contains("backup") || lower.contains("snapshot") || lower.contains("chunk_")) {
                category = "backup";
                categoryLabel = "BACKUP";
                state = lower.contains("status=started") ? "running" : "success";
            }

            String status;
            if (lower.contains("status=started") || lower.contains("iniciando")) {
                status = "em andamento";
                state = "running";
            } else if (lower.contains("status=completed") || lower.contains("status=finished") || lower.contains("conclu")) {
                status = "completado";
            } else if (lower.contains("status=already_running")) {
                status = "já em execução";
                state = "running";
            } else if (state.equals("error")) {
                status = "falhou";
            } else {
                status = "completado";
            }

            String cleaned = message.replace("[daemon]", "")
                    .replace("[INFO]", "")
                    .replace("[ERROR]", "")
                    .trim();
            if (cleaned.isBlank()) cleaned = line;

            String dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMM, yyyy", new Locale("pt", "BR")));
            String headline = timestamp + " • " + status;
            return new ActivityEvent(dateLabel, headline, cleaned, category, categoryLabel, state);
        }
    }
}
