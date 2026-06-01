package com.keeply.agent.ui;

import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import java.net.URI;
import java.util.function.Consumer;

public class MainShellController {

    @FXML private VBox navMenu;
    @FXML private Button btnInicio;
    @FXML private Button btnMeusArquivos;
    @FXML private Button btnBackups;
    @FXML private Button btnAtividade;
    @FXML private Button btnClose;
    @FXML private HBox appHeader;
    @FXML private HBox profileBox;
    @FXML private StackPane contentHost;
    @FXML private Label lblProfileInitials;
    @FXML private Label lblProfileName;

    @FXML private Label lblStorageUsed;
    @FXML private ProgressBar pbStorage;
    @FXML private Label lblStoragePercent;

    private Consumer<String> onNavigate;
    private Runnable onLogout;
    private double windowDragOffsetX;
    private double windowDragOffsetY;

    @FXML
    public void initialize() {
        btnInicio.setOnAction(e -> navigateTo("Dashboard", btnInicio));
        btnMeusArquivos.setOnAction(e -> navigateTo("Restore", btnMeusArquivos));
        btnBackups.setOnAction(e -> navigateTo("Backup", btnBackups));
        btnAtividade.setOnAction(e -> navigateTo("Atividade", btnAtividade));
        profileBox.setCursor(Cursor.HAND);

        setupProfileDropdown();
    }

    private void setupProfileDropdown() {
        ContextMenu profileMenu = new ContextMenu();
        profileMenu.getStyleClass().add("profile-context-menu");

        MenuItem manageProfile = new MenuItem("Gerenciar Perfil");
        manageProfile.getStyleClass().add("profile-menu-item");
        SVGPath manageIcon = new SVGPath();
        manageIcon.setContent("M12 12c2.21 0 4-1.79 4-4S14.21 4 12 4 8 5.79 8 8s1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z");
        manageIcon.getStyleClass().add("profile-menu-icon");
        manageProfile.setGraphic(manageIcon);
        manageProfile.setOnAction(e -> Thread.startVirtualThread(() -> openBrowser("http://localhost:3000/dashboard/perfil")));

        MenuItem logout = new MenuItem("Sair");
        logout.getStyleClass().addAll("profile-menu-item", "profile-menu-item-danger");
        SVGPath logoutIcon = new SVGPath();
        logoutIcon.setContent("M10 17l1.41-1.41L8.83 13H20v-2H8.83l2.58-2.59L10 7l-5 5z M4 4h8V2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h8v-2H4V4z");
        logoutIcon.getStyleClass().addAll("profile-menu-icon", "profile-menu-icon-danger");
        logout.setGraphic(logoutIcon);
        logout.setOnAction(e -> {
            if (onLogout != null) onLogout.run();
        });

        profileMenu.getItems().addAll(manageProfile, new SeparatorMenuItem(), logout);

        profileBox.setOnMouseClicked(e -> {
            profileMenu.show(profileBox, javafx.geometry.Side.BOTTOM, 0, 0);
        });
    }

    private void openBrowser(String url) {
        try {
            // Avoid hard-linking java.awt.Desktop so FXML controller can load on trimmed runtimes.
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Object actionBrowse = Class.forName("java.awt.Desktop$Action")
                    .getField("BROWSE")
                    .get(null);
            boolean desktopSupported = (boolean) desktopClass.getMethod("isDesktopSupported").invoke(null);
            if (desktopSupported) {
                Object desktop = desktopClass.getMethod("getDesktop").invoke(null);
                boolean canBrowse = (boolean) desktopClass.getMethod("isSupported", actionBrowse.getClass())
                        .invoke(desktop, actionBrowse);
                if (canBrowse) {
                    desktopClass.getMethod("browse", URI.class).invoke(desktop, new URI(url));
                    return;
                }
            }
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public void setNavigationHandler(Consumer<String> handler) {
        this.onNavigate = handler;
    }

    public void bindWindow(Stage stage) {
        btnClose.setOnAction(e -> stage.close());
        appHeader.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                windowDragOffsetX = e.getScreenX() - stage.getX();
                windowDragOffsetY = e.getScreenY() - stage.getY();
            }
        });
        appHeader.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                stage.setX(e.getScreenX() - windowDragOffsetX);
                stage.setY(e.getScreenY() - windowDragOffsetY);
            }
        });
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
