package com.balta.bistanalyzer;

import com.balta.bistanalyzer.models.LstmModel;
import com.balta.bistanalyzer.models.XGBoostModel;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Analyzer {
    private final DataDownloader downloader = new DataDownloader();
    // Reduced to 2 threads since we now have global locking in DataDownloader
    // This prevents thread contention while still allowing some parallelism for non-API work
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public static class ScreeningResult {
        public final String ticker;
        public final int score;
        public final double rsi;
        public final double price;
        public final double volumeRatio;
        public final double momentum5d;
        public final boolean aboveEma;

        public ScreeningResult(String ticker, int score, double rsi, double price, double volumeRatio, double momentum5d, boolean aboveEma) {
            this.ticker = ticker; this.score = score; this.rsi = rsi; this.price = price; this.volumeRatio = volumeRatio; this.momentum5d = momentum5d; this.aboveEma = aboveEma;
        }
    }

    public static class DetailedResult {
        public final String ticker;
        public final double probability;
        public final double currentPrice;
        public final double lstmProb;
        public final double xgbProb;
        public int screeningScore;
        public double volumeRatio;
        public double momentum5d;

        public DetailedResult(String ticker, double probability, double currentPrice, double lstmProb, double xgbProb) {
            this.ticker = ticker; this.probability = probability; this.currentPrice = currentPrice; this.lstmProb = lstmProb; this.xgbProb = xgbProb;
        }
    }

    public List<ScreeningResult> quickScreen(List<String> tickers) {
        List<ScreeningResult> results = new ArrayList<>();
        int batchSize = 10; // Process 10 tickers at a time

        System.out.println("📦 Processing " + tickers.size() + " tickers in batches of " + batchSize);

        for (int i = 0; i < tickers.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            int totalBatches = (tickers.size() + batchSize - 1) / batchSize;

            List<String> batch = tickers.subList(i, Math.min(i + batchSize, tickers.size()));
            System.out.println("🔄 Processing batch " + batchNum + "/" + totalBatches + " (" + batch.size() + " tickers)");

            List<Future<ScreeningResult>> futures = new ArrayList<>();
            for (String t : batch) {
                futures.add(pool.submit(() -> quickScreenTicker(t)));
            }

            for (Future<ScreeningResult> f : futures) {
                try {
                    ScreeningResult r = f.get();
                    if (r != null) results.add(r);
                } catch (Exception ignored) {}
            }

            // Add delay between batches to further reduce rate limiting risk
            if (i + batchSize < tickers.size()) {
                try {
                    Thread.sleep(2000); // 2 second pause between batches
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return results;
    }

    private ScreeningResult quickScreenTicker(String ticker) {
        try {
            DataDownloader.TimeSeriesData data = downloader.download(ticker, "3mo");
            if (data == null || data.close.size() < 20) return null;

            List<BigDecimal> closes = data.close;
            List<Long> volumes = data.volume;

            double currentPrice = closes.get(closes.size()-1).doubleValue();
            double ema20 = simpleEMA(closes, 20);
            double avgVol = simpleMALong(volumes, 20);
            double recentVol = volumes.get(volumes.size()-1);
            double volumeRatio = avgVol > 0 ? recentVol / avgVol : 1.0;
            double price5ago = closes.size() >= 6 ? closes.get(closes.size()-6).doubleValue() : currentPrice;
            double priceChange5d = (currentPrice / price5ago - 1.0) * 100.0;
            double rsi = simpleRSI(closes, 14);
            boolean aboveEma = currentPrice > ema20;

            int score = 0;
            if (rsi >= 30 && rsi <= 70) score++;
            if (aboveEma) score++;
            if (volumeRatio > 1.2) score++;
            if (priceChange5d > 0) score++;

            return new ScreeningResult(ticker, score, rsi, currentPrice, volumeRatio, priceChange5d, aboveEma);
        } catch (Exception e) {
            System.err.println("⚠️ " + ticker + ": Hızlı tarama hatası - " + e.getMessage());
            return null;
        }
    }

    public List<DetailedResult> analyzeMultipleTickers(List<String> tickers) {
        System.out.println("🔍 Hızlı tarama başlatılıyor...");
        List<ScreeningResult> screeningResults = quickScreen(tickers);
        screeningResults.sort(Comparator.comparingInt((ScreeningResult r)->r.score).reversed());

        List<ScreeningResult> promising = screeningResults.stream().filter(r -> r.score >= 3).collect(Collectors.toList());
        if (promising.size() < 5) promising = screeningResults.stream().filter(r -> r.score >= 2).limit(12).collect(Collectors.toList());
        if (promising.size() > 10) promising = promising.subList(0, 10);

        System.out.println("📊 Tarama tamamlandı: " + screeningResults.size() + " hisse tarandı");
        System.out.println("🎯 Detaylı analiz için seçilen: " + promising.size() + " hisse");

        List<DetailedResult> detailed = new ArrayList<>();
        int i = 0;
        for (ScreeningResult s : promising) {
            i++;
            System.out.printf("Analyzing %d/%d: %s%n", i, promising.size(), s.ticker);
            DetailedResult dr = analyzeTicker(s.ticker);
            if (dr != null) {
                dr.screeningScore = s.score;
                dr.volumeRatio = s.volumeRatio;
                dr.momentum5d = s.momentum5d;
                detailed.add(dr);
            }
        }
        pool.shutdown();
        return detailed;
    }

    public DetailedResult analyzeTicker(String ticker) {
        try {
            System.out.println("\n" + ticker.replace(".IS","") + " analiz ediliyor...");

            DataDownloader.TimeSeriesData data = downloader.download(ticker, "1y");
            if (data == null || data.close.size() < 100) {
                System.err.println("❌ " + ticker + ": Yetersiz veri");
                return null;
            }

            // Compute indicators
            Technicals.Indicators ind = Technicals.calculate(null, data.close);

            // Build target vector analogous to Python: next day >= target percent
            double target = 0.049; // default 5% - you may set mapping from env
            int seqLen = 20;

            // Prepare arrays for LSTM
            // Scale manually using min-max per feature (Close, Volume)
            List<BigDecimal> closes = data.close;
            List<Long> volumes = data.volume;
            int n = closes.size();

            // compute target array
            double[] targetArr = new double[n];
            for (int i = 0; i < n-1; i++) {
                double pct = closes.get(i+1).doubleValue() / closes.get(i).doubleValue() - 1.0;
                targetArr[i] = pct >= target ? 1.0 : 0.0;
            }
            // drop last row's target (no next day)
            int usable = n - seqLen - 1;
            if (usable < 20) {
                System.err.println("❌ " + ticker + ": LSTM eğitimi için yetersiz veri (" + usable + " örnek)");
                return null;
            }

            // prepare scaled matrix
            double minClose = closes.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0.0);
            double maxClose = closes.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(1.0);
            double minVol = volumes.stream().mapToLong(Long::longValue).min().orElse(0L);
            double maxVol = volumes.stream().mapToLong(Long::longValue).max().orElse(1L);

            // Create X_lstm and y_lstm
            double[][][] X_lstm = new double[usable][seqLen][2];
            double[] y_lstm = new double[usable];
            for (int i = 0; i < usable; i++) {
                for (int t = 0; t < seqLen; t++) {
                    double c = closes.get(i + t).doubleValue();
                    double v = volumes.get(i + t);
                    double cs = (c - minClose) / (maxClose - minClose + 1e-9);
                    double vs = (v - minVol) / (maxVol - minVol + 1e-9);
                    X_lstm[i][t][0] = cs;
                    X_lstm[i][t][1] = vs;
                }
                y_lstm[i] = targetArr[i + seqLen - 1];
            }

            // Split train/test (80/20)
            int trainCount = (int) (usable * 0.8);
            double sumPos = 0;
            for (int i = 0; i < trainCount; i++) sumPos += y_lstm[i];
            System.out.printf("  📊 %s: %.0f/%d pozitif örnek (%%%4.1f)%n", ticker.replace(".IS",""), sumPos, trainCount, sumPos / trainCount * 100.0);

            double lstmProb;
            if (sumPos == 0) {
                lstmProb = 0.01;
                System.out.println("  ⚠️ " + ticker + ": Hiç pozitif örnek yok, LSTM baseline kullanılıyor!");
            } else {
                // train LSTM
                LstmModel lstm = new LstmModel();
                int epochs = Math.min(15, Math.max(5, trainCount / 10));
                lstmProb = lstm.trainAndPredict(X_lstm, Arrays.copyOfRange(y_lstm, 0, usable), epochs, 16);
            }

            // XGBoost features matrix (time-varying per day)
            // Features per row i (information available up to day i):
            // [rsi14, ema20, ema50, macd(ema12-ema26), bollHigh20, bollLow20, volume]
            int m = 7;
            int start = Math.max(50, 20); // need at least 50 for ema50 and 20 for bollinger
            int rows = Math.max(0, (n - 1) - start);
            if (rows < 10) {
                System.err.println("❌ " + ticker + ": XGBoost için yetersiz veri");
                return null;
            }
            double[][] X_xgb = new double[rows][m];
            double[] y_xgb = new double[rows];

            for (int i = start, r = 0; i < n - 1; i++, r++) {
                List<BigDecimal> closesUpToI = data.close.subList(0, i + 1);

                double rsi14 = simpleRSI(closesUpToI, 14);
                double ema20v = simpleEMA(closesUpToI, 20);
                double ema50v = simpleEMA(closesUpToI, 50);
                double ema12v = simpleEMA(closesUpToI, 12);
                double ema26v = simpleEMA(closesUpToI, 26);
                double macdV = ema12v - ema26v;

                // Bollinger 20, stdev over last 20 closes
                int win = 20;
                int from = Math.max(0, (i + 1) - win);
                List<BigDecimal> last20 = data.close.subList(from, i + 1);
                double mean = last20.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
                double var = 0.0;
                for (BigDecimal bd : last20) {
                    double d = bd.doubleValue() - mean;
                    var += d * d;
                }
                var /= Math.max(1, last20.size());
                double sd20 = Math.sqrt(var);
                double bollHigh = mean + 2 * sd20;
                double bollLow = mean - 2 * sd20;

                X_xgb[r][0] = rsi14;
                X_xgb[r][1] = ema20v;
                X_xgb[r][2] = ema50v;
                X_xgb[r][3] = macdV;
                X_xgb[r][4] = bollHigh;
                X_xgb[r][5] = bollLow;
                X_xgb[r][6] = volumes.get(i);
                y_xgb[r] = targetArr[i];
            }

            double xgbProb;
            try {
                XGBoostModel xg = new XGBoostModel();
                xgbProb = xg.trainAndPredict(X_xgb, y_xgb);
            } catch (Exception ex) {
                System.err.println("❌ " + ticker + ": XGBoost model hatası - " + ex.getMessage());
                return null;
            }

            double finalProb = (lstmProb + xgbProb) / 2.0;
            double currentPrice = closes.get(closes.size() - 1).doubleValue();

            return new DetailedResult(ticker, finalProb, currentPrice, lstmProb, xgbProb);

        } catch (Exception e) {
            System.err.println(ticker + " analiz hatası: " + e.getMessage());
            return null;
        }
    }

    // Utility functions (same as earlier)
    private double simpleEMA(List<BigDecimal> data, int period) {
        if (data.size() < period) return data.get(data.size()-1).doubleValue();
        double k = 2.0/(period+1);
        double ema = data.get(data.size()-period).doubleValue();
        for (int i = data.size()-period+1; i < data.size(); i++) {
            ema = data.get(i).doubleValue() * k + ema * (1-k);
        }
        return ema;
    }

    private double simpleMALong(List<Long> data, int period) {
        int n = data.size();
        int start = Math.max(0, n - period);
        double sum = 0; int count=0;
        for (int i=start;i<n;i++){ sum += data.get(i); count++; }
        return count==0?0:sum/count;
    }

    private double simpleRSI(List<BigDecimal> closes, int period) {
        if (closes.size() <= period) return 50.0;
        double gain=0, loss=0;
        for (int i=closes.size()-period; i<closes.size(); i++){
            double diff = closes.get(i).doubleValue() - closes.get(i-1).doubleValue();
            if (diff>0) gain+=diff; else loss-=diff;
        }
        if (loss==0) return 100.0;
        double rs = (gain/period)/(loss/period);
        return 100 - (100/(1+rs));
    }
}
