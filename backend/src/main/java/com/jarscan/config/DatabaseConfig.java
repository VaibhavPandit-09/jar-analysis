package com.jarscan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(JarScanProperties properties) {
        Path dbPath = Path.of(properties.dbPath()).toAbsolutePath().normalize();
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create database directory for " + dbPath, ex);
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }
}
