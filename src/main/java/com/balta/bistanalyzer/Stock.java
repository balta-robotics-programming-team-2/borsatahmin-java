package com.balta.bistanalyzer;

import java.util.Random;

public class Stock {

    private String companyName;
    private String ticker;
    private int[] lastValues = new int[5];
    private Random random = new Random();

    public Stock(String companyName, String ticker, int startingValue) {
        this.companyName = companyName;
        this.ticker = ticker;

        for (int i = 0; i < 5; i++) {
            lastValues[i] = startingValue;
        }
    }

    public void updateValue(String scenario) {
        int change = 0;

        switch (scenario) {
            case "HIGH_UP":
                change = random.nextInt(15) - 3;
                break;
            case "UP":
                change = random.nextInt(6) - 1;
                break;
            case "DOWN":
                change = -(random.nextInt(6) + 1);
                break;
            case "HIGH_DOWN":
                change = -(random.nextInt(15) + 3);
                break;
        }

        for (int i = lastValues.length - 1; i > 0; i--) {
            lastValues[i] = lastValues[i - 1];
        }

        lastValues[0] += change;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getTicker() {
        return ticker;
    }

    public int[] getLastValues() {
        return lastValues;
    }
}
