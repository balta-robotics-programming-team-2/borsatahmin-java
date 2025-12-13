### BIST Analyzer (borsatahmin-java)

An experimental Java application that downloads market data for Borsa Istanbul (BIST) tickers, computes technical indicators, and runs basic machine‑learning models (LSTM and XGBoost) to provide a consolidated analysis report per ticker.

This project is for educational purposes only. It is not financial advice.

#### Key Features
- Fetches historical price data via Yahoo Finance
- Computes technical indicators using TA4J
- Runs example ML models:
  - LSTM (DeepLearning4J)
  - XGBoost (XGBoost4J)
- Analyzes multiple tickers in one run and prints a summary to the console
- Caches network/data resources to speed up subsequent runs

#### Tech Stack
- Java 17
- Gradle (Kotlin DSL)
- YahooFinanceAPI
- TA4J
- DeepLearning4J (DL4J) + ND4J native backend
- XGBoost4J
- SLF4J Simple for logging

#### Project Layout
- `src/main/java/com/balta/bistanalyzer/App.java` — Entry point; orchestrates multi‑ticker analysis and runtime.
- `src/main/java/com/balta/bistanalyzer/Analyzer.java` — Performs the end‑to‑end analysis per ticker and aggregates detailed results.
- `src/main/java/com/balta/bistanalyzer/DataDownloader.java` — Retrieves historical OHLCV data (Yahoo Finance).
- `src/main/java/com/balta/bistanalyzer/Technicals.java` — Builds TA4J indicators and related utilities.
- `src/main/java/com/balta/bistanalyzer/CacheManager.java` — Simple cache lifecycle and shutdown hook management.
- `src/main/java/com/balta/bistanalyzer/Tickers.java` — Central list of BIST tickers to analyze.
- `src/main/java/com/balta/bistanalyzer/models/LstmModel.java` — Sample LSTM model scaffolding.
- `src/main/java/com/balta/bistanalyzer/models/XGBoostModel.java` — Sample XGBoost model scaffolding.

#### Requirements
- JDK 17+
- Internet access (for fetching data from Yahoo Finance)
- Windows/macOS/Linux

Gradle wrapper is included; no global Gradle installation required.

#### Build & Run
Using the Gradle Wrapper from the project root:

```
# Windows (PowerShell or CMD)
gradlew.bat run

# macOS/Linux
./gradlew run
```

The application’s main class is `com.balta.bistanalyzer.App` (configured in `build.gradle.kts`).

To produce build artifacts:

```
# Windows
gradlew.bat build

# macOS/Linux
./gradlew build
```

This generates outputs under `build/`. A distributable application can be created via:

```
# Windows
gradlew.bat installDist

# macOS/Linux
./gradlew installDist
```

Then run the installed distribution (adjust path for your OS):

```
build/install/borsatahmin-java-team/bin/borsatahmin-java
```

#### Configuring Tickers
By default, the app reads tickers from `Tickers.ALL` and analyzes them all. To change the list, edit:

```
src/main/java/com/balta/bistanalyzer/Tickers.java
```

and adjust the `ALL` collection accordingly. Command‑line argument support is not enabled by default.

#### Output
The app prints progress and a final summary to the console, including:
- Number of analyzed tickers
- Per‑ticker analysis results (scored/recommended by implemented logic)
- Total runtime

Example runtime messages are in Turkish (e.g., “Toplam süre sayacı başlatıldı…”). You can internationalize these messages as needed.

#### Notes & Limitations
- Yahoo Finance data may be rate‑limited or temporarily unavailable.
- Models provided are templates/scaffolding; tune, validate, and back‑test before any practical use.
- Caching behavior depends on `CacheManager` implementation; delete cache if data becomes stale.

#### Troubleshooting
- Ensure JDK 17 is active: `java -version` should report 17.x.
- If native backends fail to load (ND4J/DL4J), make sure your OS/architecture is supported and up‑to‑date.
- For dependency resolution issues, try `gradlew.bat --refresh-dependencies` (Windows) or `./gradlew --refresh-dependencies` (macOS/Linux).

#### License
No license specified. If you intend to share or use this code publicly, please add a license (e.g., MIT, Apache‑2.0) to this repository.
