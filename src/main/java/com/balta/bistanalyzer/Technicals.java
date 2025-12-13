package com.balta.bistanalyzer;

import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

public class Technicals {
    public static class Indicators {
        public double rsi;
        public double ema20;
        public double ema50;
        public double macd;
        public double bollHigh;
        public double bollLow;
    }

    public static Indicators calculate(List<java.util.Date> dates, List<BigDecimal> closePrices) {
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("prices").build();
        for (BigDecimal c : closePrices) {
            // build minimal bar with same open/high/low/close
            Bar bar = new BaseBar(
                    Duration.ofDays(1),
                    ZonedDateTime.now(),
                    c.doubleValue(), // open
                    c.doubleValue(), // high
                    c.doubleValue(), // low
                    c.doubleValue(), // close
                    0.0              // volume
            );
            series.addBar(bar);
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Indicators ind = new Indicators();
        try {
            RSIIndicator rsi = new RSIIndicator(close, 14);
            EMAIndicator ema20 = new EMAIndicator(close, 20);
            EMAIndicator ema50 = new EMAIndicator(close, 50);
            MACDIndicator macd = new MACDIndicator(close, 12, 26);
            BollingerBandsMiddleIndicator bbm = new BollingerBandsMiddleIndicator(new SMAIndicator(close, 20));
            StandardDeviationIndicator sd = new StandardDeviationIndicator(close, 20);
            BollingerBandsUpperIndicator bbu = new BollingerBandsUpperIndicator(bbm, sd, DecimalNum.valueOf(2));
            BollingerBandsLowerIndicator bbl = new BollingerBandsLowerIndicator(bbm, sd, DecimalNum.valueOf(2));

            int idx = Math.max(series.getEndIndex(), 0);
            ind.rsi = safeNumToDouble(rsi.getValue(idx));
            ind.ema20 = safeNumToDouble(ema20.getValue(idx));
            ind.ema50 = safeNumToDouble(ema50.getValue(idx));
            ind.macd = safeNumToDouble(macd.getValue(idx));
            ind.bollHigh = safeNumToDouble(bbu.getValue(idx));
            ind.bollLow = safeNumToDouble(bbl.getValue(idx));
        } catch (Exception e) {
            System.err.println("Indicator calculation error: " + e.getMessage());
        }
        return ind;
    }

    private static double safeNumToDouble(Num n) {
        try { return n.doubleValue(); } catch (Exception e) { return 0.0; }
    }
}