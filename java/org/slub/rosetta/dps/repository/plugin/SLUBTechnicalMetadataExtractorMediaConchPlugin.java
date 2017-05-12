/* 
2017 by Andreas Romeyke (SLUB Dresden)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.slub.rosetta.dps.repository.plugin;


import com.exlibris.core.sdk.strings.StringUtils;
import com.exlibris.dps.sdk.techmd.MDExtractorPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SLUBTechnicalMetadataExtractorMediaConchPlugin
 *
 * @author andreas.romeyke@slub-dresden.de (Andreas Romeyke)
 * @see com.exlibris.dps.sdk.techmd.MDExtractorPlugin 
 */
/*public class SLUBTechnicalMetadataExtractorMediaConchPlugin implements MDExtractorPlugin { */
public class SLUBTechnicalMetadataExtractorMediaConchPlugin implements MDExtractorPlugin {

    private String mediaconch_binary_path;
    private String mediaconch_profile_path;
    private String ffprobe_binary_path;

    private List<String> extractionErrors = new ArrayList<String>();
    private List<String> validationLog = new ArrayList<String>();
    private boolean isvalid = false;
    private boolean iswellformed = false;



    private Map<String,String> attributes = new HashMap<String, String>();
    //static final ExLogger log = ExLogger.getExLogger(SLUBTechnicalMetadataExtractorMediaConchPlugin.class, ExLogger.VALIDATIONSTACK);
    /** constructor */
    public SLUBTechnicalMetadataExtractorMediaConchPlugin() {
        //log.info("SLUBVirusCheckPlugin instantiated with host=" + host + " port=" + port + " timeout=" + timeout);
        System.out.println("SLUBTechnicalMetadataExtractorMediaConchPlugin instantiated");
    }
    /** init params to configure the plugin via xml forms
     * @param initp parameter map
     */
    public void initParams(Map<String, String> initp) {
        this.mediaconch_binary_path = initp.get("mediaconch_binary_path").trim();
        this.mediaconch_profile_path = initp.get("mediaconch_profile_path").trim();
        this.ffprobe_binary_path = initp.get("ffprobe_binary_path").trim();

        System.out.println("SLUBTechnicalMetadataExtractorMediaConchPlugin instantiated with "
                + " mediaconch_binary_path=" + mediaconch_binary_path
                + " mediaconch_profile_path=" + mediaconch_profile_path
                + " ffprobe_binary_path=" + ffprobe_binary_path
        );
    }

    private void parse_ffprobe_csv_output(String exiftoolxml ) {
        // see output of exiftool -X, alternatively check http://ns.exiftool.ca/ExifTool/1.0/
        Pattern p = Pattern.compile("<([^>]+)>([^<]+)</\1>");
        Matcher m = p.matcher(exiftoolxml);
        if (m.matches()) {
            String key = m.group(1);
            String value = m.group(2);
            System.out.println("matcher: key=" + key + " value=" + value);
            attributes.put(key, value);
        }
    }

    @Override
    public void extract(String filePath) throws Exception {
        if (StringUtils.isEmptyString(mediaconch_binary_path)) {
            //log.error("No mediaconch_binary_path defined. Please set the plugin parameter to hold your mediaconch_binary_path.");
            throw new Exception("mediaconch_binary_path not found");
        }
        if (StringUtils.isEmptyString(mediaconch_profile_path)) {
            //log.error("No mediaconch_config_path defined. Please set the plugin parameter to hold your mediaconch_config_path.");
            throw new Exception("mediaconch_profile_path not found");
        }
        if (StringUtils.isEmptyString(ffprobe_binary_path)) {

            throw new Exception("ffprobe_binary_path (part of ffmpeg) not found");
        }


        // mediaconch validation
        try {
            String execstring = this.mediaconch_binary_path + " " + filePath + " " + this.mediaconch_profile_path;
            System.out.println("executing: " + execstring);
            Process p = Runtime.getRuntime().exec(execstring);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();

            while (line != null) {
                System.out.println(line);
                validationLog.add(line);
                line = reader.readLine();
            }
            if (p.exitValue() == 0) {
                isvalid = true;
                iswellformed=true;
            } else { // something wrong
                isvalid = false;
                iswellformed = false;
                extractionErrors = validationLog;
            }
        } catch (IOException e) {
            //log.error("exception creation socket, clamd not available at host=" + host + "port=" + port, e);
            System.out.println("ERROR: (actual) mediaconch not available, path=" + this.mediaconch_binary_path + ", " + e.getMessage());
            throw new Exception("ERROR: (actual) mediaconch not available, path=" + this.mediaconch_binary_path + ", " + e.getMessage());
        }


        // exiftool output of metadata
        try {
            String execstring = this.ffprobe_binary_path + " -print_format csv -v error -show_format -show_streams " + filePath;
            System.out.println("executing: " + execstring);
            Process p = Runtime.getRuntime().exec(execstring);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line=reader.readLine();
            String response="";
            while (line != null) {
                System.out.println(line);
                parse_ffprobe_csv_output(line.trim());
                response+=line;
                line = reader.readLine();
            }
            attributes.put("ffprobe-log", response.trim());

        } catch (IOException e) {
            //log.error("exception creation socket, clamd not available at host=" + host + "port=" + port, e);


        } catch (InterruptedException e) {
            e.printStackTrace();

        }
    }

    public String getAgentName()
    {
      return "mediaconch";
    }

    /** get clamd agent version and signature version calling clamd-command VERSION
     *
     * @return string with clamd version and signature version
     */
    public String getAgent() {
        String response="";
        response+="mediaconch:\n";
        try {
            String execstring = this.mediaconch_binary_path + " -v";
            Process p = Runtime.getRuntime().exec(execstring);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line=reader.readLine();
            while (line != null) {
                System.out.println(line);
                response+=line;
                line = reader.readLine();
            }
        } catch (IOException e) {
            //log.error("exception creation socket, clamd not available at host=" + host + "port=" + port, e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return response.trim();
    }

    @Override
    public String getAttributeByName(String attribute) {
      if (attributes.containsKey(attribute)) {
          return attributes.get(attribute);
      }
        return "not found";
    }

    @Override
    public List<String> getExtractionErrors() {
      return this.extractionErrors;
    }

    @Override
    public List<String> getSupportedAttributeNames() {
      //return new ArrayList<String>(attributes.keySet());
        List<String> available = new ArrayList<String>();
        //available.add("checkit-tiff-conf");

        return available;
    }

    @Override
    public boolean isWellFormed() {
      return this.iswellformed;
    }

    @Override
    public boolean isValid() {
        System.out.println("DEBUG: is valid=" + this.isvalid);
        return this.isvalid;
    }
    @Override
    public String getFormatName() {
        return "FFV1/Matroska";
    }

    @Override
    public String getFormatVersion() {
      return "FFV1 v3, Matroska v1.4 (Pronom PUID: fmt/569)";
    }

    @Override
    public Integer getImageCount() {
        return 1; //baseline tiff holds exact one
    }

    @Override
    public String getMimeType() {
      return "video/x-matroska";
    }

    /** stand alone check, main file to call local installed clamd
     * @param args list of files which should be scanned
     */
    public static void main(String[] args) {
        SLUBTechnicalMetadataExtractorMediaConchPlugin plugin = new SLUBTechnicalMetadataExtractorMediaConchPlugin();
        Map<String, String> initp = new HashMap<String, String>();
        initp.put( "mediaconch_binary_path", "/usr/bin/mediaconch");
        initp.put( "mediaconch_profile_path", "/etc/mediaconch/profile.xml");
        initp.put( "ffprobe_binary_path", "/usr/bin/ffprobe");
        plugin.initParams( initp );
        System.out.println("Agent: '" + plugin.getAgent() + "'");
        System.out.println();
        for (String file : args) {
            try {
                plugin.extract(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("RESULT: " + plugin.isValid());
            System.out.println("ERRORMESSAGE: " + plugin.getExtractionErrors());
        }
    }
}


