package org.agmip.ui.acebgen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.agmip.ace.AceDataset;
import org.agmip.ace.AceExperiment;
import org.agmip.ace.AceSoil;
import org.agmip.ace.AceWeather;
import org.agmip.ace.io.AceParser;
import org.agmip.common.Functions;
import static org.agmip.common.Functions.getStackTrace;
import org.agmip.translators.dssat.DssatControllerInput;
import org.agmip.util.JSONAdapter;
import static org.agmip.util.JSONAdapter.*;
import org.agmip.util.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Meng Zhang
 */
public class AcebGenCmd {

    public enum DomeMode {

        NONE, FIELD, STRATEGY
    }
    
    private static Logger LOG = LoggerFactory.getLogger(AcebGenCmd.class);
    private DomeMode mode = DomeMode.NONE;
    private String convertPath = null;
    private String linkPath = null;
    private String fieldPath = null;
    private String strategyPath = null;
    private String outputPath = null;
    private boolean helpFlg = false;
    private Properties versionProperties = new Properties();
    private String acebgenVersion = "";
    private boolean isFromCRAFT = false;

    public AcebGenCmd() {
        try {
            InputStream versionFile = getClass().getClassLoader().getResourceAsStream("product.properties");
            versionProperties.load(versionFile);
            versionFile.close();
            StringBuilder qv = new StringBuilder();
            String buildType = versionProperties.getProperty("product.buildtype").toString();
            qv.append("Version ");
            qv.append(versionProperties.getProperty("product.version").toString());
            qv.append("-").append(versionProperties.getProperty("product.buildversion").toString());
            qv.append("(").append(buildType).append(")");
            if (buildType.equals("dev")) {
                qv.append(" [").append(versionProperties.getProperty("product.buildts")).append("]");
            }
            acebgenVersion = qv.toString();
        } catch (IOException ex) {
            LOG.error("Unable to load version information, version will be blank.");
        }
    }

    public void run(String[] args) {

        LOG.info("AcebGen {} lauched with JAVA {} under OS {}", acebgenVersion, System.getProperty("java.runtime.version"), System.getProperty("os.name"));
        readCommand(args);
        if (helpFlg) {
            printHelp();
            return;
        } else if (!validate()) {
            LOG.info("Type -h or -help for arguments info");
            return;
        } else {
            argsInfo();
        }

        LOG.info("Starting translation job");
        try {
            startTranslation();
        } catch (Exception ex) {
            LOG.error(getStackTrace(ex));
        }

    }

    private void readCommand(String[] args) {
        int i = 0;
        int pathNum = 1;
        while (i < args.length && args[i].startsWith("-")) {
            if (args[i].equalsIgnoreCase("-n") || args[i].equalsIgnoreCase("-none")) {
                mode = DomeMode.NONE;
                pathNum = 1;
            } else if (args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("-field")) {
                mode = DomeMode.FIELD;
                pathNum = 3;
            } else if (args[i].equalsIgnoreCase("-s") || args[i].equalsIgnoreCase("-strategy")) {
                mode = DomeMode.STRATEGY;
                pathNum = 4;
            } else if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("-help")) {
                helpFlg = true;
                return;
            } else {
                LOG.warn("\"{}\" is an unrecognized arguments, will be ignored.");
            }
            i++;
        }
        try {
            if (pathNum >= 1) {
                convertPath = args[i++];
            }
            if (pathNum >= 2) {
                linkPath = args[i++].trim();
            }
            if (pathNum >= 3) {
                fieldPath = args[i++];
            }
            if (pathNum >= 4) {
                strategyPath = args[i++];
            }
            if (i < args.length) {
                outputPath = args[i];
            } else {
                try {
                    outputPath = new File(convertPath).getCanonicalFile().getParent();
                } catch (IOException ex) {
                    outputPath = null;
                    LOG.error(getStackTrace(ex));
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            LOG.error("Path arguments are not enough for selected dome mode");
        }
    }

    private boolean validate() {

        if (convertPath == null || !new File(convertPath).exists()) {
            LOG.warn("convert_path is invalid : " + convertPath);
            return false;
        } else if (!isValidPath(outputPath, false)) {
            LOG.warn("output_path is invalid : " + outputPath);
            return false;
        }

        if (mode.equals(DomeMode.NONE)) {
        } else if (mode.equals(DomeMode.FIELD)) {
            if (!linkPath.equals("") && !isValidPath(linkPath, true)) {
                LOG.warn("link_path is invalid : " + linkPath);
                return false;
            } else if (!isValidPath(fieldPath, true)) {
                LOG.warn("field_path is invalid : " + fieldPath);
                return false;
            }
        } else if (mode.equals(DomeMode.STRATEGY)) {
            if (!linkPath.equals("") && !isValidPath(linkPath, true)) {
                LOG.warn("link_path is invalid : " + linkPath);
                return false;
            } else if (!isValidPath(fieldPath, true)) {
                LOG.warn("field_path is invalid : " + fieldPath);
                return false;
            } else if (!isValidPath(strategyPath, true)) {
                LOG.warn("strategy_path is invalid : " + strategyPath);
                return false;
            }
        } else {
            LOG.warn("Unsupported mode option : " + mode);
            return false;
        }
        
        isFromCRAFT = new File(convertPath).isDirectory();
        if (outputPath != null) {
            File dir = new File(outputPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }

        return true;
    }

    private boolean isValidPath(String path, boolean isFile) {
        if (path == null) {
            return false;
        } else {
            File f = new File(path);
            if (isFile) {
                return f.isFile();
            } else {
                return !path.matches(".*\\.\\w+$");
            }
        }
    }

    private void startTranslation() throws Exception {
        LOG.info("Importing data...");
        if (isFromCRAFT) {
            DssatControllerInput in = new DssatControllerInput();
            HashMap data = in.readFileFromCRAFT(convertPath);
            
            if (!mode.equals(DomeMode.NONE)) {
                readDome(data, mode.toString().toLowerCase());
            }
        } else if (convertPath.endsWith(".json")) {
            try {
                // Load the JSON representation into memory and send it down the line.
                String json = new Scanner(new File(convertPath), "UTF-8").useDelimiter("\\A").next();
                HashMap data = fromJSON(json);

                // Check if the data has been applied with DOME.
                boolean isDomeApplied = false;
                ArrayList<HashMap> exps = MapUtil.getObjectOr(data, "experiments", new ArrayList());
                for (HashMap exp : exps) {
                    if (MapUtil.getValueOr(exp, "dome_applied", "").equals("Y")) {
                        isDomeApplied = true;
                        break;
                    }
                }
                if (exps.isEmpty()) {
                    ArrayList<HashMap> soils = MapUtil.getObjectOr(data, "soils", new ArrayList());
                    ArrayList<HashMap> weathers = MapUtil.getObjectOr(data, "weathers", new ArrayList());
                    for (HashMap soil : soils) {
                        if (MapUtil.getValueOr(soil, "dome_applied", "").equals("Y")) {
                            isDomeApplied = true;
                            break;
                        }
                    }
                    if (!isDomeApplied) {
                        for (HashMap wth : weathers) {
                            if (MapUtil.getValueOr(wth, "dome_applied", "").equals("Y")) {
                                isDomeApplied = true;
                                break;
                            }
                        }
                    }
                }
                // If it has not been applied with DOME, then dump to ACEB
                if (!isDomeApplied) {
                    dumpToAceb(data);
                } else {
                    LOG.warn("Because the imported data set in JSON format has already been applied with DOME, no ACEB data file will be generated.");
                }
                if (!mode.equals(DomeMode.NONE)) {
                    readDome(data, mode.toString().toLowerCase());
                }
            } catch (Exception ex) {
                LOG.error(getStackTrace(ex));
            }
        } else if (convertPath.endsWith(".aceb")) {
            try {
                // Load the ACE Binay file into memory and transform it to old JSON format and send it down the line.
                AceDataset ace = AceParser.parseACEB(new File(convertPath));
                ace.linkDataset();
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                AceGenerator.generate(baos, acebData, false);
//                HashMap data = fromJSON(baos.toString());
//                baos.close();
                
                HashMap data = new HashMap();
                ArrayList<HashMap> arr;
                // Experiments
                arr = new ArrayList();
                for (AceExperiment exp : ace.getExperiments()) {
                    arr.add(JSONAdapter.fromJSON(new String(exp.rebuildComponent())));
                }
                if (!arr.isEmpty()) {
                    data.put("experiments", arr);
                }
                // Soils
                arr = new ArrayList();
                for (AceSoil soil : ace.getSoils()) {
                    arr.add(JSONAdapter.fromJSON(new String(soil.rebuildComponent())));
                }
                if (!arr.isEmpty()) {
                    data.put("soils", arr);
                }
                // Weathers
                arr = new ArrayList();
                for (AceWeather wth : ace.getWeathers()) {
                    arr.add(JSONAdapter.fromJSON(new String(wth.rebuildComponent())));
                }
                if (!arr.isEmpty()) {
                    data.put("weathers", arr);
                }
                ace = null;

                if (!mode.equals(DomeMode.NONE)) {
                    readDome(data, mode.toString().toLowerCase());
                }
            } catch (Exception ex) {
                LOG.error(getStackTrace(ex));
            }
        } else {
            TranslateFromTask task = new TranslateFromTask(convertPath);
            HashMap data = task.execute();
            if (!data.containsKey("errors")) {
                dumpToAceb(data);
                if (!mode.equals(DomeMode.NONE)) {
                    readDome(data, mode.toString().toLowerCase());
                }
            } else {
                LOG.error((String) data.get("errors"));
            }
        }
        LOG.info("=== Completed translation job ===");
    }

    private void dumpToAceb(HashMap map) {
        dumpToAceb(map, false);
    }

    private void dumpToAceb(HashMap map, final boolean isDome) {

        if (!isDome) {
            generateId(map);
        }
        final String fileName = new File(convertPath).getName();
//        final HashMap result = (HashMap) map.get("domeoutput");
        boolean isSkipped = false;
        boolean isSkippedForLink = false;
        if (map == null || (!isDome && convertPath.toUpperCase().endsWith(".ACEB"))) {
            return;
        } else if (isDome && fieldPath.toUpperCase().endsWith(".ACEB") && strategyPath.toUpperCase().endsWith(".ACEB")) {
            isSkipped = true;
        }
        if (linkPath != null && linkPath.toUpperCase().endsWith(".ACEB")) {
            isSkippedForLink = true;
        }
        if (isSkipped) {
            LOG.info("Skip generating ACE Baniry file for DOMEs applied for {} ...", fileName);
        } else if (isDome) {
            LOG.info("Generate ACE Baniry file for DOMEs applied for {} ...", fileName);
        } else {
            LOG.info("Generate ACE Baniry file for {} ...", fileName);
        }
        if (isSkippedForLink) {
            LOG.info("Skip generating ACE Baniry file for linkage information used for {} ...", fileName);
        }
        DumpToAceb task = new DumpToAceb(convertPath, outputPath, map, isDome, isSkipped, isSkippedForLink);
        try {
            task.execute();
        } catch (IOException ex) {
            LOG.error(Functions.getStackTrace(ex));
        }
    }

    private void generateId(HashMap data) {
        try {
            String json = toJSON(data);
            data.clear();
            AceDataset ace = AceParser.parse(json);
            ace.linkDataset();
            ArrayList<HashMap> arr;
            // Experiments
            arr = new ArrayList();
            for (AceExperiment exp : ace.getExperiments()) {
                arr.add(JSONAdapter.fromJSON(new String(exp.rebuildComponent())));
            }
            if (!arr.isEmpty()) {
                data.put("experiments", arr);
            }
            // Soils
            arr = new ArrayList();
            for (AceSoil soil : ace.getSoils()) {
                arr.add(JSONAdapter.fromJSON(new String(soil.rebuildComponent())));
            }
            if (!arr.isEmpty()) {
                data.put("soils", arr);
            }
            // Weathers
            arr = new ArrayList();
            for (AceWeather wth : ace.getWeathers()) {
                arr.add(JSONAdapter.fromJSON(new String(wth.rebuildComponent())));
            }
            if (!arr.isEmpty()) {
                data.put("weathers", arr);
            }
        } catch (IOException e) {
            LOG.warn(getStackTrace(e));
        }
    }

    private void readDome(HashMap map, String mode) {
        if (linkPath.toLowerCase().endsWith("aceb")) {
            LOG.info("Skip getting the linkage information since it is already in ACEB format.");
        } else {
            LOG.info("Getting the linkage information between experiments and DOME...");
        }
        ApplyDomeTask task = new ApplyDomeTask(linkPath, fieldPath, strategyPath, mode, map, isAutoDomeApply());
        HashMap data = task.execute();
        if (!data.containsKey("errors")) {
            //LOG.error("Domeoutput: {}", data.get("domeoutput"));
            dumpToAceb(data, true);
        } else {
            LOG.error((String) data.get("errors"));
        }
    }

    private void printHelp() {
        System.out.println("\nThe arguments format : <dome_mode_option> <convert_path> <link_path> <field_path> <strategy_path> <output_path>");
        System.out.println("\t<dome_mode_option>");
        System.out.println("\t\t-n | -none\tRaw Data Only, Default");
        System.out.println("\t\t-f | -filed\tField Overlay, will require Field Overlay File");
        System.out.println("\t\t-s | -strategy\tSeasonal Strategy, will require both Field Overlay and Strategy File");
        System.out.println("\t<convert_path>");
        System.out.println("\t\tThe path for file to be converted");
        System.out.println("\t<link_path>");
        System.out.println("\t\tThe path for file to be used for link dome command to data set");
        System.out.println("\t\tIf not intend to provide link file, please set it as \"\"");
        System.out.println("\t<field_path>");
        System.out.println("\t\tThe path for file to be used for field overlay");
        System.out.println("\t<strategy_path>");
        System.out.println("\t\tThe path for file to be used for strategy");
        System.out.println("\t<output_path>");
        System.out.println("\t\tThe path for output.");
        System.out.println("\t\t* If not provided, will use convert_path");
        System.out.println("\n");
    }

    private void argsInfo() {
        LOG.info("Dome mode: \t" + mode);
        LOG.info("convertPath:\t" + convertPath);
        LOG.info("linkPath: \t" + linkPath);
        LOG.info("fieldPath: \t" + fieldPath);
        LOG.info("strategyPath:\t" + strategyPath);
        LOG.info("outputPath:\t" + outputPath);
    }

    private boolean isAutoDomeApply() {
        File convertFile = new File(convertPath);
        String fileName = convertFile.getName().toLowerCase();
        boolean autoApply = false;
        if (!linkPath.equals("")) {
            autoApply = false;
        } else if (isFromCRAFT) {
            autoApply = true;
        } else if (fileName.endsWith(".zip")) {
            try {
                ZipFile zf = new ZipFile(convertFile);
                Enumeration<? extends ZipEntry> e = zf.entries();
                while (e.hasMoreElements()) {
                    ZipEntry ze = (ZipEntry) e.nextElement();
                    String zeName = ze.getName().toLowerCase();
                    if (!zeName.endsWith(".csv")) {
                        autoApply = true;
                    } else {
                        autoApply = false;
                        break;
                    }
                }
                zf.close();
            } catch (IOException ex) {
            }

        } else if (!fileName.endsWith(".csv")) {
            autoApply = true;
        }
        return autoApply;
    }
}
