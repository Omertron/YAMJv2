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

package com.moviejukebox.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Timer;
import java.util.TimerTask;

import com.moviejukebox.model.IMovieBasicInformation;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.ExtraFile;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.WebBrowser;
import com.moviejukebox.tools.WebStats;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.FileTools;

public class AppleTrailersPlugin {

    private static Logger logger = Logger.getLogger("moviejukebox");


    private static String  configResolution = PropertiesUtil.getProperty("appletrailers.resolution", "");
    private static boolean configDownload = Boolean.parseBoolean(PropertiesUtil.getProperty("appletrailers.download", "false"));
    private static String  configTrailerTypes = PropertiesUtil.getProperty("appletrailers.trailertypes", "tlr,clip,tsr,30sec,640w");
    private static int     configMax;
    private static boolean configTypesInclude = Boolean.parseBoolean(PropertiesUtil.getProperty("appletrailers.typesinclude", "true"));
    private static String  configReplaceUrl = PropertiesUtil.getProperty("appletrailers.replaceurl", "www.apple.com");
    static {
        try {
            configMax = Integer.parseInt(PropertiesUtil.getProperty("appletrailers.max", "0"));
        } catch (Exception ignored) {
            configMax = 0;
        }
    };

    protected WebBrowser webBrowser;

    public AppleTrailersPlugin() {
        webBrowser = new WebBrowser();
    }

    // TODO Check to see any previously downloaded trailers physically exist and then recheck them
    public void generate(Movie movie) {

        // Check if trailer resolution was selected
        if (configResolution.equals(""))
            return;

        // Check if this movie was already checked for trailers
        if (movie.isTrailerExchange()) {
            logger.finest("AppleTrailers Plugin: Movie has previously been checked for trailers, skipping.");
            return;
        }

        if (movie.isExtra())
            return;

        if (movie.getMovieType().equals(Movie.TYPE_TVSHOW))
            return;

        String movieName = movie.getOriginalTitle();
        
        String trailerPageUrl = GetTrailerPageUrl(movieName);
        
        if (trailerPageUrl == Movie.UNKNOWN) {
            logger.finer("AppleTrailers Plugin: Trailer not found for " + movie.getBaseName());
            movie.setTrailerExchange(true);
            return;
        }
        
        ArrayList<String> trailersUrl = new ArrayList<String>();
        ArrayList<String> bestTrailersUrl = new ArrayList<String>();
        
        getTrailerSubUrl(trailerPageUrl, trailersUrl);
        
        selectBestTrailer(trailersUrl, bestTrailersUrl);

        int trailerCnt = bestTrailersUrl.size();
        int trailerDownloadCnt = 0;
        
        if (trailerCnt == 0) {
            logger.finest("AppleTrailers Plugin: No trailers found for " + movie.getBaseName());
            return;
        }

        for (int i=0; i < trailerCnt; i++) {            
        
            if (trailerDownloadCnt >= configMax) {
                logger.finest("AppleTrailers Plugin: Downloaded maximum of " + configMax + (configMax == 1 ? "trailer" : "trailers"));
                break;
            }
        
            String trailerRealUrl = bestTrailersUrl.get(i);
            
            // Add the trailer URL to the movie
            MovieFile tmf = new MovieFile();
            tmf.setTitle("TRAILER-" + getTrailerTitle(trailerRealUrl));
            
            // Is the found trailer one of the types to download/link to?
            if (!isValidTrailer(getFilenameFromUrl(trailerRealUrl))) {
                logger.finer("AppleTrailers Plugin: Trailer skipped: " + getFilenameFromUrl(trailerRealUrl));
                continue;           // Quit the rest of the trailer loop.
            }
            
            // Issue with the naming of URL for trailer download
            // See: http://www.hd-trailers.net/blog/how-to-download-hd-trailers-from-apple/
            trailerRealUrl = trailerRealUrl.replace("www.apple.com", configReplaceUrl);
            trailerRealUrl = trailerRealUrl.replace("images.apple.com", configReplaceUrl);
            trailerRealUrl = trailerRealUrl.replace("movies.apple.com", configReplaceUrl);
            
            logger.finer("AppleTrailers Plugin: Trailer found for " + movie.getBaseName() + " (" + getFilenameFromUrl(trailerRealUrl) + ")");
            trailerDownloadCnt++;
            
            // Check if we need to download the trailer, or just link to it
            if (configDownload) {
                // Download the trailer
                MovieFile mf = movie.getFirstFile();
                String parentPath = mf.getFile().getParent();
                String name = mf.getFile().getName();
                String basename;

                if (mf.getFilename().toUpperCase().endsWith("/VIDEO_TS")) {
                    parentPath += File.separator + name;
                    basename = name;
                } else if (mf.getFile().getAbsolutePath().toUpperCase().contains("BDMV")) {
                    parentPath = parentPath.substring(0, parentPath.toUpperCase().indexOf("BDMV") - 1);
                    basename = parentPath.substring(parentPath.lastIndexOf(File.separator) + 1);
                } else {
                    int index = name.lastIndexOf(".");
                    basename = index == -1 ? name : name.substring(0, index);
                }
                
                String trailerAppleName = getFilenameFromUrl(trailerRealUrl);
                String trailerAppleExt = trailerAppleName.substring(trailerAppleName.lastIndexOf("."));
                trailerAppleName = trailerAppleName.substring(0, trailerAppleName.lastIndexOf("."));
                String trailerBasename = FileTools.makeSafeFilename(basename + ".[TRAILER-" + trailerAppleName + "]" + trailerAppleExt);
                String trailerFileName = parentPath + File.separator + trailerBasename;
                
                int slash = mf.getFilename().lastIndexOf("/");
                String playPath = slash == -1 ? mf.getFilename() : mf.getFilename().substring(0, slash);
                String trailerPlayFileName = playPath + "/" + HTMLTools.encodeUrl(trailerBasename);
                
                logger.finest("Found trailer: " + trailerRealUrl);
                logger.finest("Download path: " + trailerFileName);
                logger.finest("     Play URL: " + trailerPlayFileName);
                
                File trailerFile = new File(trailerFileName);
                
                // Check if the file already exists - after jukebox directory was deleted for example
                if (trailerFile.exists()) {
                    logger.finer("AppleTrailers Plugin: Trailer file (" + trailerPlayFileName + ") already exist for " + movie.getBaseName());
                
                    tmf.setFilename(trailerPlayFileName);
                    movie.addExtraFile(new ExtraFile(tmf));
                    //movie.setTrailer(true);
                } else if (trailerDownload(movie, trailerRealUrl, trailerFile)) {
                    tmf.setFilename(trailerPlayFileName);
                    movie.addExtraFile(new ExtraFile(tmf));
                    //movie.setTrailer(true);
                }
            } else {
                // Just link to the trailer
                int underscore = trailerRealUrl.lastIndexOf("_");
                if (underscore > 0) {
                    if (trailerRealUrl.substring(underscore + 1, underscore + 2).equals("h")) {
                        // remove the "h" from the trailer url for streaming
                        trailerRealUrl = trailerRealUrl.substring(0, underscore + 1) + trailerRealUrl.substring(underscore + 2);
                    }
                }
                tmf.setFilename(trailerRealUrl);
                movie.addExtraFile(new ExtraFile(tmf));
                //movie.setTrailer(true);
            }
        }
        
        movie.setTrailerExchange(true);
    }
    
    private String GetTrailerPageUrl(String movieName) {
        try {
            String searchURL = "http://www.apple.com/trailers/home/scripts/quickfind.php?callback=searchCallback&q=" + URLEncoder.encode(movieName, "UTF-8");

            String xml = webBrowser.request(searchURL);

            int index = 0;
            int endIndex = 0;
            while (true) {
                index = xml.indexOf("\"title\":\"", index);
                if (index == -1)
                    break;

                index += 9;

                endIndex = xml.indexOf("\",", index);
                if (endIndex == -1)
                    break;

                String trailerTitle = decodeEscapeICU(xml.substring(index, endIndex));

                index = endIndex + 2;

                index = xml.indexOf("\"location\":\"", index);
                if (index == -1)
                    break;

                index += 12;

                endIndex = xml.indexOf("\",", index);
                if (endIndex == -1)
                    break;

                String trailerLocation = decodeEscapeICU( xml.substring(index, endIndex) );

                index = endIndex + 2;
                
                
                if (trailerTitle.equalsIgnoreCase(movieName)) {
                    String trailerUrl;
                    
                    int itmsIndex = trailerLocation.indexOf("itms://");
                    if (itmsIndex == -1) {
                        // Convert relative URL to absolute URL - some urls are already absolute, and some relative
                        trailerUrl = getAbsUrl("http://www.apple.com/trailers/" , trailerLocation);
                    }
                    else {
                        trailerUrl = "http" + trailerLocation.substring(itmsIndex+4);
                    }
                    
                    return trailerUrl;
                }
            }

        } catch (Exception error) {
            logger.severe("Failed retreiving trailer for movie : " + movieName);
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    
        return Movie.UNKNOWN;
    }
    
    private void getTrailerSubUrl(String trailerPageUrl, ArrayList<String> trailersUrl) {
        try {
        
            String xml = webBrowser.request(trailerPageUrl);

            // Try to find the movie link on the main page
            getTrailerMovieUrl(xml, trailersUrl);

            String trailerPageUrlHD = getAbsUrl(trailerPageUrl, "hd");
            String xmlHD = getSubPage(trailerPageUrlHD);

            // Try to find the movie link on the HD page
            getTrailerMovieUrl(xmlHD, trailersUrl);

            // Go over the href links and check the sub pages
            
            int index = 0;
            int endIndex = 0;
            while (true) {
                index = xml.indexOf("href=\"", index);
                if (index == -1)
                    break;

                index += 6;

                endIndex = xml.indexOf("\"", index);
                if (endIndex == -1)
                    break;

                String href = xml.substring(index, endIndex);

                index = endIndex + 1;
                
                String absHref = getAbsUrl(trailerPageUrl, href);
                
                // Check if this href is a sub page of this trailer
                if (absHref.startsWith(trailerPageUrl)) {

                    String subXml = getSubPage(absHref);
                    
                    // Try to find the movie link on the sub page
                    getTrailerMovieUrl(subXml, trailersUrl);
                }
            }


        } catch (Exception error) {
            logger.severe("Error : " + error.getMessage());
            return;
        }
    }

    // Get sub page url - if error return empty page
    private String getSubPage(String url) {
    
        String ret="";
        
        try {
        
            ret = webBrowser.request(url);
            return ret;
            
        } catch (Exception error) {
            return ret;
        }
    }

    private void getTrailerMovieUrl(String xml, ArrayList<String> trailersUrl) {

        Matcher m = Pattern.compile("http://(movies|images|trailers).apple.com/movies/[^\"]+?-(tlr|trailer)[^\"]+?\\.(mov|m4v)(\")").matcher(xml);
        while (m.find()) {
            String movieUrl = m.group();
            boolean duplicate = false;

            // Check for duplicate
            for (int i=0;i<trailersUrl.size();i++) {
            
                if (trailersUrl.get(i).equals(movieUrl))
                    duplicate = true;
            }
        
            if (!duplicate)
                trailersUrl.add(movieUrl.substring(0, movieUrl.lastIndexOf("\"")));
        }
    }

    private void selectBestTrailer(ArrayList<String> trailersUrl,ArrayList<String> bestTrailersUrl) {
        
        if (configResolution.equals("1080p")) {
            // Search for 1080p
            for (int i=0;i<trailersUrl.size();i++) {
                
                String curURL = trailersUrl.get(i);
                            
                if (curURL.indexOf("1080p")!=-1)
                    addTailerRealUrl(bestTrailersUrl,curURL);
            }
            
            if (!bestTrailersUrl.isEmpty())
                return;
        }

        if ((configResolution.equals("1080p")) ||
            (configResolution.equals("720p"))) {
            // Search for 720p
            for (int i=0;i<trailersUrl.size();i++) {
                
                String curURL = trailersUrl.get(i);
                
                if (curURL.indexOf("720p")!=-1)
                    addTailerRealUrl(bestTrailersUrl,curURL);
            }

            if (!bestTrailersUrl.isEmpty())
                return;
        }

        if ((configResolution.equals("1080p")) ||
            (configResolution.equals("720p")) ||
            (configResolution.equals("480p"))) {
            // Search for 480p
            for (int i=0;i<trailersUrl.size();i++) {
                
                String curURL = trailersUrl.get(i);
                
                if (curURL.indexOf("480p")!=-1)
                    addTailerRealUrl(bestTrailersUrl,curURL);
            }

            if (!bestTrailersUrl.isEmpty())
                return;
        }

        // Search for 640
        for (int i=0;i<trailersUrl.size();i++) {
            
            String curURL = trailersUrl.get(i);
            
            if (curURL.indexOf("640")!=-1)
                addTailerRealUrl(bestTrailersUrl,curURL);
        }

        if (!bestTrailersUrl.isEmpty())
            return;
        
        // Search for 480
        for (int i=0;i<trailersUrl.size();i++) {
            
            String curURL = trailersUrl.get(i);
            
            if (curURL.indexOf("480")!=-1)
                addTailerRealUrl(bestTrailersUrl,curURL);
        }
        
    }

    private void addTailerRealUrl(ArrayList<String> bestTrailersUrl,String trailerUrl) {
    
        String trailerRealUrl = getTrailerRealUrl(trailerUrl);
        
        // Check for duplicate
        for (int i=0;i<bestTrailersUrl.size();i++) {
        
            if (bestTrailersUrl.get(i).equals(trailerRealUrl))
                return;
        }
        
        
        bestTrailersUrl.add(trailerRealUrl);
    }

    private String getTrailerRealUrl(String trailerUrl) {
        try {
        
    
            URL url = new URL(trailerUrl);
            HttpURLConnection connection = (HttpURLConnection) (url.openConnection());
            InputStream inputStream = connection.getInputStream();
        
        
            byte buf[] = new byte[1024];
            int len;
            len = inputStream.read(buf);

            // Check if too much data read, that this is the real url already
            if (len==1024)
                return trailerUrl;

        
            String mov = new String(buf);

            int pos = 44;        
            String realUrl = "";
            
            while (mov.charAt(pos)!=0) {
                realUrl += mov.charAt(pos);
                
                pos++;
            }
            
            String absRealURL = getAbsUrl(trailerUrl, realUrl);
            
            return absRealURL;
            
        } catch (Exception error) {
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    private String getTrailerTitle(String url) {
        int start=url.lastIndexOf('/');
        int end=url.indexOf(".mov",start);
        
        if ((start==-1) || (end==-1))
            return Movie.UNKNOWN;
            
        String title="";
        
        for (int i=start+1;i<end;i++) {
            if ((url.charAt(i)=='-') || (url.charAt(i)=='_'))
                title += ' ';
            else
            
            if (i==start+1)
                title += Character.toUpperCase(url.charAt(i));
            else
            
                title += url.charAt(i);
        }                        
            
        return title;
    }
    
    private String getAbsUrl(String BaseUrl,String RelativeUrl) {
        try {
       
            URL BaseURL = new URL(BaseUrl);
            URL AbsURL = new URL(BaseURL, RelativeUrl);
            String AbsUrl = AbsURL.toString();
            
            return AbsUrl;
            
        } catch (Exception error) {
            return Movie.UNKNOWN;
        }
    }
    
    private String decodeEscapeICU(String s) {
        String r = "";

        int i=0;
        while (i < s.length()) {
            // Check ICU esacaping
            if ((s.charAt(i) == '%') && (i+5 < s.length()) && (s.charAt(i+1) == 'u')) {

                String value=s.substring(i+2,i+6);
                int intValue= Integer.parseInt(value,16);
                
                // fix for ' char
                if (intValue==0x2019)
                    intValue=0x0027;
                
                char c = (char)intValue;

                r += c;
                i += 6;
            }
            else
            if (s.charAt(i) == '\\') {
                i++;
            }
            else {
                r += s.charAt(i);
                i++;
            }
        }
        
        return r;
    }

    private boolean trailerDownload(final IMovieBasicInformation movie, String trailerUrl, File trailerFile) {
        Timer timer = new Timer();

        Semaphore s = null;
        try {
            URL url = new URL(trailerUrl);
            s = WebBrowser.getSemaphore(url.getHost());
            s.acquireUninterruptibly();

            logger.fine("AppleTrailers Plugin: Download trailer for " + movie.getBaseName());
            final WebStats stats = WebStats.make(url);
            // after make!
            timer.schedule(new TimerTask() {
                private String lastStatus = "";
                public void run() {
                    String status = stats.calculatePercentageComplete();
                    // only print if percentage changed
                    if (status.equals(lastStatus)) return;
                    lastStatus = status;
                    // this runs in a thread, so there is no way to output on one line...
                    // try to keep it visible at least...
                    System.out.println("Downloading trailer for " + movie.getTitle() + ": " + stats.statusString());
                }
            }, 1000, 1000);

            HttpURLConnection connection = (HttpURLConnection) (url.openConnection());
            connection.setRequestProperty("User-Agent", "QuickTime/7.6.2");
            InputStream inputStream = connection.getInputStream();

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                logger.severe("AppleTrailers Plugin: Download Failed");
                return false;
            }

            OutputStream out = new FileOutputStream(trailerFile);
            byte buf[] = new byte[1024*1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
                stats.bytes(len);
            }
            out.close();
            System.out.println("Downloading trailer for " + movie.getTitle() + ": " + stats.statusString()); // Output the final stat information (100%)

            return true;

        } catch (Exception error) {
            logger.severe("AppleTrailers Plugin: Download Exception");
            return false;
        } finally {
            timer.cancel();         // Close the timer
            if(s!=null) s.release();
        }
    }
 
    // Extract the filename from the URL
    private String getFilenameFromUrl(String fullUrl) {
        int nameStart = fullUrl.lastIndexOf('/') + 1;
        return fullUrl.substring(nameStart);
    }
    
    // Check the trailer filename against the valid trailer types from appletrailers.trailertypes
    private boolean isValidTrailer(String trailerFilename) {
        boolean validTrailer;
        
        if (configTypesInclude)
            validTrailer = false;
        else
            validTrailer = true;

        for (String ttype : configTrailerTypes.split(",")) {
            if (trailerFilename.lastIndexOf(ttype) > 0) {
                if (configTypesInclude)
                    // Found the trailer type, so this is a valid trailer
                    validTrailer = true;
                else
                    // Found the trailer type, so this trailer should be excluded
                    validTrailer = false;
                break;
            }
        }
        
        return validTrailer;
    }   
}