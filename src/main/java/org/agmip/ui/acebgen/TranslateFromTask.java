package org.agmip.ui.acebgen;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.agmip.common.Functions;
import org.agmip.core.types.TranslatorInput;
import org.agmip.translators.agmip.AgmipInput;
import org.agmip.translators.csv.CSVInput;
import org.agmip.translators.dssat.DssatControllerInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslateFromTask {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateFromTask.class);
    private String file;
    private HashMap<String, TranslatorInput> translators = new HashMap<String, TranslatorInput>();

    public TranslateFromTask(String file) throws Exception {
        this.file = file;
        if (file.toLowerCase().endsWith(".zip")) {
            FileInputStream f = new FileInputStream(file);
            ZipInputStream z = new ZipInputStream(new BufferedInputStream(f));
            ZipEntry ze;
            while ((ze = z.getNextEntry()) != null) {
                if (ze.getName().toLowerCase().endsWith(".csv")) {
                    translators.put("CSV", new CSVInput());
//                    break;
                } else
                if (ze.getName().toLowerCase().endsWith(".wth")) {
                    translators.put("DSSAT", new DssatControllerInput());
//                    break;
                } else
                if (ze.getName().toLowerCase().endsWith(".agmip")) {
                    LOG.debug("Found .AgMIP file {}", ze.getName());
                    translators.put("AgMIP", new AgmipInput());
//                    break;
                }
            }
            if (translators.isEmpty()) {
                translators.put("DSSAT", new DssatControllerInput());
            }
            z.close();
            f.close();
        } else if (file.toLowerCase().endsWith(".agmip")) {
            translators.put("AgMIP", new AgmipInput());
        } else if (file.toLowerCase().endsWith(".csv")) {
            translators.put("CSV", new CSVInput());
        } else { 
            LOG.error("Unsupported file: {}", file);
            throw new Exception("Unsupported file type");
        }
    }

    public HashMap<String, Object> execute() {
        HashMap<String, Object> output = new HashMap<String, Object>();
        try {
//            output = (HashMap<String, Object>) translator.readFile(file);
            for (String key : translators.keySet()) {
                LOG.info("{} translator is fired to read source data package", key);
                Map m = translators.get(key).readFile(file);
                combineResult(output, m);
                LOG.debug("{}", output.get("weathers"));
            }
            // Only use in extreme cases
            //LOG.debug("Translate From Results: {}", output.toString());
            return output;
        } catch (Exception ex) {
            LOG.error(Functions.getStackTrace(ex));
            output.put("errors", ex.toString());
            return output;
        }
    }
    
    private void combineResult(HashMap out, Map in) {
        String[] keys = {"experiments", "soils", "weathers"};
        for (String key : keys) {
            ArrayList outArr;
            ArrayList inArr;
            if ((inArr = (ArrayList) in.get(key)) != null && !inArr.isEmpty()) {
                outArr = (ArrayList) out.get(key);
                if (outArr == null) {
                    out.put(key, inArr);
                } else {
                    outArr.addAll(inArr);
                }
            } 
        }
    }
}
