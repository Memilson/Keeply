package com.keeply.agent.ui;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public class MainShellController {

    @FXML private VBox navMenu;
    @FXML private Button btnInicio;
    @FXML private Button btnMeusArquivos;
    @FXML private Button btnBackups;
    @FXML private Button btnSnapshots;
    @FXML private Button btnRestaurar;
    @FXML private Button btnCompartilhado;
    @FXML private Button btnAtividade;
    @FXML private Button btnConfiguracoes;
    @FXML private TextField searchField;
    @FXML private StackPane contentHost;
    @FXML private javafx.scene.control.Label lblProfileInitials;
    @FXML private javafx.scene.control.Label lblProfileName;

    private Consumer<String> onNavigate;

    @FXML
    public void initialize() {
        btnInicio.setOnAction(e -> navigateTo("Dashboard", btnInicio));
        btnBackups.setOnAction(e -> navigateTo("Backup", btnBackups));
        btnRestaurar.setOnAction(e -> navigateTo("Restore", btnRestaurar));
        btnConfiguracoes.setOnAction(e -> navigateTo("Configurações", btnConfiguracoes));
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
