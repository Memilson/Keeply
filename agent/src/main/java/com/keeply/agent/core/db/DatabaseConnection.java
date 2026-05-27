package com.keeply.agent.core.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection implements AutoCloseable {
    private final String url;
    private Connection connection;

    public DatabaseConnection(String path) {
        this.url = "jdbc:sqlite:" + path;
        try {
            this.connection = DriverManager.getConnection(url);
            new LocalSchemaManager().initialize(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar SQLite", e);
        }
    }

    public Connection get() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
