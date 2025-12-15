package com.balta.bistanalyzer;

import java.util.*;

public class Main {

    static void main() {

        AI ai = new AI();

        senaryo scenarioEngine = new senaryo();
        senaryo.senaryoSonuc currentScenario = scenarioEngine.generate();

        String[] tickers = {
                "THYAO.IS", "AKBNK.IS", "GARAN.IS", "ISCTR.IS", "KCHOL.IS",
                "TCELL.IS", "TUPRS.IS", "BIMAS.IS", "ASELS.IS", "EREGL.IS"
        };

        List<Stock> stocks = new ArrayList<>();

        for (int i = 0; i < tickers.length; i++) {
            stocks.add(new Stock(
                    "Sirket" + (i + 1),
                    tickers[i],
                    100
            ));
        }

        String scenarioName = switch (currentScenario.isim) {
            case "High Increase" -> "HIGH_UP";
            case "Increase" -> "UP";
            case "Decrease" -> "DOWN";
            case "High Decrease" -> "HIGH_DOWN";
            default -> "UP";
        };

        for (Stock s : stocks) {
            s.updateValue(scenarioName);
        }

        System.out.println("Mevcut Hisse Kodlari:");
        for (Stock s : stocks) {
            System.out.println("- " + s.getTicker());
        }

        Scanner input = new Scanner(System.in);
        System.out.print("\nTahmin almak istediginiz hisse kodu: ");
        String userTicker = input.nextLine().trim();

        for (Stock s : stocks) {
            if (s.getTicker().equalsIgnoreCase(userTicker)) {

                int prediction = ai.predict(s.getLastValues());

                System.out.println(
                        "\n" + s.getTicker() + " icin tahmin edilen gelecek deger: "
                                + prediction
                );
                break;
            }
        }

        input.close();
    }
}
