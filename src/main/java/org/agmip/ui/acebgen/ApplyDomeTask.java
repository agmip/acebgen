package org.agmip.ui.acebgen;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.agmip.dome.DomeUtil;
import org.agmip.translators.csv.DomeInput;
import org.agmip.util.MapUtil;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplyDomeTask {

    private static Logger log = LoggerFactory.getLogger(ApplyDomeTask.class);
    private HashMap<String, HashMap<String, Object>> ovlDomes = new HashMap<String, HashMap<String, Object>>();
    private HashMap<String, HashMap<String, Object>> stgDomes = new HashMap<String, HashMap<String, Object>>();
    private HashMap<String, Object> linkDomes = new HashMap<String, Object>();
    private HashMap<String, String> ovlLinks = new HashMap<String, String>();
    private HashMap<String, String> stgLinks = new HashMap<String, String>();
    private HashMap<String, String> orgOvlLinks = new HashMap<String, String>();
    private HashMap<String, String> orgStgLinks = new HashMap<String, String>();
//    private HashMap<String, ArrayList<String>> wthLinks = new HashMap<String, ArrayList<String>>();
//    private HashMap<String, ArrayList<String>> soilLinks = new HashMap<String, ArrayList<String>>();
    private HashMap source;
    private String mode;
    private boolean autoApply;

    public ApplyDomeTask(String linkFile, String fieldFile, String strategyFile, String mode, HashMap m, boolean autoApply) {
        this.source = m;
        this.mode = mode;
        this.autoApply = autoApply;
        // Setup the domes here.
        loadDomeLinkFile(linkFile);
        log.debug("link csv: {}", ovlLinks);

        if (mode.equals("strategy")) {
            loadDomeFile(strategyFile, stgDomes);
        }
        loadDomeFile(fieldFile, ovlDomes);
    }

    private void loadDomeLinkFile(String fileName) {
        String fileNameTest = fileName.toUpperCase();
        log.debug("Loading LINK file: {}", fileName);
        linkDomes = null;

        try {
            if (fileNameTest.endsWith(".CSV")) {
                log.debug("Entering single CSV file DOME handling");
                DomeInput translator = new DomeInput();
                linkDomes = (HashMap<String, Object>) translator.readFile(fileName);
            }

            if (linkDomes != null) {
                log.debug("link info: {}", linkDomes.toString());
                try {
                    if (!linkDomes.isEmpty()) {
                        if (linkDomes.containsKey("link_overlay")) {
                            ovlLinks = (HashMap<String, String>) linkDomes.get("link_overlay");
                        }
                        if (linkDomes.containsKey("link_stragty")) {
                            stgLinks = (HashMap<String, String>) linkDomes.get("link_stragty");
                        }
                    }
                } catch (Exception ex) {
                    log.error("Error processing DOME file: {}", ex.getMessage());
                    HashMap<String, Object> d = new HashMap<String, Object>();
                    d.put("errors", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Error processing DOME file: {}", ex.getMessage());
            HashMap<String, Object> d = new HashMap<String, Object>();
            d.put("errors", ex.getMessage());
        }
    }

    private String getLinkIds(String domeType, HashMap entry) {
        String exname = MapUtil.getValueOr(entry, "exname", "");
        String wst_id = MapUtil.getValueOr(entry, "wst_id", "");
        String soil_id = MapUtil.getValueOr(entry, "soil_id", "");
        String linkIdsExp = getLinkIds(domeType, "EXNAME", exname);
        String linkIdsWst = getLinkIds(domeType, "WST_ID", wst_id);
        String linkIdsSoil = getLinkIds(domeType, "SOIL_ID", soil_id);
        String ret = "";
        if (!linkIdsExp.equals("")) {
            ret += linkIdsExp + "|";
        }
        if (!linkIdsWst.equals("")) {
            ret += linkIdsWst + "|";
        }
        if (!linkIdsSoil.equals("")) {
            ret += linkIdsSoil;
        }
        if (ret.endsWith("|")) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }

    private String getLinkIds(String domeType, String idType, String id) {
        HashMap<String, String> links;
        if (domeType.equals("strategy")) {
            links = stgLinks;
        } else if (domeType.equals("overlay")) {
            links = ovlLinks;
        } else {
            return "";
        }
        if (links.isEmpty() || id.equals("")) {
            return "";
        }
        String linkIds = "";
        ArrayList<String> altLinkIds = new ArrayList();
        altLinkIds.add(idType + "_ALL");
        if (id.matches(".+_\\d+$") && domeType.equals("overlay")) {
            altLinkIds.add(idType + "_" + id.replaceAll("_\\d+$", ""));
        } else if (id.matches(".+_\\d+__\\d+$") && domeType.equals("strategy")) {
            altLinkIds.add(idType + "_" + id.replaceAll("_\\d+__\\d+$", ""));
        }
        altLinkIds.add(idType + "_" + id);
        for (String linkId : altLinkIds) {
            if (links.containsKey(linkId)) {
                linkIds += links.get(linkId) + "|";
            }
        }
        if (linkIds.endsWith("|")) {
            linkIds = linkIds.substring(0, linkIds.length() - 1);
        }
        return linkIds;
    }
    
    private void setOriLinkIds(HashMap entry, String domeIds, String domeType) {
        HashMap<String, String> links;
        if (domeType.equals("strategy")) {
            links = orgStgLinks;
        } else if (domeType.equals("overlay")) {
            links = orgOvlLinks;
        } else {
            return;
        }
        String exname = MapUtil.getValueOr(entry, "exname", "");
        if (!exname.equals("")) {
            links.put("EXNAME_" + exname, domeIds);
        } else {
            String soil_id = MapUtil.getValueOr(entry, "soil_id", "");
            String wst_id = MapUtil.getValueOr(entry, "wst_id", "");
            if (!soil_id.equals("")) {
                links.put("SOIL_ID_" + soil_id, domeIds);
            } else if (!wst_id.equals("")) {
                links.put("WST_ID_" + wst_id, domeIds);
            }
        }
    }

    private void loadDomeFile(String fileName, HashMap<String, HashMap<String, Object>> domes) {
        String fileNameTest = fileName.toUpperCase();

        log.info("Loading DOME file: {}", fileName);

        if (fileNameTest.endsWith(".ZIP")) {
            log.debug("Entering Zip file handling");
            ZipFile z = null;
            try {
                z = new ZipFile(fileName);
                Enumeration entries = z.entries();
                while (entries.hasMoreElements()) {
                    // Do we handle nested zips? Not yet.
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    File zipFileName = new File(entry.getName());
                    if (zipFileName.getName().toLowerCase().endsWith(".csv") && !zipFileName.getName().startsWith(".")) {
                        log.debug("Processing file: {}", zipFileName.getName());
                        DomeInput translator = new DomeInput();
                        translator.readCSV(z.getInputStream(entry));
                        HashMap<String, Object> dome = translator.getDome();
                        log.debug("dome info: {}", dome.toString());
                        String domeName = DomeUtil.generateDomeName(dome);
                        if (!domeName.equals("----")) {
                            domes.put(domeName, new HashMap<String, Object>(dome));
                        }
                    }
                }
                z.close();
            } catch (Exception ex) {
                log.error("Error processing DOME file: {}", ex.getMessage());
                HashMap<String, Object> d = new HashMap<String, Object>();
                d.put("errors", ex.getMessage());
            }
        } else if (fileNameTest.endsWith(".CSV")) {
            log.debug("Entering single CSV file DOME handling");
            try {
                DomeInput translator = new DomeInput();
                HashMap<String, Object> dome = (HashMap<String, Object>) translator.readFile(fileName);
                String domeName = DomeUtil.generateDomeName(dome);
                log.debug("Dome name: {}", domeName);
                log.debug("Dome layout: {}", dome.toString());

                domes.put(domeName, dome);
            } catch (Exception ex) {
                log.error("Error processing DOME file: {}", ex.getMessage());
                HashMap<String, Object> d = new HashMap<String, Object>();
                d.put("errors", ex.getMessage());
            }
        } else if (fileNameTest.endsWith(".ACEB")) {
            log.debug("Entering single ACEB file DOME handling");
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = new Scanner(new GZIPInputStream(new FileInputStream(fileName)), "UTF-8").useDelimiter("\\A").next();
                HashMap<String, HashMap<String, Object>> tmp = mapper.readValue(json, new TypeReference<HashMap<String, HashMap<String, Object>>>() {});
//                domes.putAll(tmp);
                for (HashMap dome : tmp.values()) {
                    String domeName = DomeUtil.generateDomeName(dome);
                    if (!domeName.equals("----")) {
                        domes.put(domeName, new HashMap<String, Object>(dome));
                    }
                }
                log.debug("Domes layout: {}", domes.toString());
            } catch (Exception ex) {
                log.error("Error processing DOME file: {}", ex.getMessage());
                HashMap<String, Object> d = new HashMap<String, Object>();
                d.put("errors", ex.getMessage());
            }
        }
    }

    public HashMap<String, Object> execute() {
        // First extract all the domes and put them in a HashMap by DOME_NAME
        // The read the DOME_NAME field of the CSV file
        // Split the DOME_NAME, and then apply sequentially to the HashMap.

        // PLEASE NOTE: This can be a massive undertaking if the source map
        // is really large. Need to find optimization points.

        HashMap<String, Object> output = new HashMap<String, Object>();
        //HashMap<String, ArrayList<HashMap<String, String>>> dome;
        // Load the dome

        if (ovlDomes.isEmpty() && stgDomes.isEmpty()) {
            log.info("No DOME to apply.");
            HashMap<String, Object> d = new HashMap<String, Object>();
            //d.put("domeinfo", new HashMap<String, String>());
            d.put("domeoutput", source);
            return d;
        }

        if (autoApply) {
            HashMap<String, Object> d = new HashMap<String, Object>();
            if (ovlDomes.size() > 1) {
                log.error("Auto-Apply feature only allows one field overlay file per run");
                d.put("errors", "Auto-Apply feature only allows one field overlay file per run");
                return d;
            } else if (stgDomes.size() > 1) {
                log.error("Auto-Apply feature only allows one seasonal strategy file per run");
                d.put("errors", "Auto-Apply feature only allows one seasonal strategy file per run");
                return d;
            }
        }

        // Flatten the data and apply the dome.
        ArrayList<HashMap<String, Object>> flattenedData = MapUtil.flatPack(source);
//        boolean noExpMode = false;
        if (flattenedData.isEmpty()) {
            log.info("No experiment data detected, will try Weather and Soil data only mode");
//            noExpMode = true;
            flattenedData.addAll(MapUtil.getRawPackageContents(source, "soils"));
            flattenedData.addAll(MapUtil.getRawPackageContents(source, "weathers"));
            if (flattenedData.isEmpty()) {
                HashMap<String, Object> d = new HashMap<String, Object>();
                log.error("No data found from input file, no DOME will be applied for data set {}", source.toString());
                d.put("errors", "Loaded raw data is invalid, please check input files");
                return d;
            }
        }

        if (mode.equals("strategy")) {
            log.debug("Domes: {}", stgDomes.toString());
            log.debug("Entering Strategy mode!");

            String stgDomeName = "";
            if (autoApply) {
                for (String domeName : stgDomes.keySet()) {
                    stgDomeName = domeName;
                }
                log.info("Auto apply seasonal strategy: {}", stgDomeName);
            }
            for (HashMap<String, Object> entry : flattenedData) {
                if (autoApply) {
                    entry.put("seasonal_strategy", stgDomeName);
                }
                String domeName = getLinkIds("strategy", entry);
                if (domeName.equals("")) {
                    domeName = MapUtil.getValueOr(entry, "seasonal_strategy", "");
                } else {
                    log.debug("Record seasonal strategy domes from link csv: {}", domeName);
                }

                setOriLinkIds(entry, domeName, "strategy");
            }
            log.debug("=== FINISHED RECORDING STRATEGY DOME ===");
        }

        String ovlDomeName = "";
        if (autoApply) {
            for (String domeName : ovlDomes.keySet()) {
                ovlDomeName = domeName;
            }
            log.info("Auto apply field overlay: {}", ovlDomeName);
        }

        for (HashMap<String, Object> entry : flattenedData) {

            if (autoApply) {
                entry.put("field_overlay", ovlDomeName);
            }
            String domeName = getLinkIds("overlay", entry);
            if (domeName.equals("")) {
                domeName = MapUtil.getValueOr(entry, "field_overlay", "");
            } else {
                log.debug("Apply field overlay domes from link csv: {}", domeName);
            }

            setOriLinkIds(entry, domeName, "overlay");
        }

        if (linkDomes != null && !linkDomes.isEmpty()) {
            output.put("linkDomes", linkDomes);
        } else {
            linkDomes = new HashMap<String, Object>();
            linkDomes.put("link_overlay", orgOvlLinks);
            linkDomes.put("link_stragty", orgStgLinks);
            output.put("linkDomes", linkDomes);
        }
        if (ovlDomes != null && !ovlDomes.isEmpty()) {
            output.put("ovlDomes", ovlDomes);
        }
        if (stgDomes != null && !stgDomes.isEmpty()) {
            output.put("stgDomes", stgDomes);
        }
        return output;
    }
}
