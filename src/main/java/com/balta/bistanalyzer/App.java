package com.balta.bistanalyzer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class App {
    public static void main(String[] args) {
        Instant start = Instant.now();
        System.out.println("Toplam süre sayacı başlatıldı...");

        List<String> tickers = Tickers.ALL.stream().distinct().sorted().collect(Collectors.toList());

        System.out.println("🚀 Çoklu hisse analizi başlatılıyor...");
        System.out.println("📋 Analiz edilecek hisseler: " + String.join(", ", tickers));

        Analyzer analyzer = new Analyzer();
        List<Analyzer.DetailedResult> results = analyzer.analyzeMultipleTickers(tickers);

        if (results != null && !results.isEmpty()) {
            System.out.println("\n✅ " + results.size() + " hisse başarıyla analiz edildi!");
        } else {
            System.out.println("❌ Hiçbir hisse analiz edilemedi!");
        }

        Instant end = Instant.now();
        Duration total = Duration.between(start, end);
        long minutes = total.toMinutes();
        long seconds = total.minusMinutes(minutes).getSeconds();
        System.out.printf("\n⏱️ Toplam işlem süresi: %02d:%02d%n", minutes, seconds);

        CacheManager.getInstance().shutdown();
    }
}
