package com.balta.bistanalyzer;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DataDownloader {
    // Global lock to ensure only ONE request at a time across all threads
    private static final Lock API_LOCK = new ReentrantLock();
    private static long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // 1 second between requests

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

        // Use global lock to ensure only ONE thread makes API calls at a time
        API_LOCK.lock();
        try {
            // Enforce minimum delay between requests
            long now = System.currentTimeMillis();
            long timeSinceLastRequest = now - lastRequestTime;
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest);
            }

            String symbol = ticker.replace(".IS", "");
            Stock stock = YahooFinance.get(symbol);
            Calendar from = Calendar.getInstance();
            from.add(Calendar.YEAR, -1);
            List<HistoricalQuote> history = stock.getHistory(from, Calendar.getInstance(), yahoofinance.histquotes.Interval.DAILY);

            lastRequestTime = System.currentTimeMillis();

            TimeSeriesData ts = new TimeSeriesData();
            for (HistoricalQuote q : history) {
                if (q.getClose() == null) continue;
                ts.dates.add(q.getDate().getTime());
                ts.close.add(q.getClose());
                ts.volume.add(q.getVolume() == null ? 0L : q.getVolume());
            }
            cm.save(ticker, cachePeriod, ts);
            return ts;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ " + ticker + ": İşlem kesildi - " + e.getMessage());
            return null;
        } catch (Exception e) {
            // Check if it's a rate limit error (429)
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                System.err.println("⚠️ " + ticker + ": Rate limit - bekleniyor...");
                try {
                    Thread.sleep(5000); // Wait 5 seconds on rate limit
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            System.err.println("❌ " + ticker + ": Veri indirme hatası - " + e.getMessage());
            return null;
        } finally {
            API_LOCK.unlock();
        }
    }
}
