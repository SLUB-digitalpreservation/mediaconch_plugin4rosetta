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

    /* ffprobe output of metadata
           supports different outputs. we are using the flat-model, see WRITERS section in ffprobe manual
           the streams will be mapped as: streams.stream.0.$property
           the separator is "="
         */
    private void parse_ffprobe_flat_output(String exiftoolxml ) {
        // see output of exiftool -X, alternatively check http://ns.exiftool.ca/ExifTool/1.0/
        Pattern p = Pattern.compile("([^=]+)=(.*)");
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


        /* ffprobe output of metadata
           supports different outputs. we are using the flat-model, see WRITERS section in ffprobe manual
           the streams will be mapped as: streams.stream.0.$property
           the separator is "="
         */

        try {
            String execstring = this.ffprobe_binary_path + " -print_format flat -v error -show_format -show_streams -show_entries stream=r_frame_rate" + filePath;
            System.out.println("executing: " + execstring);
            Process p = Runtime.getRuntime().exec(execstring);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line=reader.readLine();
            String response="";
            while (line != null) {
                System.out.println(line);
                parse_ffprobe_flat_output(line.trim());
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

    /* following list is build using:
    (find ./ -name "*.mkv" -exec ffprobe -print_format flat  -v error -show_format -show_streams -show_entries stream=r_frame_rate \{\} \; ) \
    | cut -d "=" -f 1 | sort | uniq \
    | sed -e "s/\(.*\)/available.add(\"\1\");/g"
     */
    @Override
    public List<String> getSupportedAttributeNames() {
      //return new ArrayList<String>(attributes.keySet());
        List<String> available = new ArrayList<String>();
        //available.add("checkit-tiff-conf");
        available.add("format.bit_rate");
        available.add("format.duration");
        available.add("format.filename");
        available.add("format.format_long_name");
        available.add("format.format_name");
        available.add("format.nb_programs");
        available.add("format.nb_streams");
        available.add("format.probe_score");
        available.add("format.size");
        available.add("format.start_time");
        available.add("format.tags.DATE");
        available.add("format.tags.ENCODED_BY");
        available.add("format.tags.ENCODER");
        available.add("format.tags.MAJOR_BRAND");
        available.add("format.tags.MINOR_VERSION");
        available.add("format.tags.ORIGINATOR_REFERENCE");
        available.add("format.tags.TIME_REFERENCE");
        available.add("streams.stream.0.avg_frame_rate");
        available.add("streams.stream.0.bit_rate");
        available.add("streams.stream.0.bits_per_raw_sample");
        available.add("streams.stream.0.bits_per_sample");
        available.add("streams.stream.0.channel_layout");
        available.add("streams.stream.0.channels");
        available.add("streams.stream.0.chroma_location");
        available.add("streams.stream.0.codec_long_name");
        available.add("streams.stream.0.codec_name");
        available.add("streams.stream.0.codec_tag");
        available.add("streams.stream.0.codec_tag_string");
        available.add("streams.stream.0.codec_time_base");
        available.add("streams.stream.0.codec_type");
        available.add("streams.stream.0.coded_height");
        available.add("streams.stream.0.coded_width");
        available.add("streams.stream.0.color_primaries");
        available.add("streams.stream.0.color_range");
        available.add("streams.stream.0.color_space");
        available.add("streams.stream.0.color_transfer");
        available.add("streams.stream.0.display_aspect_ratio");
        available.add("streams.stream.0.disposition.attached_pic");
        available.add("streams.stream.0.disposition.clean_effects");
        available.add("streams.stream.0.disposition.comment");
        available.add("streams.stream.0.disposition.default");
        available.add("streams.stream.0.disposition.dub");
        available.add("streams.stream.0.disposition.forced");
        available.add("streams.stream.0.disposition.hearing_impaired");
        available.add("streams.stream.0.disposition.karaoke");
        available.add("streams.stream.0.disposition.lyrics");
        available.add("streams.stream.0.disposition.original");
        available.add("streams.stream.0.disposition.timed_thumbnails");
        available.add("streams.stream.0.disposition.visual_impaired");
        available.add("streams.stream.0.duration");
        available.add("streams.stream.0.duration_ts");
        available.add("streams.stream.0.field_order");
        available.add("streams.stream.0.has_b_frames");
        available.add("streams.stream.0.height");
        available.add("streams.stream.0.id");
        available.add("streams.stream.0.index");
        available.add("streams.stream.0.level");
        available.add("streams.stream.0.max_bit_rate");
        available.add("streams.stream.0.nb_frames");
        available.add("streams.stream.0.nb_read_frames");
        available.add("streams.stream.0.nb_read_packets");
        available.add("streams.stream.0.pix_fmt");
        available.add("streams.stream.0.profile");
        available.add("streams.stream.0.refs");
        available.add("streams.stream.0.r_frame_rate");
        available.add("streams.stream.0.sample_aspect_ratio");
        available.add("streams.stream.0.sample_fmt");
        available.add("streams.stream.0.sample_rate");
        available.add("streams.stream.0.start_pts");
        available.add("streams.stream.0.start_time");
        available.add("streams.stream.0.tags.DURATION");
        available.add("streams.stream.0.tags.ENCODER");
        available.add("streams.stream.0.tags.HANDLER_NAME");
        available.add("streams.stream.0.tags.language");
        available.add("streams.stream.0.tags.TIMECODE");
        available.add("streams.stream.0.time_base");
        available.add("streams.stream.0.timecode");
        available.add("streams.stream.0.width");
        available.add("streams.stream.1.avg_frame_rate");
        available.add("streams.stream.1.bit_rate");
        available.add("streams.stream.1.bits_per_raw_sample");
        available.add("streams.stream.1.bits_per_sample");
        available.add("streams.stream.1.channel_layout");
        available.add("streams.stream.1.channels");
        available.add("streams.stream.1.codec_long_name");
        available.add("streams.stream.1.codec_name");
        available.add("streams.stream.1.codec_tag");
        available.add("streams.stream.1.codec_tag_string");
        available.add("streams.stream.1.codec_time_base");
        available.add("streams.stream.1.codec_type");
        available.add("streams.stream.1.disposition.attached_pic");
        available.add("streams.stream.1.disposition.clean_effects");
        available.add("streams.stream.1.disposition.comment");
        available.add("streams.stream.1.disposition.default");
        available.add("streams.stream.1.disposition.dub");
        available.add("streams.stream.1.disposition.forced");
        available.add("streams.stream.1.disposition.hearing_impaired");
        available.add("streams.stream.1.disposition.karaoke");
        available.add("streams.stream.1.disposition.lyrics");
        available.add("streams.stream.1.disposition.original");
        available.add("streams.stream.1.disposition.timed_thumbnails");
        available.add("streams.stream.1.disposition.visual_impaired");
        available.add("streams.stream.1.duration");
        available.add("streams.stream.1.duration_ts");
        available.add("streams.stream.1.id");
        available.add("streams.stream.1.index");
        available.add("streams.stream.1.max_bit_rate");
        available.add("streams.stream.1.nb_frames");
        available.add("streams.stream.1.nb_read_frames");
        available.add("streams.stream.1.nb_read_packets");
        available.add("streams.stream.1.profile");
        available.add("streams.stream.1.r_frame_rate");
        available.add("streams.stream.1.sample_fmt");
        available.add("streams.stream.1.sample_rate");
        available.add("streams.stream.1.start_pts");
        available.add("streams.stream.1.start_time");
        available.add("streams.stream.1.tags.DURATION");
        available.add("streams.stream.1.tags.HANDLER_NAME");
        available.add("streams.stream.1.tags.language");
        available.add("streams.stream.1.time_base");
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


