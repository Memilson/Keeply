package com.keeply.agent;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.config.AgentConfigReader;
import com.keeply.agent.config.AgentConfigWriter;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.BackupSnapshotException;
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
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import javafx.animation.KeyValue;
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
    private BackendClient backend;
    private LocalDatabase db;
    private UUID deviceId;
    private DeviceAuthStore deviceAuthStore;
    private AgentConfigReader configReader;
    private AgentConfigWriter configWriter;
    private final TextArea logs = new TextArea();

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

        
        backendUrl = new TextField("http://localhost:8080");
        email = new TextField();
        password = new PasswordField();
        status = new Label("Desconectado");
        backupSourcesConfig = new TextArea();
        backupSourcesConfig.setPrefRowCount(4);
        backupSourcesConfig.setPromptText("Ex: /home/usuario/Documentos\n/home/usuario/Imagens");

        logs.setEditable(false);

        appViews.put("Login", loginView());
        appViews.put("Dashboard", dashboardView());
        appViews.put("Backup", configView()); // Configurações virou Backup
        appViews.put("AddFolder", backupView(stage)); // Wizard de nova pasta
        appViews.put("Restore", restoreView(stage)); // Meus arquivos
        logs.getStyleClass().add("log-surface");
        appViews.put("Logs", logs);
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
        stage.setScene(scene);
        stage.show();

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

        Button loginBtn = new Button("ENTRAR NA CONTA");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.getStyleClass().addAll("btn-primary", "btn-wide");
        
        loginBtn.setOnAction(e -> {
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
                    synchronizePlanAfterLogin(userPass);
                    if (backend.hasPersistedSession()) {
                        DaemonLauncher.ensureRunning(this::log, true);
                    } else {
                        log("event=daemon.start status=skipped reason=session_not_persisted");
                    }

                    ui(() -> {
                        status.setText("Conectado. Device: " + deviceId);
                        if (mainShellController != null && session.email() != null) {
                            mainShellController.setProfile(session.email());
                        }
                        updateAuthenticationNavigation(true);
                        showView("Dashboard");
                    });
                    log("event=ui.login status=completed device_id=" + deviceId);
                } catch (Exception ex) {
                    log("Erro no login: " + ex.getMessage());
                }
            });
        });

        grid.addRow(0, new Label("Servidor:"), backendUrl);
        grid.addRow(1, new Label("E-mail:"), email);
        grid.addRow(2, new Label("Senha:"), password);
        grid.add(loginBtn, 1, 3);

        box.getChildren().addAll(title, new Label("Faça login para sincronizar seus backups com a nuvem."), grid);
        return box;
    }

    private BorderPane buildAppShell(Stage stage) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
            BorderPane shell = loader.load();
            this.mainShellController = loader.getController();
            this.mainShellController.setNavigationHandler(this::showView);
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
            navButtons.put("Logs", (Button) shell.lookup("#btnAtividade"));
            
            shellPageLabel = new Label();
            return shell;
        } catch (Exception e) {
            e.printStackTrace();
            return new BorderPane(new Label("Erro ao carregar MainShell.fxml"));
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
                runAsync(() -> {
                    try {
                        log("Iniciando backup manual pelo Dashboard...");
                        var optPlan = backend.getDevicePlan(deviceId);
                        List<String> sources = optPlan.isPresent() ? optPlan.get().sources() : parseSources(backupSourcesConfig.getText());
                        if (sources == null || sources.isEmpty()) {
                            log("Nenhuma pasta configurada para backup.");
                            return;
                        }
                        for (String sourcePath : sources) {
                            try {
                                Path source = Path.of(sourcePath);
                                UUID snapshotId = new BackupEngine(backend, db).backup(deviceId, source);
                                if (snapshotId != null) {
                                    log("event=ui.backup.manual status=completed source=" + sourcePath + " snapshot_id=" + snapshotId);
                                } else {
                                    log("event=ui.backup.manual status=skipped source=" + sourcePath + " reason=already_running");
                                }
                            } catch (Exception ex) {
                                log("event=ui.backup.manual status=failed source=" + sourcePath + " message=" + getErrorMessage(ex));
                            }
                        }
                        refreshDashboard();
                        log("Backup manual concluído.");
                        ui(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Backup concluído");
                            alert.setHeaderText(null);
                            alert.setContentText("O backup manual das pastas foi finalizado.");
                            alert.showAndWait();
                        });
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

    private Pane backupView(Stage stage) {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("screen-root");
        layout.setPadding(new Insets(20));

        VBox center = new VBox(20);
        center.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Proteger nova pasta");
        title.getStyleClass().add("page-title");
        
        VBox form = new VBox(10);
        Label label = new Label("Escolha o diretório que você deseja sincronizar com a nuvem Keeply:");
        label.setWrapText(true);

        HBox selector = new HBox(10);
        TextField folder = new TextField();
        folder.setPromptText("Caminho da pasta (ex: /home/documentos)");
        HBox.setHgrow(folder, Priority.ALWAYS);

        Button choose = new Button("Selecionar pasta", createBootstrapFileIcon(true));
        choose.getStyleClass().add("btn-secondary");
        choose.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) folder.setText(dir.toPath().toString());
        });
        selector.getChildren().addAll(folder, choose);

        VBox infoBox = new VBox(8);
        infoBox.getStyleClass().add("info-box");
        infoBox.getChildren().addAll(
            new Label("O que acontece agora?"),
            new Label("O Keeply analisara todos os arquivos da pasta."),
            new Label("Apenas mudancas serao enviadas com deduplicacao."),
            new Label("Seus dados serao comprimidos e protegidos.")
        );

        Button backup = new Button("Iniciar backup agora");
        backup.setMaxWidth(Double.MAX_VALUE);
        backup.getStyleClass().addAll("btn-success", "btn-wide");
        backup.setOnAction(e -> {
            if (!ready()) return;
            String pathStr = folder.getText();
            if (pathStr == null || pathStr.isBlank()) {
                log("Selecione uma pasta primeiro.");
                return;
            }
            Path source = Path.of(pathStr);
            runAsync(() -> {
                try {
                    UUID snapshotId = new BackupEngine(backend, db).backup(deviceId, source);
                    log("event=ui.backup status=completed snapshot_id=" + snapshotId);
                } catch (BackupSnapshotException backupError) {
                    try {
                        backend.failSnapshot(backupError.snapshotId(), backupError.userMessage());
                    } catch (Exception failSnapshotError) {
                        log("event=ui.backup status=fail_report_failed snapshot_id="
                                + backupError.snapshotId() + " message=" + failSnapshotError.getMessage());
                    }
                    db.setLastFailedSnapshot(deviceId, backupError.sourcePath(),
                            backupError.snapshotId().toString(), backupError.userMessage());
                    log("event=ui.backup status=failed snapshot_id=" + backupError.snapshotId()
                            + " message=" + backupError.userMessage());
                    throw backupError;
                }
            });
        });

        form.getChildren().addAll(label, selector, infoBox, backup);
        center.getChildren().addAll(title, new Separator(), form);
        
        layout.setCenter(center);
        return layout;
    }

    private Pane restoreView(Stage stage) {
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("screen-root");
        layout.getStyleClass().add("restore-shell");
        layout.setPadding(new Insets(12));

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("restore-toolbar");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topBar.getChildren().add(topSpacer);

        Button backToSnapshots = new Button("Voltar");
        backToSnapshots.getStyleClass().add("btn-secondary");
        backToSnapshots.setVisible(false);
        backToSnapshots.setManaged(false);
        Label currentPathLabel = new Label("Snapshots");
        currentPathLabel.getStyleClass().add("item-muted");
        HBox addressBar = new HBox(8, backToSnapshots, currentPathLabel);
        addressBar.getStyleClass().add("restore-address");
        Region addressSpacer = new Region();
        HBox.setHgrow(addressSpacer, Priority.ALWAYS);
        addressBar.getChildren().add(addressSpacer);

        VBox topArea = new VBox(8, topBar, addressBar);
        layout.setTop(topArea);

        VBox snapshotsPane = new VBox(10);
        snapshotsPane.getStyleClass().add("restore-sidebar");
        Label sidebarTitle = new Label("PONTOS DE RESTAURACAO");
        sidebarTitle.getStyleClass().add("section-title");
        ListView<SnapshotSummary> snapshotList = new ListView<>();
        snapshotList.getStyleClass().add("explorer-list");
        VBox.setVgrow(snapshotList, Priority.ALWAYS);
        Label browserTitle = new Label("ITENS");
        browserTitle.getStyleClass().add("section-title");
        Label browserStatus = new Label("Selecione um snapshot concluído e clique em Carregar itens.");
        browserStatus.getStyleClass().add("item-muted");
        Button actionRestore = new Button("Recuperar");
        actionRestore.getStyleClass().add("restore-nav-line");
        actionRestore.setGraphic(createNavigationIcon("restore"));
        actionRestore.setMaxWidth(Double.MAX_VALUE);
        actionRestore.setDisable(true);
        VBox rightActions = new VBox(10, actionRestore);
        rightActions.setMinWidth(220);
        TreeView<RestoreNode> fileTree = new TreeView<>();
        fileTree.getStyleClass().add("explorer-list");
        fileTree.setShowRoot(true);
        BorderPane itemsPane = new BorderPane();
        itemsPane.setCenter(fileTree);
        itemsPane.setRight(rightActions);
        VBox fileBrowser = new VBox(10, browserTitle, browserStatus, itemsPane);
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        fileBrowser.setVisible(false);
        fileBrowser.setManaged(false);
        SnapshotSummary[] selectedSnapshot = new SnapshotSummary[1];
        java.util.function.Consumer<SnapshotSummary> recoverSnapshot = snapshot -> {
            if (snapshot == null) return;
            runAsync(() -> new RestoreEngine(backend).restore(snapshot.id(), null, null, OverwritePolicy.ALWAYS));
        };
        java.util.function.Consumer<SnapshotSummary> loadItems = snapshot -> {
            if (snapshot == null) return;
            if (!"COMPLETED".equals(snapshot.status())) {
                throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status() + ")");
            }
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
                RestoreNode rootNode = new RestoreNode(
                        rootLabelForSnapshot(snapshot.sourcePath()),
                        "",
                        true,
                        0L,
                        null
                );
                LazyRestoreTreeItem rootItem = new LazyRestoreTreeItem(rootNode);
                ui(() -> {
                    fileTree.setRoot(rootItem);
                    rootItem.setExpanded(true);
                    actionRestore.setDisable(true);
                });
                loadFolderChildren(snapshot, rootItem, browserStatus, () -> {
                    SelectedRestorePaths selections = collectCheckedSelectionsFromTree(fileTree.getRoot());
                    actionRestore.setDisable(selections.files().isEmpty() && selections.directories().isEmpty());
                });
                log("event=ui.restore.items_loaded snapshot_id=" + snapshot.id() + " prefix=/");
            });
        };

        fileTree.setCellFactory(tv -> new TreeCell<>() {
            private final CheckBox check = new CheckBox();
            private final Label text = new Label();
            private final HBox row = new HBox(8);
            private CheckBoxTreeItem<RestoreNode> bound;

            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(check, createBootstrapFileIcon(false), text);
            }

            @Override
            protected void updateItem(RestoreNode item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) {
                    check.selectedProperty().unbindBidirectional(bound.selectedProperty());
                    bound = null;
                }
                if (empty || item == null || !(getTreeItem() instanceof CheckBoxTreeItem<?> cbItem)) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                @SuppressWarnings("unchecked")
                CheckBoxTreeItem<RestoreNode> typed = (CheckBoxTreeItem<RestoreNode>) cbItem;
                bound = typed;
                check.selectedProperty().bindBidirectional(typed.selectedProperty());
                check.setAllowIndeterminate(false);
                row.getChildren().set(1, createBootstrapFileIcon(item.isDirectory));
                text.setText(item.label);
                text.getStyleClass().setAll(item.isDirectory ? "snapshot-item-title" : "item-muted");
                setText(null);
                setGraphic(row);
            }
        });
        fileTree.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot instanceof CheckBoxTreeItem<?> cbRoot) {
                @SuppressWarnings("unchecked")
                CheckBoxTreeItem<RestoreNode> typedRoot = (CheckBoxTreeItem<RestoreNode>) cbRoot;
                bindCheckboxListeners(typedRoot, () -> {
                    SelectedRestorePaths selections = collectCheckedSelectionsFromTree(fileTree.getRoot());
                    actionRestore.setDisable(selections.files().isEmpty() && selections.directories().isEmpty());
                });
            }
        });

        snapshotList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label dateLabel = new Label("Data " + formatSnapshotDateCompact(item.startedAt()));
                    dateLabel.getStyleClass().add("item-muted");
                    dateLabel.setStyle("-fx-font-size: 11px;");
                    Label typeLabel = new Label(inferSnapshotType(snapshotList.getItems(), item));
                    typeLabel.getStyleClass().add("snapshot-item-title");
                    Label filesLabel = new Label(item.totalFiles() + " arquivos");
                    filesLabel.getStyleClass().add("item-muted");
                    Label stateLabel = new Label(formatSnapshotStatus(item.status()));
                    stateLabel.getStyleClass().add("snapshot-badge");

                    Button rowRecover = new Button("Recuperar snapshot");
                    rowRecover.getStyleClass().add("btn-secondary");
                    rowRecover.setOnAction(e -> {
                        snapshotList.getSelectionModel().select(item);
                        recoverSnapshot.accept(item);
                    });

                    Button rowLoadItems = new Button("Carregar itens");
                    rowLoadItems.getStyleClass().add("btn-success");
                    rowLoadItems.setDisable(!"COMPLETED".equals(item.status()));
                    rowLoadItems.setOnAction(e -> {
                        snapshotList.getSelectionModel().select(item);
                        loadItems.accept(item);
                    });
                    HBox actions = new HBox(8, rowRecover, rowLoadItems);
                    actions.setAlignment(Pos.CENTER_LEFT);
                    StackPane actionsWrap = new StackPane(actions);
                    actionsWrap.setPadding(new Insets(18, 0, 8, 24));
                    actionsWrap.setVisible(true);
                    actionsWrap.setManaged(true);
                    actionsWrap.setOpacity(1.0);
                    actionsWrap.setMaxHeight(78);
                    actionsWrap.setPrefHeight(78);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    HBox row = new HBox(10,
                            createBootstrapPcIcon(),
                            dateLabel,
                            typeLabel,
                            filesLabel,
                            spacer,
                            stateLabel);
                    row.setAlignment(Pos.CENTER_LEFT);
                    VBox cellContent = new VBox(6, row, actionsWrap);
                    setGraphic(cellContent);
                }
            }
        });
        snapshotsPane.getChildren().addAll(sidebarTitle, snapshotList);
        VBox centerHost = new VBox(10, snapshotsPane, fileBrowser);
        VBox.setVgrow(snapshotsPane, Priority.ALWAYS);
        VBox.setVgrow(fileBrowser, Priority.ALWAYS);
        layout.setCenter(centerHost);

        Runnable refreshSnapshots = () -> {
            if (backend == null) {
                return;
            }
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
                currentPathLabel.setText("Snapshots > " + newVal.id().toString().substring(0, 8)
                        + " [" + newVal.status() + "]");
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
                if (selectedPaths.isEmpty()) {
                    throw new IllegalStateException("Nenhum arquivo selecionado para recuperação");
                }
                new RestoreEngine(backend).restore(snapshot.id(), finalDestination, selectedPaths, OverwritePolicy.ALWAYS);
            });
        });

        backToSnapshots.setOnAction(e -> {
            fileBrowser.setVisible(false);
            fileBrowser.setManaged(false);
            snapshotsPane.setVisible(true);
            snapshotsPane.setManaged(true);
            backToSnapshots.setVisible(false);
            backToSnapshots.setManaged(false);
            currentPathLabel.setText("Snapshots");
            fileTree.setRoot(null);
            actionRestore.setDisable(true);
        });

        return layout;
    }

    private Optional<RestoreDestinationMode> showRestoreDestinationDialog(Stage owner) {
        Dialog<RestoreDestinationMode> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Recuperar itens");
        dialog.setHeaderText(null);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("restore-destination-dialog");

        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().setAll(cancelType, okType);

        Label title = new Label("Escolha o destino da recuperação");
        title.getStyleClass().add("restore-destination-title");

        Label destinationLabel = new Label("Destino:");
        destinationLabel.getStyleClass().add("restore-destination-label");

        ComboBox<String> destinationBox = new ComboBox<>();
        destinationBox.getItems().setAll("Caminho original", "Caminho personalizado");
        destinationBox.getSelectionModel().selectFirst();
        destinationBox.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.add(destinationLabel, 0, 0);
        form.add(destinationBox, 1, 0);
        GridPane.setHgrow(destinationBox, Priority.ALWAYS);

        VBox content = new VBox(14, title, form);
        content.setPadding(new Insets(6, 6, 4, 6));
        pane.setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn != okType) return null;
            return "Caminho personalizado".equals(destinationBox.getValue())
                    ? RestoreDestinationMode.CUSTOM
                    : RestoreDestinationMode.ORIGINAL;
        });
        return dialog.showAndWait();
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

    private Pane configView() {
        VBox box = box();
        box.getStyleClass().add("screen-root");
        box.setSpacing(12);

        Label scheduleTitle = new Label("Agendamento automático");
        scheduleTitle.getStyleClass().add("section-title");

        GridPane scheduleGrid = new GridPane();
        scheduleGrid.setHgap(8);
        scheduleGrid.setVgap(8);

        Map<Integer, CheckBox> dayChecks = new LinkedHashMap<>();
        dayChecks.put(1, new CheckBox("Seg"));
        dayChecks.put(2, new CheckBox("Ter"));
        dayChecks.put(3, new CheckBox("Qua"));
        dayChecks.put(4, new CheckBox("Qui"));
        dayChecks.put(5, new CheckBox("Sex"));
        dayChecks.put(6, new CheckBox("Sáb"));
        dayChecks.put(0, new CheckBox("Dom"));

        int col = 0;
        for (CheckBox day : dayChecks.values()) {
            scheduleGrid.add(day, col++, 0);
        }

        TextField startTime = new TextField("02:00");
        startTime.setPromptText("HH:mm");
        startTime.setMaxWidth(100);

        Label scheduleStatus = new Label();
        Button saveSchedule = new Button("Salvar agendamento");
        saveSchedule.setOnAction(e -> runAsync(() -> saveScheduleToYaml(dayChecks, startTime, scheduleStatus)));
        Button startDaemonLocal = new Button("Tentar start local do daemon");
        startDaemonLocal.setOnAction(e -> runAsync(() -> {
            DaemonLauncher.ensureRunning(this::log, true);
            log("Solicitação de start local do daemon enviada.");
        }));

        HBox actions = new HBox(8, saveSchedule, startDaemonLocal);

        box.getChildren().addAll(
                new Label("URL do backend:"),
                backendUrl,
                new Label("Device atual:"),
                new Label(deviceId == null ? "Ainda não registrado" : deviceId.toString()),
                new Label("Pastas de backup (uma por linha):"),
                backupSourcesConfig,
                new Separator(),
                scheduleTitle,
                new Label("Dias da semana:"),
                scheduleGrid,
                new Label("Hora de começo (HH:mm):"),
                startTime,
                actions,
                scheduleStatus
        );
        runAsync(() -> loadScheduleFromYaml(dayChecks, startTime, scheduleStatus));
        return box;
    }

    private void saveScheduleToYaml(Map<Integer, CheckBox> dayChecks, TextField startTime, Label statusLabel) throws Exception {
        List<Integer> selectedDays = new ArrayList<>();
        for (Map.Entry<Integer, CheckBox> entry : dayChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedDays.add(entry.getKey());
            }
        }
        if (selectedDays.isEmpty()) {
            throw new IllegalStateException("Selecione pelo menos um dia.");
        }

        LocalTime parsedTime;
        try {
            parsedTime = LocalTime.parse(startTime.getText().trim(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Hora inválida. Use HH:mm, ex: 02:00");
        }

        String dow = selectedDays.size() == 7
                ? "*"
                : selectedDays.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("*");
        String cron = "%d %d * * %s".formatted(parsedTime.getMinute(), parsedTime.getHour(), dow);
        String backendValue = backendUrl.getText() != null ? backendUrl.getText().trim() : "";
        String emailValue = email.getText() != null ? email.getText().trim() : "";
        String passwordValue = password.getText() != null ? password.getText() : "";
        List<String> sources = parseSources(backupSourcesConfig.getText());
        configWriter.saveSchedule(backendValue, emailValue, passwordValue, sources, cron);
        DaemonLauncher.ensureRunning(this::log, true);
        ui(() -> statusLabel.setText("Agendamento salvo e daemon reiniciado em " + configWriter.path() + " | cron=" + cron));
        log("Agendamento salvo: " + cron);
    }

    private void synchronizePlanAfterLogin(String userPass) {
        Optional<ProtectionPlan> maybePlan = backend.getDevicePlan(deviceId);
        ProtectionPlan plan = maybePlan.orElseGet(this::createPlanFromWizard);

        try {
            configWriter.savePlan(backendUrl.getText().trim(), email.getText().trim(), userPass, plan);
            ui(() -> backupSourcesConfig.setText(String.join("\n", plan.sources())));
            log("Plano sincronizado e agent.yaml configurado com sucesso.");        } catch (Exception e) {
            log("Erro ao salvar configuração após login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ProtectionPlan createPlanFromWizard() {
        ProtectionPlan.PlanType selectedType = selectPlanType();
        List<String> sources;
        if (selectedType == ProtectionPlan.PlanType.DEFAULT) {
            sources = List.of(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString());
        } else {
            sources = askCustomSources();
            if (sources.isEmpty()) {
                ui(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Plano Inválido");
                    alert.setHeaderText(null);
                    alert.setContentText("O plano CUSTOM requer ao menos uma pasta monitorada.");
                    alert.showAndWait();
                });
                return createPlanFromWizard(); // Repete o processo completo
            }
        }
        return backend.upsertDevicePlan(deviceId, selectedType, sources);
    }

    private ProtectionPlan.PlanType selectPlanType() {
        java.util.concurrent.atomic.AtomicReference<ProtectionPlan.PlanType> choiceRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        ui(() -> {
            ChoiceDialog<String> dialog = new ChoiceDialog<>("DEFAULT", "DEFAULT", "CUSTOM");
            dialog.setTitle("Plano de proteção obrigatório");
            dialog.setHeaderText("Escolha o plano para este dispositivo");
            dialog.setContentText("Tipo:");
            String selected = dialog.showAndWait().orElse(null);
            if (selected != null) {
                choiceRef.set(ProtectionPlan.PlanType.valueOf(selected));
                latch.countDown();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Ação Obrigatória");
                alert.setHeaderText(null);
                alert.setContentText("Você deve escolher um plano de proteção para continuar.");
                alert.showAndWait();
                ui(() -> {
                    choiceRef.set(selectPlanType());
                    latch.countDown();
                });
            }
        });
        awaitLatch(latch);
        return choiceRef.get();
    }

    private List<String> askCustomSources() {
        java.util.concurrent.atomic.AtomicReference<String> valueRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        ui(() -> {
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Plano CUSTOM");
            dialog.setHeaderText("Informe pastas (uma por linha)");
            ButtonType saveType = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

            TextArea area = new TextArea();
            area.setPromptText("/home/usuario/Documentos\n/home/usuario/Imagens");
            area.setPrefRowCount(6);
            dialog.getDialogPane().setContent(area);
            dialog.setResultConverter(button -> button == saveType ? area.getText() : null);

            String value = dialog.showAndWait().orElse(null);
            if (value != null) {
                valueRef.set(value);
                latch.countDown();
            } else {
                latch.countDown(); // Deixa createPlanFromWizard lidar com a lista vazia
            }
        });
        awaitLatch(latch);
        String val = valueRef.get();
        return val == null ? List.of() : parseSources(val);
    }

    private void awaitLatch(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operação interrompida", e);
        }
    }


    private void loadScheduleFromYaml(Map<Integer, CheckBox> dayChecks, TextField startTime, Label statusLabel) throws Exception {
        Optional<AgentConfigReader.UiConfig> loaded = configReader.read();
        if (loaded.isEmpty()) {
            ui(() -> {
                dayChecks.values().forEach(cb -> cb.setSelected(true));
                startTime.setText("02:00");
                statusLabel.setText("Primeiro uso: clique em Salvar agendamento para criar " + configReader.path());
            });
            return;
        }
        AgentConfigReader.UiConfig config = loaded.get();
        ui(() -> {
            if (config.backendUrl() != null) backendUrl.setText(config.backendUrl());
            if (config.email() != null) email.setText(config.email());
            if (config.password() != null) password.setText(config.password());
            backupSourcesConfig.setText(String.join("\n", config.sources()));
        });
        String cron = config.cron();
        if (cron == null || cron.isBlank()) {
            ui(() -> statusLabel.setText("schedule.cron vazio em " + configReader.path()));
            return;
        }

        String[] parts = cron.trim().split("\\s+");
        if (parts.length != 5) {
            ui(() -> statusLabel.setText("Cron inválido no YAML: " + cron));
            return;
        }

        String minutePart = parts[0];
        String hourPart = parts[1];
        String dow = parts[4];

        ui(() -> {
            try {
                int minute = Integer.parseInt(minutePart);
                int hour = Integer.parseInt(hourPart);
                startTime.setText("%02d:%02d".formatted(hour, minute));
            } catch (NumberFormatException e) {
                // Se não for numérico (ex: *, */2), apenas limpa ou mantém padrão, 
                // indicando que a UI não reflete cron complexo perfeitamente
                startTime.setText("02:00"); 
            }

            Set<Integer> selectedDays = new HashSet<>();
            if ("*".equals(dow)) {
                selectedDays.addAll(dayChecks.keySet());
            } else {
                for (String token : dow.split(",")) {
                    try {
                        selectedDays.add(Integer.parseInt(token.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            dayChecks.forEach((idx, cb) -> cb.setSelected(selectedDays.contains(idx)));
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

    private VBox metricCard(String title, Label valueLabel) {
        VBox card = new VBox(8);
        card.getStyleClass().add("metric-card");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        valueLabel.getStyleClass().add("metric-value");
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private VBox panelWithTitle(String title) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("panel");
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        panel.getChildren().add(label);
        return panel;
    }

    private boolean ready() {
        if (backend == null || deviceId == null) {
            log("Faça login primeiro.");
            return false;
        }
        return true;
    }

    private void runAsync(ThrowingRunnable task) {
        Thread.startVirtualThread(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                String userMessage = getErrorMessage(ex);
                if (isInvalidCredentialsError(ex)) {
                    log("ERRO: Credenciais inválidas");
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
            if (msg != null && msg.toLowerCase().contains("credenciais inválidas")) {
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
        ui(() -> {
            logs.appendText(text);
            int excess = logs.getLength() - VISIBLE_LOG_LIMIT_CHARS;
            if (excess > 0) {
                logs.deleteText(0, excess);
            }
        });
    }

    private void ui(Runnable r) {
        Platform.runLater(r);
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

    public static void main(String[] args) {
        launch(args);
    }
}
