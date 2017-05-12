Plugin using MediaConch to validate FFV1/Matroska files
=======================================================

you could test it using 'test.sh'

== compile

* make clean
* make

== install
* copy jar-file to /operational_shared/plugins/custom/

== configuration
* check blog https://developers.exlibrisgroup.com/blog/Jpylyzer-Technical-Metadata-Extractor-Plugin
* check https://mediaarea.net/MediaConch/ for latest MediaConch release
* add Mapping under "Preservation:Extractors", switch from "Global" to "Local", use
  "Custom"-Tab
* fill the fields 

== copyright hints

MediaConch is released under Gnu General Public License 3.0 (or higher)
it could not be integrated and delivered as a binary only plugin.
