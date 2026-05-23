package com.keeply.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.core.RestoreEngine;
import com.keeply.agent.core.RestoreEngine.OverwritePolicy;
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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class KeeplyAgentApp extends Application {
    private BackendClient backend;
    private LocalDatabase db;
    private UUID deviceId;
    private final TextArea logs = new TextArea();

    private TextField backendUrl;
    private TextField email;
    private PasswordField password;
    private Label status;

    @Override
    public void start(Stage stage) {
        db = new LocalDatabase("keeply_agent.db");
        
        backendUrl = new TextField("http://localhost:8080");
        email = new TextField();
        password = new PasswordField();
        status = new Label("Desconectado");

        logs.setEditable(false);

        TabPane tabs = new TabPane(
                new Tab("Login", loginView()),
                new Tab("Dashboard", dashboardView()),
                new Tab("Backup", backupView(stage)),
                new Tab("Restore", restoreView(stage)),
                new Tab("Configurações", configView()),
                new Tab("Logs", logs)
        );

        tabs.getTabs().forEach(t -> t.setClosable(false));

        Scene scene = new Scene(tabs, 900, 600);
        stage.setTitle("Keeply Agent MVP");
        stage.setScene(scene);
        stage.show();
    }

    private Pane loginView() {
        GridPane grid = grid();

        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> runAsync(() -> {
            String userEmail = email.getText().trim();
            String userPass = password.getText();
            
            log("Tentando login para: " + userEmail);
            backend = new BackendClient(backendUrl.getText().trim());
            backend.login(userEmail, userPass);

            String hostname = InetAddress.getLocalHost().getHostName();
            deviceId = backend.registerDevice(hostname, hostname, System.getProperty("os.name"), "0.1.0");

            ui(() -> status.setText("Conectado. Device: " + deviceId));
            log("Login OK. Device registrado: " + deviceId);
        }));

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
        CheckBoxTreeItem<RestoreNode> root = new CheckBoxTreeItem<>(new RestoreNode("root", null, false));
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
        CheckBoxTreeItem<RestoreNode> created = new CheckBoxTreeItem<>(new RestoreNode(value, isLeaf ? fullPath : null, isLeaf));
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
        private final boolean file;

        private RestoreNode(String label, String fullPath, boolean file) {
            this.label = label;
            this.fullPath = fullPath;
            this.file = file;
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
        box.getChildren().addAll(
                new Label("URL do backend:"),
                backendUrl,
                new Label("Device atual:"),
                new Label(deviceId == null ? "Ainda não registrado" : deviceId.toString())
        );
        return box;
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
