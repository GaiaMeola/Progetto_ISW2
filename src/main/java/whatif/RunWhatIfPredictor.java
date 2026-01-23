package whatif;

import util.Configuration;


public class RunWhatIfPredictor {

    private static final String BASE_PATH = "whatif/";

    public static void main(String[] args) {

        try {
            Configuration.logger.info("INIZIO: Predizione What-If");

            String project = Configuration.getProjectName().toLowerCase();
            String datasetAPath = Configuration.getOutputArffPath();
            String datasetBplusPath = BASE_PATH + project + "_Bplus.csv";
            String datasetBPath = BASE_PATH + project + "_B.csv";
            String datasetCPath = BASE_PATH + project + "_C.csv";
            String outputSummaryCsv = BASE_PATH + project + "_summary.csv";

            WhatIfPredictor.runPrediction(
                    datasetAPath,
                    datasetBplusPath,
                    datasetBPath,
                    datasetCPath,
                    outputSummaryCsv
            );

            Configuration.logger.info("FINE: Predizione completata");
        } catch (Exception e) {
            Configuration.logger.severe("Errore nella predizione What-If: " + e.getMessage());
        }
    }
}
