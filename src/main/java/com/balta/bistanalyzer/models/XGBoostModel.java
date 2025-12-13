package com.balta.bistanalyzer.models;

import ml.dmlc.xgboost4j.java.*;
import java.util.*;

/**
 * Thin wrapper over XGBoost4J classifier. Trains on feature arrays and predicts a probability.
 */
public class XGBoostModel {

    public double trainAndPredict(double[][] X, double[] y) throws XGBoostError {
        int n = X.length;
        int m = X[0].length;

        // build DMatrix
        float[] flat = new float[n * m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                flat[i * m + j] = (float) X[i][j];
            }
        }
        DMatrix trainMat = new DMatrix(flat, n, m, Float.NaN);

        trainMat.setLabel(toFloatArray(y));

        Map<String, Object> params = new HashMap<>();
        params.put("eta", 0.1);
        params.put("max_depth", 4);
        params.put("objective", "binary:logistic");
        params.put("verbosity", 0);
        params.put("nthread", 1);

        int nround = Math.min(80, Math.max(30, n / 5));
        Booster booster = XGBoost.train(trainMat, params, nround, new HashMap<>(), null, null);

        // predict last row
        DMatrix last = new DMatrix(Arrays.copyOfRange(flat, (n - 1) * m, n * m), 1, m, Float.NaN);
        float[][] preds = booster.predict(last);
        return preds[0][0];
    }

    private float[] toFloatArray(double[] arr) {
        float[] f = new float[arr.length];
        for (int i = 0; i < arr.length; i++) f[i] = (float) arr[i];
        return f;
    }
}
