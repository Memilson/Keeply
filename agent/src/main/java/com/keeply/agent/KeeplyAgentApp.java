package com.keeply.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.config.PlanConfigSync;
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
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

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

public class KeeplyAgentApp extends Application {
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
    private final PlanConfigSync planConfigSync = new PlanConfigSync();
    private final Path daemonLogPath = AgentPaths.resolveLogPath();
    private final AtomicLong daemonLogOffset = new AtomicLong(0L);

    @Override
    public void start(Stage stage) {
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

        Tab loginTab = new Tab("Login", loginView());
        Tab dashboardTab = new Tab("Dashboard", dashboardView());
        TabPane tabs = new TabPane(
                loginTab,
                dashboardTab,
                new Tab("Backup", backupView(stage)),
                new Tab("Restore", restoreView(stage)),
                new Tab("Configurações", configView()),
                new Tab("Logs", logs)
        );

        tabs.getTabs().forEach(t -> t.setClosable(false));

        // Tenta auto-login
        Optional<DeviceSession> saved = deviceAuthStore.load();
        if (saved.isPresent()) {
            DeviceSession session = saved.get();
            backend = new BackendClient(backendUrl.getText().trim());
            backend.setSession(session);
            deviceId = session.deviceId();
            status.setText("Conectado. Device: " + deviceId);
            tabs.getTabs().remove(loginTab);
            tabs.getSelectionModel().select(dashboardTab);
        }

        Scene scene = new Scene(tabs, 900, 600);
        stage.setTitle("Keeply Agent MVP");
        stage.setScene(scene);
        stage.show();

        Thread.startVirtualThread(() -> DaemonProcessManager.ensureDaemonRunning(this::log));
        Thread.startVirtualThread(this::streamDaemonLogs);
    }

    private Pane loginView() {
        GridPane grid = grid();

        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> {
            TabPane tabPane = (TabPane) grid.getScene().getRoot();
            runAsync(() -> {
                String userEmail = email.getText().trim();
                String userPass = password.getText();
                
                log("Tentando login para: " + userEmail);
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
                    tabPane.getTabs().removeIf(t -> t.getText().equals("Login"));
                });
                log("Login OK. Device registrado: " + deviceId);
            });
        });

        grid.addRow(0, new Label("Backend:"), backendUrl);
        grid.addRow(1, new Label("Email:"), email);
        grid.addRow(2, new Label("Senha:"), password);
        grid.add(loginBtn, 1, 3);

        return grid;
    }

    private Pane dashboardView() {
        VBox box = box();
        Button refresh = new Button("Atualizar status");
        refresh.setOnAction(e -> {
            if (backend == null) {
                log("Faça login primeiro.");
                return;
            }
            runAsync(() -> {
                List<SnapshotSummary> snapshots = backend.listSnapshots();
                log("Snapshots encontrados: " + snapshots.size());
            });
        });

        box.getChildren().addAll(new Label("Status do agente:"), status, refresh);
        return box;
    }

    private Pane backupView(Stage stage) {
        VBox box = box();
        TextField folder = new TextField();
        folder.setPromptText("Escolha uma pasta");

        Button choose = new Button("Selecionar pasta");
        choose.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) folder.setText(dir.toPath().toString());
        });

        Button backup = new Button("Fazer backup agora");
        backup.setOnAction(e -> {
            if (!ready()) return;
            Path source = Path.of(folder.getText());
            runAsync(() -> {
                UUID snapshotId = new BackupEngine(backend, db).backup(deviceId, source, this::log);
                log("Snapshot final: " + snapshotId);
            });
        });

        box.getChildren().addAll(new Label("Pasta de origem:"), folder, choose, backup);
        return box;
    }

    private Pane restoreView(Stage stage) {
        VBox mainBox = box();

        ListView<SnapshotSummary> snapshotList = new ListView<>();
        snapshotList.setPrefHeight(150);
        snapshotList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("Snapshot: %s...\nOrigem: %s\nArquivos: %d | Status: %s",
                            item.id().toString().substring(0, 8), item.sourcePath(), item.totalFiles(), item.status()));
                }
            }
        });

        TreeView<RestoreNode> fileTree = new TreeView<>();
        fileTree.setShowRoot(false);
        fileTree.setPrefHeight(220);
        fileTree.setCellFactory(CheckBoxTreeCell.forTreeView());

        snapshotList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                runAsync(() -> {
                    try {
                        String json = backend.downloadManifest(newVal.id());
                        ObjectMapper localMapper = new ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .findAndRegisterModules();
                        com.keeply.agent.model.SnapshotManifest manifest = localMapper.readValue(json, com.keeply.agent.model.SnapshotManifest.class);
                        List<String> files = manifest.files().stream().map(com.keeply.agent.model.FileManifest::path).toList();
                        ui(() -> fileTree.setRoot(buildFileTree(files)));
                    } catch (Exception ex) {
                        log("Erro ao carregar arquivos: " + ex.getMessage());
                    }
                });
            }
        });

        TextField destination = new TextField();
        destination.setPromptText("Pasta de destino");

        ToggleGroup destinationModeGroup = new ToggleGroup();
        RadioButton restoreOriginal = new RadioButton("Restaurar no local original");
        restoreOriginal.setToggleGroup(destinationModeGroup);
        RadioButton restoreCustom = new RadioButton("Restaurar em pasta específica");
        restoreCustom.setToggleGroup(destinationModeGroup);
        restoreCustom.setSelected(true);

        Button choose = new Button("Selecionar destino");
        choose.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) destination.setText(dir.toPath().toString());
        });
        destination.disableProperty().bind(restoreOriginal.selectedProperty());
        choose.disableProperty().bind(restoreOriginal.selectedProperty());

        ComboBox<OverwritePolicy> overwritePolicy = new ComboBox<>();
        overwritePolicy.getItems().setAll(OverwritePolicy.values());
        overwritePolicy.setValue(OverwritePolicy.ALWAYS);

        Button refresh = new Button("🔄 Atualizar Lista de Backups");
        refresh.setOnAction(e -> {
            if (backend == null) {
                log("Faça login primeiro.");
                return;
            }
            runAsync(() -> {
                List<SnapshotSummary> snapshots = backend.listSnapshots();
                ui(() -> snapshotList.getItems().setAll(snapshots));
                log("Lista de backups atualizada.");
            });
        });

        Button restoreAll = new Button("📥 Restaurar Snapshot Completo");
        Button restoreSelected = new Button("🗂️ Restaurar Arquivos Selecionados");
        restoreAll.setDisable(true);
        restoreSelected.setDisable(true);
        Label warningLabel = new Label();
        warningLabel.setStyle("-fx-text-fill: red;");

        snapshotList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                boolean isCompleted = "COMPLETED".equals(newVal.status());
                restoreAll.setDisable(!isCompleted);
                restoreSelected.setDisable(!isCompleted);
                if (!isCompleted) {
                    warningLabel.setText("⚠️ Apenas backups COMPLETED podem ser restaurados.");
                } else {
                    warningLabel.setText("");
                }
            } else {
                restoreAll.setDisable(true);
                restoreSelected.setDisable(true);
                warningLabel.setText("");
            }
        });

        restoreAll.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                log("Selecione um backup.");
                return;
            }
            Path destinationRoot = restoreOriginal.isSelected() ? null : parseDestinationPath(destination.getText());
            if (!restoreOriginal.isSelected() && destinationRoot == null) return;
            runAsync(() -> new RestoreEngine(backend).restore(
                    selected.id(),
                    destinationRoot,
                    null,
                    overwritePolicy.getValue(),
                    this::log
            ));
        });

        restoreSelected.setOnAction(e -> {
            SnapshotSummary selected = snapshotList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                log("Selecione um backup.");
                return;
            }
            Path destinationRoot = restoreOriginal.isSelected() ? null : parseDestinationPath(destination.getText());
            if (!restoreOriginal.isSelected() && destinationRoot == null) return;
            Set<String> selectedFiles = collectCheckedFilePaths(fileTree.getRoot());
            if (selectedFiles.isEmpty()) {
                log("Selecione pelo menos um arquivo na árvore.");
                return;
            }
            runAsync(() -> new RestoreEngine(backend).restore(
                    selected.id(),
                    destinationRoot,
                    selectedFiles,
                    overwritePolicy.getValue(),
                    this::log
            ));
        });

        mainBox.getChildren().addAll(
                new Label("1. Selecione o Backup:"),
                refresh,
                snapshotList,
                new Label("Arquivos contidos neste backup:"),
                fileTree,
                new Label("2. Tipo de restauração:"),
                restoreAll,
                restoreSelected,
                new Label("3. Destino:"),
                restoreOriginal,
                restoreCustom,
                new Label("2. Escolha o destino da restauração:"),
                destination,
                choose,
                new Label("4. Política de sobrescrita:"),
                overwritePolicy,
                warningLabel,
                new Label("Dica: em restauração parcial, apenas arquivos marcados serão restaurados.")
        );
        return mainBox;
    }

    private CheckBoxTreeItem<RestoreNode> buildFileTree(List<String> filePaths) {
        CheckBoxTreeItem<RestoreNode> root = new CheckBoxTreeItem<>(new RestoreNode("root", null));
        root.setIndependent(true);
        for (String path : filePaths) {
            String normalized = path == null ? "" : path.trim();
            if (normalized.isEmpty()) continue;

            String[] parts = normalized.split("/");
            CheckBoxTreeItem<RestoreNode> current = root;
            StringBuilder currentPath = new StringBuilder();
            for (String part : parts) {
                if (part.isBlank()) continue;
                if (currentPath.length() > 0) currentPath.append("/");
                currentPath.append(part);
                boolean isLeaf = part.equals(parts[parts.length - 1]);
                current = getOrCreateChild(current, part, currentPath.toString(), isLeaf);
            }
        }
        expandAll(root);
        return root;
    }

    private CheckBoxTreeItem<RestoreNode> getOrCreateChild(CheckBoxTreeItem<RestoreNode> parent, String value, String fullPath, boolean isLeaf) {
        for (TreeItem<RestoreNode> rawChild : parent.getChildren()) {
            CheckBoxTreeItem<RestoreNode> child = (CheckBoxTreeItem<RestoreNode>) rawChild;
            if (value.equals(child.getValue().label)) {
                return child;
            }
        }
        CheckBoxTreeItem<RestoreNode> created = new CheckBoxTreeItem<>(new RestoreNode(value, isLeaf ? fullPath : null));
        created.setIndependent(true);
        parent.getChildren().add(created);
        return created;
    }

    private void expandAll(TreeItem<?> node) {
        if (node == null) return;
        node.setExpanded(true);
        for (TreeItem<?> child : node.getChildren()) {
            expandAll(child);
        }
    }

    private Set<String> collectCheckedFilePaths(TreeItem<RestoreNode> root) {
        Set<String> selected = new HashSet<>();
        collectCheckedFilePathsRec(root, selected);
        return selected;
    }

    private void collectCheckedFilePathsRec(TreeItem<RestoreNode> node, Set<String> out) {
        if (node == null) return;
        if (node instanceof CheckBoxTreeItem<RestoreNode> cbNode) {
            boolean isLeaf = cbNode.getChildren().isEmpty();
            if (isLeaf && cbNode.isSelected()) {
                RestoreNode value = cbNode.getValue();
                if (value != null && value.fullPath != null && !value.fullPath.isBlank()) {
                    out.add(value.fullPath);
                }
            }
        }
        for (TreeItem<RestoreNode> child : node.getChildren()) {
            collectCheckedFilePathsRec(child, out);
        }
    }

    private static class RestoreNode {
        private final String label;
        private final String fullPath;

        private RestoreNode(String label, String fullPath) {
            this.label = label;
            this.fullPath = fullPath;
        }

        @Override
        public String toString() {
            return label;
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
        box.setSpacing(12);

        Label scheduleTitle = new Label("Agendamento automático");
        scheduleTitle.setStyle("-fx-font-weight: bold;");

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
            root = yaml.readValue(Files.readString(configPath), Map.class);
            if (root == null) root = new LinkedHashMap<>();
        } else {
            root = new LinkedHashMap<>();
        }

        String backendValue = backendUrl.getText() != null ? backendUrl.getText().trim() : "";
        String emailValue = email.getText() != null ? email.getText().trim() : "";
        String passwordValue = password.getText() != null ? password.getText() : "";
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

        Map<String, Object> schedule = root.containsKey("schedule") && root.get("schedule") instanceof Map<?, ?> existing
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
        planConfigSync.applyPlan(AgentPaths.resolveDefaultConfigPath(), plan);
        ui(() -> backupSourcesConfig.setText(String.join("\n", plan.sources())));
        log("Plano sincronizado do backend para agent.yaml.");
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
        Map<String, Object> root = yaml.readValue(Files.readString(configPath), Map.class);
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

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
