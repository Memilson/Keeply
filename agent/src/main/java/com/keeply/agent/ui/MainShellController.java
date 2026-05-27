package com.keeply.agent.ui;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public class MainShellController {

    @FXML private VBox navMenu;
    @FXML private Button btnInicio;
    @FXML private Button btnMeusArquivos;
    @FXML private Button btnBackups;
    @FXML private Button btnAtividade;
    @FXML private TextField searchField;
    @FXML private StackPane contentHost;
    @FXML private Label lblProfileInitials;
    @FXML private Label lblProfileName;

    @FXML private Label lblStorageUsed;
    @FXML private ProgressBar pbStorage;
    @FXML private Label lblStoragePercent;

    private Consumer<String> onNavigate;

    @FXML
    public void initialize() {
        btnInicio.setOnAction(e -> navigateTo("Dashboard", btnInicio));
        btnMeusArquivos.setOnAction(e -> navigateTo("Restore", btnMeusArquivos));
        btnBackups.setOnAction(e -> navigateTo("Backup", btnBackups));
        btnAtividade.setOnAction(e -> navigateTo("Logs", btnAtividade));
    }

    public void setNavigationHandler(Consumer<String> handler) {
        this.onNavigate = handler;
    }

    public StackPane getContentHost() {
        return contentHost;
    }

    public void setProfile(String name) {
        if (name == null || name.isBlank()) {
            lblProfileName.setText("Convidado");
            lblProfileInitials.setText("C");
            return;
        }
        lblProfileName.setText(name);
        String[] parts = name.split("\\s+");
        if (parts.length >= 2) {
            lblProfileInitials.setText((parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase());
        } else {
            lblProfileInitials.setText(name.substring(0, Math.min(2, name.length())).toUpperCase());
        }
    }

    public void updateStorageInfo(String usedGb, double percent) {
        if (lblStorageUsed != null) lblStorageUsed.setText(usedGb);
        if (pbStorage != null) pbStorage.setProgress(percent);
        if (lblStoragePercent != null) lblStoragePercent.setText(String.format("%.0f%% usado", percent * 100));
    }

    public void setContent(Node node) {
        contentHost.getChildren().setAll(node);
    }

    private void navigateTo(String viewName, Button clicked) {
        // Update active class
        navMenu.getChildren().forEach(n -> {
            if (n instanceof Button btn) {
                btn.getStyleClass().remove("active-nav-button");
            }
        });
        clicked.getStyleClass().add("active-nav-button");

        if (onNavigate != null) {
            onNavigate.accept(viewName);
        }
    }
}
