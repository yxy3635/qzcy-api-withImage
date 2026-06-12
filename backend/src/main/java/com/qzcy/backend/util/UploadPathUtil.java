package com.qzcy.backend.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UploadPathUtil {

    private static final String DEFAULT_IMAGE_PATH = "userImage";

    private UploadPathUtil() {
    }

    public static Path resolveImageRoot(String imagePath, Class<?> anchorClass) {
        String configuredPath = imagePath == null || imagePath.isBlank() ? DEFAULT_IMAGE_PATH : imagePath.trim();
        Path configured = Path.of(configuredPath);
        Path path = configured.isAbsolute()
                ? configured
                : resolveRuntimeBaseDir().resolve(configured);

        path = path.toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create image directory: " + path, e);
            }
        }

        return path;
    }

    private static Path resolveRuntimeBaseDir() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        if (isBackendDir(userDir)) {
            return userDir;
        }

        Path backendDir = userDir.resolve("backend");
        if (isBackendDir(backendDir)) {
            return backendDir;
        }

        return userDir;
    }

    private static boolean isBackendDir(Path path) {
        return Files.isDirectory(path.resolve("src/main/java"))
                && Files.isRegularFile(path.resolve("pom.xml"));
    }
}
