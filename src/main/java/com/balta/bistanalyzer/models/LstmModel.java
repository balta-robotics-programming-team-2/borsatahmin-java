package com.balta.bistanalyzer.models;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.recurrent.LastTimeStep;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.indexing.NDArrayIndex;

/**
 * Simple wrapper that creates, trains and predicts with an LSTM using DL4J.
 * Expects numeric sequences shaped (samples, seqLen, features).
 * We perform last-time-step binary classification with sigmoid output.
 */
public class LstmModel {

    public MultiLayerNetwork buildModel(int seqLen, int numFeatures, int lstmUnits) {
        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(42)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Adam(0.001));

        MultiLayerConfiguration conf = builder.list()
                .layer(new LSTM.Builder()
                        .nIn(numFeatures)
                        .nOut(lstmUnits)
                        .activation(Activation.TANH)
                        .dropOut(0.3)
                        .build())
                // Wrap the second LSTM with LastTimeStep to reduce time dimension
                .layer(new LastTimeStep(
                        new LSTM.Builder()
                                .nOut(lstmUnits)
                                .activation(Activation.TANH)
                                .dropOut(0.3)
                                .build()
                ))
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.XENT)
                        .activation(Activation.SIGMOID)
                        .nIn(lstmUnits)
                        .nOut(1)
                        .build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }

    public double trainAndPredict(double[][][] X, double[] y, int epochs, int batchSize) {
        int samples = X.length;
        int seqLen = X[0].length;
        int features = X[0][0].length;

        // DL4J expects shape [miniBatchSize, features, timesteps] for RNNs
        INDArray input = Nd4j.create(new int[]{samples, features, seqLen});
        // Labels for last-step classification should be 2D: [batch, 1]
        INDArray labels = Nd4j.create(new int[]{samples, 1});

        for (int i = 0; i < samples; i++) {
            for (int t = 0; t < seqLen; t++) {
                for (int f = 0; f < features; f++) {
                    input.putScalar(new int[]{i, f, t}, X[i][t][f]);
                }
            }
            labels.putScalar(new int[]{i, 0}, y[i]);
        }

        MultiLayerNetwork net = buildModel(seqLen, features, 32);
        DataSet ds = new DataSet(input, labels);
        for (int epoch = 0; epoch < epochs; epoch++) {
            net.fit(ds);
        }

        // Predict using last sequence (take last sample) -> keep 3D [1, features, seqLen]
        INDArray lastInput = input.get(
                NDArrayIndex.point(samples - 1),
                NDArrayIndex.all(),
                NDArrayIndex.all()
        ).reshape(1, features, seqLen);
        INDArray out = net.output(lastInput);
        return out.getDouble(0);
    }
}