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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

public class DashboardController {

    @FXML private Label lblLastBackup;
    @FXML private Label lblNextBackup;
    @FXML private Label lblSnapshotCount;
    @FXML private Label lblStorageUsed;
    @FXML private Label lblStorageLimit;
    @FXML private ListView<com.keeply.agent.model.SnapshotSummary> listSnapshots;
    @FXML private HBox foldersContainer;
    @FXML private VBox btnAddFolder;
    @FXML private Button btnRestaurar;
    @FXML private Button btnProtegerAgora;
    @FXML private Button btnVerAtividade;

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

        if (btnVerAtividade != null) {
            btnVerAtividade.setOnAction(e -> {
                if (onNavigate != null) onNavigate.accept("Atividade");
            });
        }

        listSnapshots.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(com.keeply.agent.model.SnapshotSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
                dot.setFill(javafx.scene.paint.Color.web(statusColor(item.status())));

                HBox dateRow = new HBox(6);
                dateRow.setAlignment(Pos.CENTER_LEFT);
                Label dateLabel = new Label(formatRelativeDate(item.startedAt()));
                dateLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");
                dateRow.getChildren().add(dateLabel);
                if (getIndex() == 0 && "COMPLETED".equals(item.status())) {
                    Label badge = new Label("Mais recente");
                    badge.setStyle("-fx-background-color: #EDE9FF; -fx-text-fill: #6D47FF; -fx-font-size: 9px; -fx-font-weight: 700; -fx-background-radius: 999; -fx-padding: 2 6;");
                    dateRow.getChildren().add(badge);
                }

                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                String typeText = item.sourcePath() != null ? "Automático" : "Manual";
                Label typeLabel = new Label(typeText);
                typeLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 10px;");

                Label sizeLabel = new Label(formatSize(item.totalOriginalSize()));
                sizeLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 11px; -fx-font-weight: 600;");

                row.getChildren().addAll(dot, dateRow, spacer, typeLabel, sizeLabel);

                if ("COMPLETED".equals(item.status())) {
                    SVGPath downloadIcon = new SVGPath();
                    downloadIcon.setContent("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z");
                    downloadIcon.setStyle("-fx-fill: #CBD5E1;");
                    StackPane dlBox = new StackPane(downloadIcon);
                    dlBox.setScaleX(0.72);
                    dlBox.setScaleY(0.72);
                    row.getChildren().add(dlBox);
                }

                setText(null);
                setGraphic(row);
            }
        });
    }

    private static String formatRelativeDate(Instant instant) {
        if (instant == null) return "-";
        LocalDate today = LocalDate.now();
        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        String time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant);
        if (date.equals(today)) return "Hoje, " + time;
        if (date.equals(today.minusDays(1))) return "Ontem, " + time;
        return DateTimeFormatter.ofPattern("dd/MM, HH:mm").withZone(ZoneId.systemDefault()).format(instant);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "-";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
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
