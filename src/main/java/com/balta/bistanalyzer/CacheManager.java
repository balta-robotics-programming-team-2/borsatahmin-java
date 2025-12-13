package com.balta.bistanalyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class CacheManager {
    private static final CacheManager INSTANCE = new CacheManager();
    private final Path cacheDir;

    private CacheManager() {
        cacheDir = Paths.get("cache");
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    public Path getCachePath(String ticker, String period) {
        return cacheDir.resolve(ticker + "_" + period + ".ser");
    }

    public void save(String ticker, String period, Serializable obj) {
        Path p = getCachePath(ticker, period);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(p.toFile()))) {
            oos.writeObject(obj);
        } catch (Exception e) {
            System.err.println("Cache save error: " + e.getMessage());
        }
    }

    public Object load(String ticker, String period, long maxAgeSeconds) {
        Path p = getCachePath(ticker, period);
        if (!Files.exists(p)) return null;
        try {
            long age = Instant.now().getEpochSecond() - Files.getLastModifiedTime(p).toInstant().getEpochSecond();
            if (age > maxAgeSeconds) return null;
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(p.toFile()))) {
                return ois.readObject();
            }
        } catch (Exception e) {
            System.err.println("Cache load error: " + e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        // no-op
    }
}
