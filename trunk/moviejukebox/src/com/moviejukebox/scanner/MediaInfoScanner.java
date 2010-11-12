/*
 *      Copyright (c) 2004-2010 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list 
 *  
 *      Web: http://code.google.com/p/moviejukebox/
 *  
 *      This software is licensed under a Creative Commons License
 *      See this page: http://code.google.com/p/moviejukebox/wiki/License
 *  
 *      For any reuse or distribution, you must make clear to others the 
 *      license terms of this work.  
 */

package com.moviejukebox.scanner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import net.sf.xmm.moviemanager.fileproperties.FilePropertiesMovie;

import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.XMLHelper;
import com.mucommander.file.AbstractFile;
import com.mucommander.file.ArchiveEntry;
import com.mucommander.file.FileFactory;
import com.mucommander.file.impl.iso.IsoArchiveFile;

/**
 * @author Grael
 */
public class MediaInfoScanner {

    private static Logger logger = Logger.getLogger("moviejukebox");

    // mediaInfo repository
    private static File mediaInfoPath;

    // mediaInfo command line, depend on OS
    private static String[] mediaInfoExe;
    private static String[] mediaInfoExeWindows = { "cmd.exe", "/E:1900", "/C", "MediaInfo.exe", "-f", null };
    private static String[] mediaInfoExeLinux = { "./mediainfo", "-f", null };
    public final static String OS_NAME = System.getProperty("os.name");
    public final static String OS_VERSION = System.getProperty("os.version");
    public final static String OS_ARCH = System.getProperty("os.arch");
    private static boolean activated;
    private static boolean enableMetadata;
    private String randomDirName;

    static {
        logger.finer("OS name : " + OS_NAME);
        logger.finer("OS version : " + OS_VERSION);
        logger.finer("OS archi : " + OS_ARCH);

        mediaInfoPath = new File(PropertiesUtil.getProperty("mediainfo.home", "./mediaInfo/"));
        enableMetadata = Boolean.parseBoolean(PropertiesUtil.getProperty("mediainfo.metadata.enable", "false"));

        File checkMediainfo = null;

        if (OS_NAME.contains("Windows")) {
            mediaInfoExe = mediaInfoExeWindows;
            checkMediainfo = new File(mediaInfoPath.getAbsolutePath() + File.separator + "MediaInfo.exe");
        } else {
            mediaInfoExe = mediaInfoExeLinux;
            checkMediainfo = new File(mediaInfoPath.getAbsolutePath() + File.separator + "mediainfo");
        }
        // System.out.println(checkMediainfo.getAbsolutePath());
        if (!checkMediainfo.canExecute()) {
            logger.fine("Couldn't find CLI mediaInfo executable tool : Video files data won't be extracted");
            activated = false;
        } else {
            activated = true;
        }
    }

    // Dvd rip infos Scanner
    private DVDRipScanner localDVDRipScanner;

    public MediaInfoScanner() {
        localDVDRipScanner = new DVDRipScanner();
        randomDirName = PropertiesUtil.getProperty("mjb.jukeboxTempDir", "./temp") + "/isoTEMP/" + Thread.currentThread().getName();
    }

    @SuppressWarnings("unchecked")
    public void scan(Movie currentMovie) {
        if (currentMovie.getFile().isDirectory()) {
            // Scan IFO files
            FilePropertiesMovie mainMovieIFO = localDVDRipScanner.executeGetDVDInfo(currentMovie.getFile());
            if (mainMovieIFO != null) {
                scan(currentMovie, mainMovieIFO.getLocation());
                // Issue 1176 - Prevent lost of NFO Data
                if (currentMovie.getRuntime().equals(Movie.UNKNOWN)) {
                    currentMovie.setRuntime(StringTools.formatDuration(mainMovieIFO.getDuration()));
                }
            }
        } else if ((currentMovie.getFile().getName().toLowerCase().endsWith(".iso")) || (currentMovie.getFile().getName().toLowerCase().endsWith(".img"))) {
            // extracting IFO files from ISO file
            AbstractFile abstractIsoFile = null;

            // Issue 979: Split the reading of the ISO file to catch any errors
            try {
                abstractIsoFile = FileFactory.getFile(currentMovie.getFile().getAbsolutePath());
            } catch (Exception error) {
                logger.finer("Error reading disk Image. Please re-rip and try again");
                logger.fine(error.getMessage());
                return;
            }

            IsoArchiveFile scannedIsoFile = new IsoArchiveFile(abstractIsoFile);
            File tempRep = new File(randomDirName  + "/VIDEO_TS");
            tempRep.mkdirs();

            try {
                Vector<ArchiveEntry> allEntries = scannedIsoFile.getEntries();
                Iterator<ArchiveEntry> parcoursEntries = allEntries.iterator();
                while (parcoursEntries.hasNext()) {
                    ArchiveEntry currentArchiveEntry = (ArchiveEntry)parcoursEntries.next();
                    if (currentArchiveEntry.getName().toLowerCase().endsWith(".ifo")) {
                        File currentIFO = new File(randomDirName + "/VIDEO_TS" + File.separator + currentArchiveEntry.getName());
                        OutputStream fosCurrentIFO = FileTools.createFileOutputStream(currentIFO);
                        byte[] ifoFileContent = new byte[Integer.parseInt(Long.toString(currentArchiveEntry.getSize()))];
                        scannedIsoFile.getEntryInputStream(currentArchiveEntry).read(ifoFileContent);
                        fosCurrentIFO.write(ifoFileContent);
                        fosCurrentIFO.close();
                    }
                }
            } catch (Exception error) {
                logger.fine(error.getMessage());
            }

            // Scan IFO files
            FilePropertiesMovie mainMovieIFO = localDVDRipScanner.executeGetDVDInfo(tempRep);
            if (mainMovieIFO != null) {
                scan(currentMovie, mainMovieIFO.getLocation());
                // Issue 1176 - Prevent lost of NFO Data
                if (currentMovie.getRuntime().equals(Movie.UNKNOWN)) {
                    currentMovie.setRuntime(StringTools.formatDuration(mainMovieIFO.getDuration()));
                }
            }

            // Clean up
            FileTools.deleteDir(randomDirName);
        } else {
            scan(currentMovie, currentMovie.getFile().getAbsolutePath());
        }

    }

    public void scan(Movie currentMovie, String movieFilePath) {
        if (!activated) {
            return;
        }

        try {
            String[] commandMedia = mediaInfoExe;
            commandMedia[commandMedia.length - 1] = movieFilePath;

            ProcessBuilder pb = new ProcessBuilder(commandMedia);

            // set up the working directory.
            pb.directory(mediaInfoPath);

            Process p = pb.start();

            HashMap<String, String> infosGeneral = new HashMap<String, String>();
            ArrayList<HashMap<String, String>> infosVideo = new ArrayList<HashMap<String, String>>();
            ArrayList<HashMap<String, String>> infosAudio = new ArrayList<HashMap<String, String>>();
            ArrayList<HashMap<String, String>> infosText = new ArrayList<HashMap<String, String>>();

            parseMediaInfo(p, infosGeneral, infosVideo, infosAudio, infosText);

            updateMovieInfo(currentMovie, infosGeneral, infosVideo, infosAudio, infosText);

        } catch (Exception error) {
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.severe(eResult.toString());
        }

    }

    private String localInputReadLine(BufferedReader input) throws IOException {
        // Suppress empty lines
        String line = input.readLine();
        while ((line != null) && (line.equals(""))) {
            line = input.readLine();
        }
        return line;
    }

    public void parseMediaInfo(InputStream in, HashMap<String, String> infosGeneral, ArrayList<HashMap<String, String>> infosVideo,
                    ArrayList<HashMap<String, String>> infosAudio, ArrayList<HashMap<String, String>> infosText) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        // Improvement, less code line, each cat have same code, so use the same for all.
        Map<String, ArrayList<HashMap<String, String>>> matches = new HashMap<String, ArrayList<HashMap<String, String>>>();
        // Create a fake one for General, we got only one, but to use the same algo we must create this one.
        String generalKey[] = { "General", "Géneral", "* Général" };
        matches.put(generalKey[0], new ArrayList<HashMap<String, String>>());
        matches.put(generalKey[1], matches.get(generalKey[0])); // Issue 1311 - Create a "link" between General and Général
        matches.put(generalKey[2], matches.get(generalKey[0])); // Issue 1311 - Create a "link" between General and * Général
        matches.put("Video", infosVideo);
        matches.put("Vidéo", matches.get("Video")); // Issue 1311 - Create a "link" between Vidéo and Video
        matches.put("Audio", infosAudio);
        matches.put("Text", infosText);

        String line = localInputReadLine(input);
        String label = null;

        while (line != null) {
            // In case of new format : Text #1, Audio #1
            if (line.indexOf("#") >= 0) {
                line = line.substring(0, line.indexOf("#")).trim();
            }

            // Get cat ArrayList from cat name.
            ArrayList<HashMap<String, String>> currentCat = matches.get(line);
            if (currentCat != null) {
                // logger.finer("Current category : " + line);
                HashMap<String, String> currentData = new HashMap<String, String>();
                int indexSeparateur = -1;
                while (((line = localInputReadLine(input)) != null) && ((indexSeparateur = line.indexOf(" : ")) != -1)) {
                    label = line.substring(0, indexSeparateur).trim();
                    if (currentData.get(label) == null) {
                        currentData.put(label, line.substring(indexSeparateur + 3));
                    }
                }
                currentCat.add(currentData);
            } else {
                line = localInputReadLine(input);
            }
        }

        // Setting General Info - Beware of lose data if infosGeneral already have some ...
        try {
            for (int i = 0; i < generalKey.length; i++) {
                ArrayList<HashMap<String, String>> arrayList = matches.get(generalKey[i]);
                if (arrayList.size() > 0) {
                    HashMap<String, String> datas = arrayList.get(0);
                    if (datas.size() > 0) {
                        infosGeneral.putAll(datas);
                        break;
                    }
                }
            }
        } catch (Exception ignore) {
            // We don't care about this exception
        }

        input.close();
    }

    private void parseMediaInfo(Process p, HashMap<String, String> infosGeneral, ArrayList<HashMap<String, String>> infosVideo,
                    ArrayList<HashMap<String, String>> infosAudio, ArrayList<HashMap<String, String>> infosText) throws IOException {

        this.parseMediaInfo(p.getInputStream(), infosGeneral, infosVideo, infosAudio, infosText);
    }

    private void updateMovieInfo(Movie movie, HashMap<String, String> infosGeneral, ArrayList<HashMap<String, String>> infosVideo,
                    ArrayList<HashMap<String, String>> infosAudio, ArrayList<HashMap<String, String>> infosText) {

        String infoValue;

        // update movie with meta tags if present
        if (enableMetadata) {
            if (!movie.isOverrideTitle()) {
                infoValue = infosGeneral.get("Movie");
                if (infoValue == null) {
                    infoValue = infosGeneral.get("Movie name");
                }
                if (infoValue != null) {
                    movie.setTitle(infoValue);
                    movie.setOverrideTitle(true);
                }
            }
            infoValue = infosGeneral.get("Director");
            if (infoValue != null) {
                movie.addDirector(infoValue);
            }
            infoValue = infosGeneral.get("Summary");
            if (infoValue == null) {
                infoValue = infosGeneral.get("Comment");
            }
            if (infoValue != null) {
                movie.setPlot(infoValue);
            }
            infoValue = infosGeneral.get("Genre");
            if (infoValue != null) {
                List<String> list = XMLHelper.parseList(infoValue, "|/,");
                if (!list.isEmpty()) {
                    movie.setGenres(list);
                }
            }
            infoValue = infosGeneral.get("Actor");
            if (infoValue == null) {
                infoValue = infosGeneral.get("Performer");
            }
            if (infoValue != null) {
                List<String> list = XMLHelper.parseList(infoValue, "|/,");
                if (!list.isEmpty()) {
                    movie.setCast(list);
                }
            }
            infoValue = infosGeneral.get("LawRating");
            if (infoValue == null) {
                infoValue = infosGeneral.get("Law rating");
            }
            if (infoValue != null) {
                movie.setCertification(infoValue);
            }
            infoValue = infosGeneral.get("Rating");
            if (infoValue != null) {
                try {
                    float r = Float.parseFloat(infoValue);
                    r = r * 20.0f;
                    movie.setRating(Math.round(r));
                } catch (Exception ignore) {
                }
            }
            infoValue = infosGeneral.get("Country");
            if (infoValue == null) {
                infoValue = infosGeneral.get("Movie/Country");
            }
            if (infoValue == null) {
                infoValue = infosGeneral.get("Movie name/Country");
            }
            if (infoValue != null) {
                movie.setCountry(infoValue);
            }
            infoValue = infosGeneral.get("Released_Date");
            if (infoValue != null) {
                movie.setReleaseDate(infoValue);
            }
        }

        // get Container from General Section
        infoValue = infosGeneral.get("Format");
        if (infoValue != null) {
            movie.setContainer(infoValue);
        }

        infoValue = infosGeneral.get("PlayTime");
        if (infoValue != null) {
            int duration = 0;
            if (infoValue.indexOf('.') >= 0) {
                infoValue = infoValue.substring(0, infoValue.indexOf('.'));
            }
            duration = Integer.parseInt(infoValue) / 1000;
            // Issue 1176 - Prevent lost of NFO Data
            if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                movie.setRuntime(StringTools.formatDuration(duration));
            }
        }
        // get Info from first Video Stream
        // - can evolve to get info from longest Video Stream
        if (infosVideo.size() > 0) {
            HashMap<String, String> infosMainVideo = infosVideo.get(0);

            // Check that movie is not multi part
            if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                if (movie.getMovieFiles().size() == 1) {
                    // Duration
                    infoValue = infosMainVideo.get("Duration");
                    if (infoValue != null) {

                        int duration;
                        duration = Integer.parseInt(infoValue) / 1000;
                        // Issue 1176 - Prevent lost of NFO Data
                        if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                            movie.setRuntime(StringTools.formatDuration(duration));
                        }
                    }
                }
            }

            // Codec (most relevant Info depending on mediainfo result)
            if (movie.getVideoCodec().equals(Movie.UNKNOWN)) {
                infoValue = infosMainVideo.get("Codec ID/Hint");
                if (infoValue != null) {
                    movie.setVideoCodec(infoValue);
                } else {
                    infoValue = infosMainVideo.get("Codec");
                    if (infoValue != null) {
                        movie.setVideoCodec(infoValue);
                    } else {
                        infoValue = infosMainVideo.get("Format");
                        if (infoValue != null) {
                            movie.setVideoCodec(infoValue);
                        } else {
                            infoValue = infosMainVideo.get("Codec ID");
                            if (infoValue != null) {
                                movie.setVideoCodec(infoValue);
                            }
                        }
                    }
                }
            }

            // Resolution
            if (movie.getResolution().equals(Movie.UNKNOWN)) {
                int width = 0;

                infoValue = infosMainVideo.get("Width");
                if (infoValue != null) {
                    width = Integer.parseInt(infoValue);

                    infoValue = infosMainVideo.get("Height");
                    if (infoValue != null) {
                        movie.setResolution(width + "x" + infoValue);
                    }
                }
            }

            // Frames per second
            infoValue = infosMainVideo.get("Frame rate");
            if (infoValue != null) {
                Float fps;
                fps = Float.parseFloat(infoValue);

                movie.setFps(fps);
            }

            // Save the aspect ratio for the video
            if (movie.getAspectRatio().equals(Movie.UNKNOWN)) {
                infoValue = infosMainVideo.get("Display aspect ratio");
                if (infoValue != null) {
                    movie.setAspectRatio(infoValue);
                }
            }

            if (movie.getVideoOutput().equals(Movie.UNKNOWN)) {
                // Guessing Video Output (Issue 988)
                String normeHD;
                if (movie.isHD()) {
                    if (movie.isHD1080()) {
                        normeHD = "1080";
                    } else {
                        normeHD = "720";
                    }

                    infoValue = infosMainVideo.get("Scan type");
                    if (infoValue != null) {
                        if (infoValue.equals("Progressive")) {
                            normeHD += "p";
                        } else {
                            normeHD += "i";
                        }
                    }
                    movie.setVideoOutput(normeHD + " " + Math.round(movie.getFps()) + "Hz");
                } else {
                    normeHD = "SD";
                    String videoOutput;
                    switch (Math.round(movie.getFps())) {
                    case 24:
                        videoOutput = "24";
                        break;
                    case 25:
                        videoOutput = "PAL 25";
                        break;
                    case 30:
                        videoOutput = "NTSC 30";
                        break;
                    case 50:
                        videoOutput = "PAL 50";
                        break;
                    case 60:
                        videoOutput = "NTSC 60";
                        break;
                    default:
                        videoOutput = "NTSC";
                        break;
                    }
                    infoValue = infosMainVideo.get("Scan type");
                    if (infoValue != null) {
                        if (infoValue.equals("Progressive")) {
                            videoOutput += "p";
                        } else {
                            videoOutput += "i";
                        }
                    }
                    movie.setVideoOutput(videoOutput);
                }
            }
        }

        // Cycle through Audio Streams
        // boolean previousAudioCodec = !movie.getAudioCodec().equals(Movie.UNKNOWN); // Do we have AudioCodec already?
        // boolean previousAudioChannels = !movie.getAudioChannels().equals(Movie.UNKNOWN); // Do we have AudioChannels already?

        ArrayList<String> foundLanguages = new ArrayList<String>();

        String tmpAudioCodec = Movie.UNKNOWN;
        String tmpAudioChannels = Movie.UNKNOWN;

        for (int numAudio = 0; numAudio < infosAudio.size(); numAudio++) {
            HashMap<String, String> infosCurAudio = infosAudio.get(numAudio);

            String infoLanguage = "";
            infoValue = infosCurAudio.get("Language");
            if (infoValue != null) {
                // Issue 1227 - Make some clean up in mediainfo datas.
                if (infoValue.contains("/")) {
                    infoValue = infoValue.substring(0, infoValue.indexOf("/")).trim(); // In this case, language are "doubled", just take the first one.
                }
                infoLanguage = " (" + infoValue + ")";
                // Add determination of language.
                String determineLanguage = MovieFilenameScanner.determineLanguage(infoValue);
                if (!foundLanguages.contains(determineLanguage)) {
                    foundLanguages.add(determineLanguage);
                }
            }

            infoValue = infosCurAudio.get("Codec ID/Hint");
            if (infoValue == null) {
                infoValue = infosCurAudio.get("Codec");
            }

            if (infoValue != null) { // Make sure we have a codec before continuing
                // String oldInfo = movie.getAudioCodec(); // Save the current codec information (if any)
                if (StringTools.isNotValidString(tmpAudioCodec)) {
                    tmpAudioCodec = infoValue + infoLanguage;
                } else {
                    tmpAudioCodec = tmpAudioCodec + " / " + infoValue + infoLanguage;
                }
            }

            infoValue = infosCurAudio.get("Channel(s)");
            if (infoValue != null) {
                // String oldInfo = movie.getAudioChannels();
                if (StringTools.isNotValidString(tmpAudioChannels)) {
                    tmpAudioChannels = infoValue;
                } else {
                    tmpAudioChannels = tmpAudioChannels + " / " + infoValue;
                }
            }
        }

        if (StringTools.isValidString(tmpAudioChannels)) {
            movie.setAudioChannels(tmpAudioChannels);
        }

        if (StringTools.isValidString(tmpAudioCodec)) {
            movie.setAudioCodec(tmpAudioCodec);
        }

        // TODO Add an option to choose to override FileName language info.
        if (foundLanguages.size() > 0) {
            int index = 0;
            for (String language : foundLanguages) {
                if (index++ > 0) {
                    movie.setLanguage(movie.getLanguage() + " / " + language);
                } else {
                    movie.setLanguage(language);
                }
            }
        }

        // Cycle through Text Streams
        for (int numText = 0; numText < infosText.size(); numText++) {
            HashMap<String, String> infosCurText = infosText.get(numText);

            String infoLanguage = "";
            infoValue = infosCurText.get("Language");
            
            // Issue 1450 - If we are here, we have subtitles, but didn't have the language, setting an UNKNOWN value to make it appear
            if(infoValue==null || infoValue.trim().length()==0){
                infoValue= Movie.UNKNOWN;
            }
            
            if (infoValue != null) {
                // Issue 1227 - Make some clean up in mediainfo datas.
                if (infoValue.contains("/")) {
                    infoValue = infoValue.substring(0, infoValue.indexOf("/")).trim(); // In this case, language are "doubled", just take the first one.
                }
                infoLanguage = infoValue;
            }

            String infoFormat = "";
            infoValue = infosCurText.get("Format");

            // Issue 1450 - If we are here, we have subtitles, but didn't have the language, setting an UNKNOWN value to make it appear
            if(infoValue==null || infoValue.trim().length()==0){
                infoValue= Movie.UNKNOWN;
            }
            
            if (infoValue != null) {
                infoFormat = infoValue;
            } else {
                // take care of label for "Format" in mediaInfo 0.6.1.1
                infoValue = infosCurText.get("Codec");
                if (infoValue != null) {
                    infoFormat = infoValue;
                }
            }

            // Make sure we have a codec & language before continuing
            if (StringTools.isValidString(infoFormat) && StringTools.isValidString(infoLanguage)) { 
                if (infoFormat.equalsIgnoreCase("SRT") || 
                        infoFormat.equalsIgnoreCase("UTF-8") || 
                        infoFormat.equalsIgnoreCase("RLE") || 
                        infoFormat.equalsIgnoreCase("PGS") || 
                        infoFormat.equalsIgnoreCase("VobSub")) {
                    String oldInfo = movie.getSubtitles(); // Save the current subtitle information (if any)
                    if (StringTools.isNotValidString(oldInfo) || oldInfo.equalsIgnoreCase("NO")) {
                        movie.setSubtitles(infoLanguage);
                    } else {
                        // Don't overwrite what is there currently
                        movie.setSubtitles(oldInfo + " / " + infoLanguage);
                    }
                } else {
                    logger.finest("MediaInfo Scanner - Subtitle format skipped: " + infoFormat);
                }
            }
        }
    }

}
