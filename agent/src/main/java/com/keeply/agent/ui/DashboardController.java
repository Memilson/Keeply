package com.keeply.agent.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class DashboardController {
    private static final DateTimeFormatter SNAPSHOT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @FXML private Label lblLastBackup;
    @FXML private Label lblSnapshotCount;
    @FXML private Label lblStorageUsed;
    @FXML private ListView<com.keeply.agent.model.SnapshotSummary> listSnapshots;
    @FXML private HBox foldersContainer;
    @FXML private VBox btnAddFolder;
    @FXML private Button btnRestaurar;
    @FXML private Button btnProtegerAgora;

    private Consumer<String> onNavigate;
    private Runnable onBackupNow;
    private boolean backupInProgress = false;

    @FXML
    public void initialize() {
        if (btnRestaurar != null) {
            btnRestaurar.setOnAction(e -> {
                if (onNavigate != null) onNavigate.accept("Restore");
            });
        }
        
        if (btnProtegerAgora != null) {
            btnProtegerAgora.setOnAction(e -> {
                if (!backupInProgress && onBackupNow != null) {
                    onBackupNow.run();
                }
            });
        }

        listSnapshots.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(com.keeply.agent.model.SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    
                    StackPane dot = new StackPane(new javafx.scene.shape.Circle(3, javafx.scene.paint.Color.web("#6D47FF")));
                    
                    VBox textInfo = new VBox(2);
                    Label date = new Label(item.startedAt() == null ? "-" : SNAPSHOT_DATE_FORMAT.format(item.startedAt()));
                    date.setStyle("-fx-font-weight: 600; -fx-text-fill: #0F172A; -fx-font-size: 11px;");
                    Label status = new Label(statusLabel(item.status()));
                    status.setStyle("-fx-text-fill: " + statusColor(item.status()) + "; -fx-font-size: 10px; -fx-font-weight: bold;");
                    textInfo.getChildren().addAll(date, status);
                    
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                    Label files = new Label(item.totalFiles() + " arq");
                    files.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

                    row.getChildren().addAll(dot, textInfo, spacer, files);
                    if (!"COMPLETED".equals(item.status())) {
                        setGraphic(row);
                        return;
                    }

                    SVGPath downloadIcon = new SVGPath();
                    downloadIcon.setContent("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z");
                    downloadIcon.setStyle("-fx-fill: #94A3B8;");
                    StackPane dlBox = new StackPane(downloadIcon);
                    dlBox.setScaleX(0.7);
                    dlBox.setScaleY(0.7);

                    row.getChildren().add(dlBox);
                    setGraphic(row);
                }
            }
        });
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "Desconhecido";
        }
        return switch (status) {
            case "COMPLETED" -> "Completo";
            case "IN_PROGRESS" -> "Em progresso";
            case "PROCESSING" -> "Processando";
            case "FAILED" -> "Falhou";
            default -> status;
        };
    }

    private static String statusColor(String status) {
        if (status == null) {
            return "#64748B";
        }
        return switch (status) {
            case "COMPLETED" -> "#16A34A";
            case "FAILED" -> "#DC2626";
            default -> "#6D47FF";
        };
    }

    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }
    
    public void setOnBackupNow(Runnable onBackupNow) {
        this.onBackupNow = onBackupNow;
    }

    public void updateStats(String lastBackup, String count, String storage) {
        lblLastBackup.setText("Último backup: " + lastBackup);
        lblSnapshotCount.setText(count);
        lblStorageUsed.setText(storage);
    }
    
    public void setFolders(java.util.List<String> paths) {
        foldersContainer.getChildren().clear();
        for (String p : paths) {
            java.nio.file.Path path = java.nio.file.Path.of(p);
            String folderName = path.getFileName() != null ? path.getFileName().toString() : p;
            
            VBox card = new VBox(8);
            card.getStyleClass().add("folder-card");
            HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
            
            SVGPath icon = new SVGPath();
            icon.setContent("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
            icon.setStyle("-fx-fill: #Fcd34d;");
            StackPane iconWrapper = new StackPane(icon);
            iconWrapper.setAlignment(Pos.TOP_LEFT);
            
            Region spacer = new Region();
            VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            
            Label title = new Label(folderName);
            title.getStyleClass().add("folder-title");
            
            Label subtitle = new Label("Monitorado");
            subtitle.getStyleClass().add("folder-subtitle");
            
            card.getChildren().addAll(iconWrapper, spacer, title, subtitle);
            foldersContainer.getChildren().add(card);
        }
        if (btnAddFolder != null) {
            foldersContainer.getChildren().add(btnAddFolder);
        }
    }
    
    public void setSnapshotsList(java.util.List<com.keeply.agent.model.SnapshotSummary> snapshots) {
        listSnapshots.getItems().setAll(snapshots);
    }

    public void setBackupInProgress(boolean inProgress) {
        this.backupInProgress = inProgress;
        if (btnProtegerAgora != null) {
            btnProtegerAgora.setDisable(inProgress);
            btnProtegerAgora.setText(inProgress ? "Protegendo..." : "Proteger Agora");
            btnProtegerAgora.setOpacity(inProgress ? 0.6 : 1.0);
        }
    }
}