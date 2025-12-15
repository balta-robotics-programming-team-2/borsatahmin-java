package com.balta.bistanalyzer;

import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DataDownloader {
    // Global lock to ensure only ONE request at a time across all threads
    private static final Lock API_LOCK = new ReentrantLock();
    private static long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 500;

    public static class TimeSeriesData implements Serializable {
        public final List<Date> dates = new ArrayList<>();
        public final List<BigDecimal> close = new ArrayList<>();
        public final List<Long> volume = new ArrayList<>();
    }

    public TimeSeriesData download(String ticker, String period) {
        String cachePeriod = period == null ? "1y" : period;
        CacheManager cm = CacheManager.getInstance();
        // Increased cache TTL to 24 hours (86400 seconds) to reduce API calls
        Object cached = cm.load(ticker, cachePeriod, 86400);
        if (cached instanceof TimeSeriesData) return (TimeSeriesData) cached;

        // Use Stooq CSV endpoint with throttling and exponential backoff
        final int maxRetries = 6;
        final long baseBackoffMs = 3000;
        final long maxBackoffMs = 20000;

        String symbol = ticker.toLowerCase(Locale.ROOT); // e.g., "asels.is"
        int attempt = 0;
        while (true) {
            API_LOCK.lock();
            try {
                // Enforce minimum delay between requests
                long now = System.currentTimeMillis();
                long timeSinceLastRequest = now - lastRequestTime;
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                    Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
                }

                String url = "https://stooq.com/q/d/l/?s=" +
                        URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "&i=d";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (compatible; borsatahmin-java/1.0)")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                lastRequestTime = System.currentTimeMillis();

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Stooq HTTP " + response.statusCode());
                }

                String body = response.body();
                if (body == null || body.isBlank()) {
                    throw new RuntimeException("Stooq empty response");
                }

                TimeSeriesData ts = new TimeSeriesData();
                try (BufferedReader br = new BufferedReader(new StringReader(body))) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length < 6) continue;
                        try {
                            Date date = java.sql.Date.valueOf(p[0]);
                            BigDecimal close = new BigDecimal(p[4]);
                            long volume = p[5].isEmpty() ? 0L : Long.parseLong(p[5]);
                            ts.dates.add(date);
                            ts.close.add(close);
                            ts.volume.add(volume);
                        } catch (Exception parseEx) {
                            // skip malformed row
                        }
                    }
                }

                cm.save(ticker, cachePeriod, ts);
                return ts;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("❌ " + ticker + ": İşlem kesildi - " + e.getMessage());
                return null;
            } catch (Exception e) {
                attempt++;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                long backoff = Math.min((long) (baseBackoffMs * Math.pow(2, Math.max(0, attempt - 1))), maxBackoffMs);
                long jitter = (long) (backoff * (0.2 * (Math.random() - 0.5)));
                backoff = Math.max(1500, backoff + jitter);
                System.err.println("⚠️ " + ticker + ": Stooq hata: " + msg + ". Deneme " + attempt + "/" + maxRetries +
                        ", bekleniyor ~" + backoff + "ms...");
                if (attempt >= maxRetries) {
                    System.err.println("❌ " + ticker + ": Maksimum deneme aşıldı. Vazgeçiliyor.");
                    return null;
                }
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            } finally {
                API_LOCK.unlock();
            }
        }
    }
}
