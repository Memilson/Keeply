package com.keeply.agent.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;

public class DashboardController {

    @FXML private Label lblLastBackup;
    @FXML private Label lblSnapshotCount;
    @FXML private Label lblStorageUsed;
    @FXML private ListView<String> listSnapshots;
    @FXML private HBox foldersContainer;
    @FXML private VBox btnAddFolder;

    @FXML
    public void initialize() {
        // Setup initial dummy data or styling for the list
        listSnapshots.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Create custom layout for list items matching the visual identity
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    
                    StackPane dot = new StackPane(new javafx.scene.shape.Circle(3, javafx.scene.paint.Color.web("#6D47FF")));
                    Label name = new Label(item.split("\\|")[0].trim());
                    name.setStyle("-fx-font-weight: 600; -fx-text-fill: #0F172A; -fx-font-size: 11px;");
                    
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                    
                    Label type = new Label("Automático");
                    type.setStyle("-fx-text-fill: #6D47FF; -fx-font-size: 10px;");
                    
                    Label size = new Label("2.4 GB");
                    size.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
                    
                    SVGPath downloadIcon = new SVGPath();
                    downloadIcon.setContent("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z");
                    downloadIcon.setStyle("-fx-fill: #94A3B8;");
                    StackPane dlBox = new StackPane(downloadIcon);
                    dlBox.setScaleX(0.7);
                    dlBox.setScaleY(0.7);

                    row.getChildren().addAll(dot, name, spacer, type, size, dlBox);
                    setGraphic(row);
                }
            }
        });
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
    
    public void setSnapshotsList(java.util.List<String> snapshots) {
        listSnapshots.getItems().setAll(snapshots);
    }
}
