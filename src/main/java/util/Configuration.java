package util;

import java.util.logging.Logger;

public class Configuration {

    private Configuration() {}

    public static final boolean BASIC_DEBUG = true;
    public static final boolean ACTIVATE_LOG = false;
    public static final boolean LABELING_DEBUG = false;

    public static final Logger logger = Logger.getLogger(Configuration.class.getName());

    public static final String PROJECT1_NAME = ConfigurationLoader.get("project1.name");
    public static final String PROJECT1_PATH = ConfigurationLoader.get("project1.path");
    public static final String OUTPUT_CSV1_PATH = ConfigurationLoader.get("project1.output_csv");
    public static final String OUTPUT_ARFF1_PATH = ConfigurationLoader.get("project1.output_arff");
    public static final String REDUCED_OUTPUT_ARFF1_PATH = ConfigurationLoader.get("project1.reduced_output_arff");
    public static final String PROJECT1_SUBSTRING = "bookkeeper/";
    public static final String PROJECT1_COLUMN = "Bookkeeper";
    public static final String DEBUG_SAMPLED_METHODS_PATH1 = ConfigurationLoader.get("debug.sampled_methods_path1");
    public static final String DEBUG_BUGGY_METHODS_PATH1 = ConfigurationLoader.get("debug.buggy_methods_path1");

    public static final String PROJECT2_NAME = ConfigurationLoader.get("project2.name");
    public static final String PROJECT2_PATH = ConfigurationLoader.get("project2.path");
    public static final String OUTPUT_CSV2_PATH = ConfigurationLoader.get("project2.output_csv");
    public static final String OUTPUT_ARFF2_PATH = ConfigurationLoader.get("project2.output_arff");
    public static final String REDUCED_OUTPUT_ARFF2_PATH = ConfigurationLoader.get("project2.reduced_output_arff");
    public static final String PROJECT2_SUBSTRING = "openjpa/";
    public static final String PROJECT2_COLUMN = "Openjpa";
    public static final String DEBUG_SAMPLED_METHODS_PATH2 = ConfigurationLoader.get("debug.sampled_methods_path2");
    public static final String DEBUG_BUGGY_METHODS_PATH2 = ConfigurationLoader.get("debug.buggy_methods_path2");

    public static final String DEBUG_TICKET_COMMITS_PATH1 = ConfigurationLoader.get("debug.ticket_commits_path1");
    public static final String DEBUG_TICKET_COMMITS_PATH2 = ConfigurationLoader.get("debug.ticket_commits_path2");
    public static final String DEBUG_VERSION_INFO_PATH1 = ConfigurationLoader.get("debug.version_info_path1");
    public static final String DEBUG_VERSION_INFO_PATH2 = ConfigurationLoader.get("debug.version_info_path2");
    public static final String DEBUG_TICKET_PATH1 = ConfigurationLoader.get("debug.ticket_path1");
    public static final String DEBUG_TICKET_PATH2 = ConfigurationLoader.get("debug.ticket_path2");
    public static final String DEBUG_COMMIT_PATH1 = ConfigurationLoader.get("debug.commit_path1");
    public static final String DEBUG_COMMIT_PATH2 = ConfigurationLoader.get("debug.commit_path2");


    public static final ProjectType SELECTED_PROJECT = ProjectType.BOOKKEEPER;

    public static String getProjectName() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? PROJECT1_NAME : PROJECT2_NAME;
    }

    public static String getProjectPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? PROJECT1_PATH : PROJECT2_PATH;
    }

    public static String getOutputCsvPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? OUTPUT_CSV1_PATH : OUTPUT_CSV2_PATH;
    }

    public static String getOutputArffPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? OUTPUT_ARFF1_PATH : OUTPUT_ARFF2_PATH;
    }

    public static String getReducedOutputArffPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? REDUCED_OUTPUT_ARFF1_PATH : REDUCED_OUTPUT_ARFF2_PATH;
    }

    public static String getDebugSampledMethodsPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? DEBUG_SAMPLED_METHODS_PATH1 : DEBUG_SAMPLED_METHODS_PATH2;
    }

    public static String getProjectSubstring() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? PROJECT1_SUBSTRING : PROJECT2_SUBSTRING;
    }

    public static String getProjectColumn() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? PROJECT1_COLUMN : PROJECT2_COLUMN ;
    }

    public static String getDebugBuggyMethods() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? DEBUG_BUGGY_METHODS_PATH1 : DEBUG_BUGGY_METHODS_PATH2;
    }

    public static String getDebugTicketCommitsPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER)
                ? DEBUG_TICKET_COMMITS_PATH1
                : DEBUG_TICKET_COMMITS_PATH2;
    }

    public static String getDebugVersionInfoPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? DEBUG_VERSION_INFO_PATH1 : DEBUG_VERSION_INFO_PATH2;
    }

    public static String getDebugTicketPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? DEBUG_TICKET_PATH1 : DEBUG_TICKET_PATH2;
    }

    public static String getCommitDebugCsvPath() {
        return (SELECTED_PROJECT == ProjectType.BOOKKEEPER) ? DEBUG_COMMIT_PATH1 : DEBUG_COMMIT_PATH2;
    }

    public static String getCorrelationCsvPath() {
        return "ml_results/" + getProjectName().toLowerCase() + "_feature_correlation.csv";
    }


}
