package com.keeply.agent;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.RestoreEngine;
import com.keeply.agent.model.SnapshotSummary;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class KeeplyAgentApp extends Application {
    private BackendClient backend;
    private UUID deviceId;
    private final TextArea logs = new TextArea();

    private TextField backendUrl;
    private TextField email;
    private PasswordField password;
    private Label status;

    @Override
    public void start(Stage stage) {
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
            backend = new BackendClient(backendUrl.getText());
            backend.login(email.getText(), password.getText());

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
                UUID snapshotId = new BackupEngine(backend).backup(deviceId, source, this::log);
                log("Snapshot final: " + snapshotId);
            });
        });

        box.getChildren().addAll(new Label("Pasta de origem:"), folder, choose, backup);
        return box;
    }

    private Pane restoreView(Stage stage) {
        VBox box = box();

        TextField snapshotId = new TextField();
        snapshotId.setPromptText("UUID do snapshot");

        TextField destination = new TextField();
        destination.setPromptText("Pasta de destino");

        Button choose = new Button("Selecionar destino");
        choose.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) destination.setText(dir.toPath().toString());
        });

        Button list = new Button("Listar snapshots no log");
        list.setOnAction(e -> {
            if (!ready()) return;
            runAsync(() -> {
                for (SnapshotSummary s : backend.listSnapshots()) {
                    log(s.id() + " | " + s.status() + " | " + s.sourcePath());
                }
            });
        });

        Button restore = new Button("Restaurar");
        restore.setOnAction(e -> {
            if (!ready()) return;
            runAsync(() -> new RestoreEngine(backend).restore(
                    UUID.fromString(snapshotId.getText()),
                    Path.of(destination.getText()),
                    this::log
            ));
        });

        box.getChildren().addAll(
                new Label("Snapshot:"), snapshotId,
                new Label("Destino:"), destination,
                choose,
                list,
                restore
        );
        return box;
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
                log("ERRO: " + ex.getMessage());
            }
        });
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
