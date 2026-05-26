package com.keeply.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.core.RestoreEngine;
import com.keeply.agent.core.RestoreEngine.OverwritePolicy;
import com.keeply.agent.daemon.AgentPaths;
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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeeplyAgentApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(KeeplyAgentApp.class);
    private BackendClient backend;
    private LocalDatabase db;
    private UUID deviceId;
    private DeviceAuthStore deviceAuthStore;
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
    private final Map<String, Node> appViews = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private Runnable restoreRefresh = () -> {};
    private final Path daemonLogPath = AgentPaths.resolveLogPath();
    private final AtomicLong daemonLogOffset = new AtomicLong(0L);

    @Override
    public void start(Stage stage) {
        redirectSystemErrToTextArea();
        log.info("Iniciando Keeply Agent UI...");
        
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
        appViews.put("Backup", backupView(stage));
        appViews.put("Restore", restoreView(stage));
        appViews.put("Configurações", configView());
        logs.getStyleClass().add("log-surface");
        appViews.put("Logs", logs);
        appShell = buildAppShell();

        // Tenta auto-login
        Optional<DeviceSession> saved = deviceAuthStore.load();
        boolean authenticated = saved.isPresent();
        if (saved.isPresent()) {
            DeviceSession session = saved.get();
            backend = new BackendClient(backendUrl.getText().trim());
            backend.setSession(session);
            deviceId = session.deviceId();
            status.setText("Conectado. Device: " + deviceId);
            if (mainShellController != null && session.email() != null) {
                mainShellController.setProfile(session.email());
            }
        }
        updateAuthenticationNavigation(authenticated);
        showView(authenticated ? "Dashboard" : "Login");

        Scene scene = new Scene(appShell, 1240, 760);
        scene.getStylesheets().add(getClass().getResource("/keeply-theme.css").toExternalForm());
        stage.setTitle("Keeply");
        stage.setScene(scene);
        stage.show();

        Thread.startVirtualThread(() -> DaemonProcessManager.ensureDaemonRunning(this::log));
        Thread.startVirtualThread(this::streamDaemonLogs);
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
                    backend = new BackendClient(backendUrl.getText().trim());
                    String installationId = DeviceIdentity.getOrCreate();
                    DeviceSession session = backend.loginDevice(userEmail, userPass, installationId, hostname, System.getProperty("os.name"), "0.1.0");
                    deviceId = session.deviceId();
                    deviceAuthStore.save(session);
                    synchronizePlanAfterLogin();
                    DaemonProcessManager.ensureDaemonRunning(this::log);

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

    private BorderPane buildAppShell() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
            BorderPane shell = loader.load();
            this.mainShellController = loader.getController();
            this.mainShellController.setNavigationHandler(this::showView);
            this.contentHost = this.mainShellController.getContentHost();
            if (this.contentHost == null) {
                 log("Erro: contentHost não encontrado no MainShell.fxml");
                 this.contentHost = new StackPane();
                 shell.setCenter(this.contentHost);
            }
            
            navButtons.put("Login", new Button("Login"));
            navButtons.put("Dashboard", (Button) shell.lookup("#btnInicio"));
            navButtons.put("Backup", (Button) shell.lookup("#btnBackups"));
            navButtons.put("Restore", (Button) shell.lookup("#btnRestaurar"));
            navButtons.put("Configurações", (Button) shell.lookup("#btnConfiguracoes"));
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
            com.keeply.agent.ui.DashboardController controller = loader.getController();
            
            // Simula um refresh de dados (opcional, pode ser movido para Timeline)
            Thread.startVirtualThread(() -> {
                if (backend != null && deviceId != null) {
                    try {
                        var snapshots = backend.listSnapshots();
                        long totalFiles = snapshots.stream().mapToLong(com.keeply.agent.model.SnapshotSummary::totalFiles).sum();
                        String latest = snapshots.isEmpty() ? "Sem snapshots" : snapshots.getFirst().status();
                        String lastDate = snapshots.isEmpty() ? "Nunca" : "Hoje, 09:42"; // Mock
                        
                        var optPlan = backend.getDevicePlan(deviceId);
                        List<String> currentSources = optPlan.isPresent() ? optPlan.get().sources() : parseSources(backupSourcesConfig.getText());

                        javafx.application.Platform.runLater(() -> {
                            controller.updateStats(lastDate, String.valueOf(snapshots.size()), "256");
                            controller.setFolders(currentSources);
                        });
                    } catch (Exception e) {}
                }
            });

            return root;
        } catch (Exception e) {
            e.printStackTrace();
            return new VBox(new Label("Erro ao carregar Dashboard.fxml"));
        }
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
                UUID snapshotId = new BackupEngine(backend, db).backup(deviceId, source);
                log("event=ui.backup status=completed snapshot_id=" + snapshotId);
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

        Label title = new Label("Explorar snapshot");
        title.getStyleClass().add("restore-title");

        Button restoreAll = new Button("Restaurar Tudo");
        restoreAll.getStyleClass().add("btn-secondary");
        restoreAll.setDisable(true);

        Button restoreSelected = new Button("Restaurar Selecionados");
        restoreSelected.getStyleClass().add("btn-success");
        restoreSelected.setDisable(true);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topBar.getChildren().addAll(title, topSpacer, restoreAll, restoreSelected);

        Label currentPathLabel = new Label("Backup");
        currentPathLabel.getStyleClass().add("item-muted");
        HBox addressBar = new HBox(8, currentPathLabel);
        addressBar.getStyleClass().add("restore-address");
        Region addressSpacer = new Region();
        HBox.setHgrow(addressSpacer, Priority.ALWAYS);
        addressBar.getChildren().add(addressSpacer);

        VBox topArea = new VBox(8, topBar, addressBar);
        layout.setTop(topArea);

        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("restore-split");

        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("restore-sidebar");
        Label sidebarTitle = new Label("PONTOS DE RESTAURACAO");
        sidebarTitle.getStyleClass().add("section-title");
        ListView<SnapshotSummary> snapshotList = new ListView<>();
        snapshotList.getStyleClass().add("explorer-list");
        VBox.setVgrow(snapshotList, Priority.ALWAYS);
        snapshotList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label deviceIcon = new Label();
                    deviceIcon.setGraphic(createBootstrapPcIcon());
                    VBox cellBox = new VBox(3);
                    cellBox.getStyleClass().add("snapshot-item");
                    Label idLabel = new Label(item.id().toString().substring(0, 8));
                    idLabel.getStyleClass().add("snapshot-item-title");
                    Label detailsLabel = new Label(item.sourcePath() + "\n" + item.totalFiles() + " arquivos");
                    detailsLabel.getStyleClass().add("item-muted");
                    Label stateLabel = new Label(item.status());
                    stateLabel.getStyleClass().add("snapshot-badge");
                    Button quickRestore = new Button("Restaurar");
                    quickRestore.getStyleClass().add("btn-success");
                    quickRestore.setOnAction(e -> {
                        snapshotList.getSelectionModel().select(item);
                        restoreAll.fire();
                    });
                    Button quickDelete = new Button("Excluir");
                    quickDelete.getStyleClass().add("btn-secondary");
                    quickDelete.setOnAction(e -> log("Exclusão de snapshot ainda não disponível no backend."));
                    HBox actions = new HBox(6, quickRestore, quickDelete);
                    cellBox.getChildren().addAll(idLabel, detailsLabel, stateLabel, actions);
                    HBox row = new HBox(8, deviceIcon, cellBox);
                    row.setAlignment(Pos.TOP_LEFT);
                    setGraphic(row);
                }
            }
        });
        sidebar.getChildren().addAll(sidebarTitle, snapshotList);
        sidebar.setMinWidth(220);

        VBox explorer = new VBox(0);
        explorer.getStyleClass().add("explorer-surface");

        TreeView<RestoreNode> folderTree = new TreeView<>();
        folderTree.getStyleClass().add("explorer-tree");
        folderTree.setMinWidth(260);
        folderTree.setCellFactory(param -> new TreeCell<>() {
            @Override
            protected void updateItem(RestoreNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                CheckBox checkBox = new CheckBox();
                if (getTreeItem() instanceof CheckBoxTreeItem<?> cbItem) {
                    @SuppressWarnings("unchecked")
                    CheckBoxTreeItem<RestoreNode> typedItem = (CheckBoxTreeItem<RestoreNode>) cbItem;
                    checkBox.selectedProperty().unbindBidirectional(typedItem.selectedProperty());
                    checkBox.selectedProperty().bindBidirectional(typedItem.selectedProperty());
                    checkBox.setAllowIndeterminate(false);
                }
                Label text = new Label(item.label);
                HBox row = new HBox(6, checkBox, createBootstrapFileIcon(item.isDirectory), text);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        RestoreNode[] currentFolderRef = new RestoreNode[1];
        VBox[] actionPanelRef = new VBox[1];
        VBox.setVgrow(folderTree, Priority.ALWAYS);
        TextField fileSearch = new TextField();
        fileSearch.setPromptText("Buscar arquivo...");
        Button searchFiles = new Button("Buscar");
        Button previousFiles = new Button("Anterior");
        Button nextFiles = new Button("Proxima");
        Label pageInfo = new Label();
        Set<String> selectedRestorePaths = new HashSet<>();
        previousFiles.setDisable(true);
        nextFiles.setDisable(true);

        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            VBox panel = actionPanelRef[0];
            if (newVal == null || newVal.getValue() == null) {
                return;
            }
            currentFolderRef[0] = newVal.getValue();
            currentPathLabel.setText("Backup > " + currentFolderRef[0].displayPath());
        });

        currentPathLabel.setOnMouseClicked(e -> {
            TreeItem<RestoreNode> rootTree = folderTree.getRoot();
            RestoreNode rootNode = rootTree == null ? null : rootTree.getValue();
            if (rootNode != null) {
                currentFolderRef[0] = rootNode;
                currentPathLabel.setText("Backup > " + rootNode.displayPath());
                folderTree.getSelectionModel().select(rootTree);
            }
        });

        HBox fileNavigation = new HBox(8, fileSearch, searchFiles, previousFiles, nextFiles, pageInfo);
        fileNavigation.setPadding(new Insets(8));
        explorer.getChildren().addAll(fileNavigation, folderTree);

        splitPane.getItems().addAll(sidebar, explorer);
        splitPane.setDividerPositions(0.28);
        layout.setCenter(splitPane);

        VBox actionPanel = new VBox(12);
        actionPanel.setPadding(new Insets(12));
        actionPanel.setPrefWidth(280);
        actionPanel.getStyleClass().add("restore-actions");
        actionPanel.setVisible(false);
        actionPanel.setManaged(false);
        actionPanelRef[0] = actionPanel;

        Label actionTitle = new Label("PAINEL DE RESTAURACAO");
        actionTitle.getStyleClass().add("section-title");
        Label summaryLabel = new Label("Selecione um snapshot para carregar os arquivos.");
        summaryLabel.getStyleClass().add("item-muted");
        summaryLabel.setWrapText(true);

        Label destLabel = new Label("Destino:");
        ToggleGroup destinationModeGroup = new ToggleGroup();
        RadioButton restoreOriginal = new RadioButton("Original");
        restoreOriginal.setToggleGroup(destinationModeGroup);
        RadioButton restoreCustom = new RadioButton("Personalizado");
        restoreCustom.setToggleGroup(destinationModeGroup);
        restoreCustom.setSelected(true);

        TextField destination = new TextField();
        destination.setPromptText("Pasta de destino...");
        Button chooseDest = new Button("Selecionar...");
        chooseDest.setMaxWidth(Double.MAX_VALUE);

        destination.disableProperty().bind(restoreOriginal.selectedProperty());
        chooseDest.disableProperty().bind(restoreOriginal.selectedProperty());

        ComboBox<OverwritePolicy> overwritePolicy = new ComboBox<>();
        overwritePolicy.getItems().setAll(OverwritePolicy.values());
        overwritePolicy.setValue(OverwritePolicy.ALWAYS);
        overwritePolicy.setMaxWidth(Double.MAX_VALUE);

        Label warningLabel = new Label();
        warningLabel.setWrapText(true);
        warningLabel.getStyleClass().add("warning-text");

        actionPanel.getChildren().addAll(
            actionTitle, 
            summaryLabel,
            new Separator(),
            destLabel, restoreOriginal, restoreCustom, destination, chooseDest,
            new Label("Sobrescrita:"), overwritePolicy,
            warningLabel
        );
        layout.setRight(actionPanel);

        // --- LOGIC ---
        int[] visiblePage = new int[]{0};
        java.util.function.BiConsumer<SnapshotSummary, Integer> loadSnapshotFiles = (snapshot, requestedPage) -> runAsync(() -> {
            try {
                var page = backend.listSnapshotFiles(snapshot.id(), Math.max(0, requestedPage), 200, fileSearch.getText());
                RestoreNode restoreRoot = buildRestoreTreeItems(snapshot.sourcePath(), page.items());
                ui(() -> {
                    visiblePage[0] = page.pagination().page();
                    CheckBoxTreeItem<RestoreNode> rootItem = buildFolderTreeItem(restoreRoot);
                    folderTree.setRoot(rootItem);
                    folderTree.setShowRoot(true);
                    if (restoreRoot != null) {
                        rootItem.setExpanded(true);
                        applyCheckedPaths(rootItem, selectedRestorePaths);
                        bindCheckboxListeners(rootItem, () -> {
                            updateSelectedPathsForVisibleTree(rootItem, selectedRestorePaths);
                            actionPanel.setVisible(!selectedRestorePaths.isEmpty());
                            actionPanel.setManaged(!selectedRestorePaths.isEmpty());
                        });
                        currentPathLabel.setText("Backup > " + restoreRoot.displayPath());
                    }
                    long first = page.items().isEmpty() ? 0 : ((long) visiblePage[0] * page.pagination().size()) + 1;
                    long last = ((long) visiblePage[0] * page.pagination().size()) + page.items().size();
                    pageInfo.setText(first + "-" + last + " / " + page.pagination().totalElements());
                    previousFiles.setDisable(visiblePage[0] == 0);
                    nextFiles.setDisable(last >= page.pagination().totalElements());
                    summaryLabel.setText("Snapshot: " + snapshot.id().toString().substring(0, 8)
                            + "\nStatus: " + snapshot.status()
                            + "\nExibindo " + page.items().size() + " arquivos");
                    actionPanel.setVisible(!selectedRestorePaths.isEmpty());
                    actionPanel.setManaged(!selectedRestorePaths.isEmpty());
                });
            } catch (Exception ex) {
                log("Erro ao carregar arquivos: " + ex.getMessage());
            }
        });
        searchFiles.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected != null) loadSnapshotFiles.accept(selected, 0);
        });
        fileSearch.setOnAction(e -> searchFiles.fire());
        previousFiles.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected != null) loadSnapshotFiles.accept(selected, visiblePage[0] - 1);
        });
        nextFiles.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected != null) loadSnapshotFiles.accept(selected, visiblePage[0] + 1);
        });

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
                selectedRestorePaths.clear();
                boolean isCompleted = "COMPLETED".equals(newVal.status());
                boolean isProcessing = "PROCESSING".equals(newVal.status());
                boolean canRestore = isCompleted;

                restoreAll.setDisable(!canRestore);
                restoreSelected.setDisable(!canRestore);
                warningLabel.setText(canRestore ? "" : "⚠️ Apenas backups COMPLETED podem ser restaurados.");
                summaryLabel.setText("Snapshot: " + newVal.id().toString().substring(0, 8) + "\nStatus: " + newVal.status());

                if (isCompleted || isProcessing) {
                    fileSearch.clear();
                    loadSnapshotFiles.accept(newVal, 0);
                } else {
                    ui(() -> {
                        folderTree.setRoot(null);
                        currentFolderRef[0] = null;
                        currentPathLabel.setText("Backup");
                        actionPanel.setVisible(false);
                        actionPanel.setManaged(false);
                    });
                }
            } else {
                restoreAll.setDisable(true);
                restoreSelected.setDisable(true);
                warningLabel.setText("");
                summaryLabel.setText("Selecione um snapshot para carregar os arquivos.");
                ui(() -> {
                    folderTree.setRoot(null);
                    currentFolderRef[0] = null;
                    currentPathLabel.setText("Backup");
                    actionPanel.setVisible(false);
                    actionPanel.setManaged(false);
                });
            }
        });

        chooseDest.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) destination.setText(dir.toPath().toString());
        });

        restoreAll.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Path destinationRoot = restoreOriginal.isSelected() ? null : parseDestinationPath(destination.getText());
            if (!restoreOriginal.isSelected() && destinationRoot == null) return;
            runAsync(() -> new RestoreEngine(backend).restore(selected.id(), destinationRoot, null, overwritePolicy.getValue()));
        });

        restoreSelected.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Path destinationRoot = restoreOriginal.isSelected() ? null : parseDestinationPath(destination.getText());
            if (!restoreOriginal.isSelected() && destinationRoot == null) return;
            Set<String> selectedFiles = new HashSet<>(selectedRestorePaths);
            if (selectedFiles.isEmpty()) {
                log("Selecione pelo menos um arquivo.");
                return;
            }
            runAsync(() -> new RestoreEngine(backend).restore(selected.id(), destinationRoot, selectedFiles, overwritePolicy.getValue()));
        });

        return layout;
    }

    private Set<String> collectCheckedFilePathsFromTree(TreeItem<RestoreNode> root) {
        Set<String> selected = new HashSet<>();
        collectCheckedRec(root, selected);
        return selected;
    }

    private void updateSelectedPathsForVisibleTree(TreeItem<RestoreNode> root, Set<String> selected) {
        Set<String> visible = new HashSet<>();
        collectVisibleFilePaths(root, visible);
        selected.removeAll(visible);
        selected.addAll(collectCheckedFilePathsFromTree(root));
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

    private void collectCheckedRec(TreeItem<RestoreNode> node, Set<String> out) {
        if (node == null || node.getValue() == null) return;
        if (node instanceof CheckBoxTreeItem<?> cbNode) {
            @SuppressWarnings("unchecked")
            CheckBoxTreeItem<RestoreNode> typedNode = (CheckBoxTreeItem<RestoreNode>) cbNode;
            RestoreNode value = typedNode.getValue();
            if (typedNode.isSelected() && value != null && !value.isDirectory && value.fullPath != null && !value.fullPath.isBlank()) {
                out.add(value.fullPath);
            }
        }
        for (TreeItem<RestoreNode> child : node.getChildren()) {
            collectCheckedRec(child, out);
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
            DaemonProcessManager.ensureDaemonRunning(this::log);
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

        Path configPath = AgentPaths.resolveDefaultConfigPath();
        Files.createDirectories(configPath.getParent());
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

        Map<String, Object> root;
        if (Files.exists(configPath)) {
            root = yaml.readValue(Files.readString(configPath), new TypeReference<LinkedHashMap<String, Object>>() {});
            if (root == null) root = new LinkedHashMap<>();
        } else {
            root = new LinkedHashMap<>();
        }

        String backendValue = backendUrl.getText() != null ? backendUrl.getText().trim() : "";
        String emailValue = email.getText() != null ? email.getText().trim() : "";
        String passwordValue = ""; // Senha removida por segurança
        List<String> sources = parseSources(backupSourcesConfig.getText());

        if (backendValue.isBlank()) {
            throw new IllegalStateException("Backend URL é obrigatório.");
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException("Informe pelo menos uma pasta em 'Pastas de backup'.");
        }

        Map<String, Object> backendSection = new LinkedHashMap<>();
        backendSection.put("url", backendValue);
        root.put("backend", backendSection);

        Map<String, Object> authSection = new LinkedHashMap<>();
        authSection.put("email", emailValue);
        authSection.put("password", passwordValue);
        root.put("auth", authSection);

        Map<String, Object> backupSection = new LinkedHashMap<>();
        backupSection.put("sources", sources);
        root.put("backup", backupSection);

        @SuppressWarnings("unchecked")
        Map<String, Object> schedule = root.get("schedule") instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        schedule.put("cron", cron);
        root.put("schedule", schedule);

        yaml.writeValue(configPath.toFile(), root);
        ui(() -> statusLabel.setText("Agendamento salvo em " + configPath + " | cron=" + cron));
        log("Agendamento salvo: " + cron);
    }

    private void synchronizePlanAfterLogin() {
        Optional<ProtectionPlan> maybePlan = backend.getDevicePlan(deviceId);
        ProtectionPlan plan = maybePlan.orElseGet(this::createPlanFromWizard);
        
        try {
            saveFullConfigAfterLogin(plan);
            ui(() -> backupSourcesConfig.setText(String.join("\n", plan.sources())));
            log("Plano sincronizado e agent.yaml configurado com sucesso.");
        } catch (Exception e) {
            log("Erro ao salvar configuração após login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveFullConfigAfterLogin(ProtectionPlan plan) throws Exception {
        Path configPath = AgentPaths.resolveDefaultConfigPath();
        Files.createDirectories(configPath.getParent());
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

        Map<String, Object> root;
        if (Files.exists(configPath)) {
            root = yaml.readValue(Files.readString(configPath), new TypeReference<LinkedHashMap<String, Object>>() {});
            if (root == null) root = new LinkedHashMap<>();
        } else {
            root = new LinkedHashMap<>();
        }

        // Backend
        Map<String, Object> backendSection = root.get("backend") instanceof Map<?, ?> existing 
                ? new LinkedHashMap<>((Map<String, Object>) existing) 
                : new LinkedHashMap<>();
        backendSection.put("url", backendUrl.getText().trim());
        root.put("backend", backendSection);

        // Auth
        Map<String, Object> authSection = root.get("auth") instanceof Map<?, ?> existingAuth 
                ? new LinkedHashMap<>((Map<String, Object>) existingAuth) 
                : new LinkedHashMap<>();
        authSection.put("email", email.getText().trim());
        authSection.put("password", ""); // Senha removida por segurança
        root.put("auth", authSection);

        // Backup
        Map<String, Object> backupSection = root.get("backup") instanceof Map<?, ?> existing 
                ? new LinkedHashMap<>((Map<String, Object>) existing) 
                : new LinkedHashMap<>();
        backupSection.put("sources", new ArrayList<>(plan.sources()));
        root.put("backup", backupSection);

        // Schedule (Garante padrão se não existir)
        if (!root.containsKey("schedule") || !(root.get("schedule") instanceof Map)) {
            Map<String, Object> schedule = new LinkedHashMap<>();
            schedule.put("cron", "0 2 * * *"); // 2 AM diário
            schedule.put("runOnStartup", true);
            root.put("schedule", schedule);
        }

        yaml.writeValue(configPath.toFile(), root);
    }

    private ProtectionPlan createPlanFromWizard() {
        ProtectionPlan.PlanType selectedType = selectPlanType();
        List<String> sources;
        if (selectedType == ProtectionPlan.PlanType.DEFAULT) {
            sources = List.of(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString());
        } else {
            List<String> custom = askCustomSources();
            if (custom.isEmpty()) {
                throw new IllegalStateException("Plano CUSTOM requer ao menos uma pasta.");
            }
            sources = custom;
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
            String selected = dialog.showAndWait()
                    .orElseThrow(() -> new IllegalStateException("Escolha do plano é obrigatória."));
            choiceRef.set(ProtectionPlan.PlanType.valueOf(selected));
            latch.countDown();
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

            String value = dialog.showAndWait()
                    .orElseThrow(() -> new IllegalStateException("Plano CUSTOM exige pastas."));
            valueRef.set(value);
            latch.countDown();
        });
        awaitLatch(latch);
        return parseSources(valueRef.get());
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
        Path configPath = AgentPaths.resolveDefaultConfigPath();
        if (!Files.exists(configPath)) {
            ui(() -> {
                dayChecks.values().forEach(cb -> cb.setSelected(true));
                startTime.setText("02:00");
                statusLabel.setText("Primeiro uso: clique em Salvar agendamento para criar " + configPath);
            });
            return;
        }

        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        Map<String, Object> root = yaml.readValue(Files.readString(configPath), new TypeReference<LinkedHashMap<String, Object>>() {});
        if (root == null) {
            ui(() -> statusLabel.setText("Config vazia em " + configPath));
            return;
        }

        if (root.get("backend") instanceof Map<?, ?> backendSection && backendSection.get("url") != null) {
            ui(() -> backendUrl.setText(backendSection.get("url").toString()));
        }
        if (root.get("auth") instanceof Map<?, ?> authSection) {
            Object loadedEmail = authSection.get("email");
            Object loadedPassword = authSection.get("password");
            ui(() -> {
                if (loadedEmail != null) email.setText(loadedEmail.toString());
                if (loadedPassword != null) password.setText(loadedPassword.toString());
            });
        }
        if (root.get("backup") instanceof Map<?, ?> backupSection && backupSection.get("sources") instanceof List<?> loadedSources) {
            String sourcesText = loadedSources.stream().map(String::valueOf).reduce((a, b) -> a + "\n" + b).orElse("");
            ui(() -> backupSourcesConfig.setText(sourcesText));
        }

        if (!(root.get("schedule") instanceof Map<?, ?> schedule)) {
            ui(() -> statusLabel.setText("Seção schedule não encontrada em " + configPath));
            return;
        }

        String cron = schedule.get("cron") != null ? schedule.get("cron").toString() : null;
        if (cron == null || cron.isBlank()) {
            ui(() -> statusLabel.setText("schedule.cron vazio em " + configPath));
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
        ui(() -> logs.appendText(message + "\n"));
    }

    private void streamDaemonLogs() {
        while (true) {
            try {
                if (Files.exists(daemonLogPath)) {
                    long fileSize = Files.size(daemonLogPath);
                    long offset = daemonLogOffset.get();
                    if (fileSize < offset) {
                        daemonLogOffset.set(0L);
                        offset = 0L;
                    }
                    if (fileSize > offset) {
                        byte[] full = Files.readAllBytes(daemonLogPath);
                        String chunk = new String(full, (int) offset, (int) (fileSize - offset), StandardCharsets.UTF_8);
                        daemonLogOffset.set(fileSize);
                        for (String line : chunk.split("\\R")) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                log("[daemon] " + trimmed);
                            }
                        }
                    }
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
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
