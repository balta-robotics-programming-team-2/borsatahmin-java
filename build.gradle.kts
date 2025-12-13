plugins {
    java
    application
}

group = "com.balta"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Yahoo finance data
    implementation("com.yahoofinance-api:YahooFinanceAPI:3.15.0")

    // TA library for technical indicators
    implementation("org.ta4j:ta4j-core:0.14")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Deeplearning4j for LSTM (includes ND4J native backend)
    implementation("org.deeplearning4j:deeplearning4j-core:1.0.0-M2.1")
    implementation("org.nd4j:nd4j-native-platform:1.0.0-M2.1")

    // XGBoost4J
    implementation("ml.dmlc:xgboost4j_2.13:3.1.1")
    implementation("ml.dmlc:xgboost4j-spark_2.13:3.1.1")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Apache Commons
    implementation("org.apache.commons:commons-math3:3.6.1")
}

application {
    mainClass.set("com.balta.bistanalyzer.App")
}
