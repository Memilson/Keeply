#include "KeeplyMainWindow.hpp"
#include "KeeplyStyle.hpp"

#include <QApplication>
#include <QCoreApplication>
#include <QDir>
#include <filesystem>

int main(int argc, char* argv[]) {
    // Must be set before QApplication to ensure platform plugin (qwindows.dll) is found
    QCoreApplication::addLibraryPath(QDir::currentPath());
    QCoreApplication::addLibraryPath(QDir::currentPath() + "/vcpkg_installed/x64-windows/debug/Qt6/plugins");
    QCoreApplication::addLibraryPath(QDir::currentPath() + "/vcpkg_installed/x64-windows/Qt6/plugins");

    QApplication app(argc, argv);
    app.setApplicationName("Keeply");
    app.setApplicationVersion("0.1.0");
    app.setOrganizationName("Keeply");

    app.setStyleSheet(keeply::style::globalStylesheet());

    std::filesystem::path configPath = "keeply.local.json";
    if (argc > 1) configPath = argv[1];

    keeply::KeeplyMainWindow window(std::move(configPath));
    window.show();

    return app.exec();
}
