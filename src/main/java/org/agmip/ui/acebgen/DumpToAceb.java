package org.agmip.ui.acebgen;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.rits.cloning.Cloner;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.agmip.ace.io.AceGenerator;
import static org.agmip.util.JSONAdapter.toJSON;
import org.agmip.util.MapUtil;

public class DumpToAceb {

    private HashMap data;
    private String fileName, directoryName;
    private boolean isDome;
    private boolean isSkipped;
    private boolean isSkippedForLink;
    private static final HashFunction hf = Hashing.sha256();
    private HashMap domeIdHashMap = new HashMap();
    private HashMap domeHashData = null;

    public DumpToAceb(String file, String dirName, HashMap data, boolean isDome, boolean isSkipped, boolean isSkippedForLink) {
        this.fileName = file;
        this.directoryName = dirName;
        this.data = data;
        this.isDome = isDome;
        this.isSkipped = isSkipped;
        this.isSkippedForLink = isSkippedForLink;
    }

    public void execute() throws IOException {
        File file = new File(fileName);
        String[] base = file.getName().split("\\.(?=[^\\.]+$)");
        String outputAceb;
        if (isDome) {
            if (data.containsKey("stgDomes")) {
                outputAceb = directoryName + "/" + base[0] + "_All_DOMEs.aceb";
            } else {
                outputAceb = directoryName + "/" + base[0] + "_OverlayOnly_DOMEs.aceb";
            }
        } else {
            outputAceb = directoryName + "/" + base[0] + ".aceb";
        }
        file = new File(outputAceb);
        int count = 1;
        while (file.isFile() && !file.canWrite()) {
            file = new File(file.getPath().replaceAll(".+_\\d*.aceb", "_" + count + ".aceb"));
            count++;
        }
//            if (!file.exists()) {
//                file.createNewFile();
//            }
        if (!isDome) {
            if (!isSkipped) {
                AceGenerator.generateACEB(file, toJSON(data));
            }
        } else {
            domeHashData = new HashMap();
            domeIdHashMap = new HashMap();
            buildDomeHash("ovlDomes");
            buildDomeHash("stgDomes");
            if (!isSkipped) {
                AceGenerator.generateACEB(file, toJSON(domeHashData));
            }
            if (!isSkippedForLink) {
                file = new File(directoryName + "/" + base[0] + "_Linkage.aceb");
                HashMap<String, HashMap> linkInfo = MapUtil.getObjectOr(data, "linkDomes", new HashMap());
                String hash = generateHCId(linkInfo).toString();
                HashMap hashData = new HashMap();
                hashData.put(hash, linkInfo);
                AceGenerator.generateACEB(file, toJSON(hashData));
            }
            domeHashData = null;
        }
    }
    
    private void buildDomeHash(String domeType) throws IOException {
        HashMap<String, HashMap> domes = MapUtil.getObjectOr(data, domeType, new HashMap());
        for (String domeId : domes.keySet()) {
            String hash = generateHCId(domes.get(domeId)).toString();
            domeHashData.put(hash, domes.get(domeId));
            domeIdHashMap.put(domeId, hash);
        }
    }
    
    private HashCode generateHCId(HashMap data) throws IOException {
        return hf.newHasher().putBytes(toJSON(data).getBytes("UTF-8")).hash();
    }
}
