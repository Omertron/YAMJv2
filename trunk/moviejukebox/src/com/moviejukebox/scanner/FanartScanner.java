/*
 *      Copyright (c) 2004-2009 YAMJ Members
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

/**
 *  Fanart Scanner
 *
 * Routines for locating and downloading Fanart for videos
 *
 */
package com.moviejukebox.scanner;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.MovieImagePlugin;
import com.moviejukebox.themoviedb.TheMovieDb;
import com.moviejukebox.themoviedb.model.Artwork;
import com.moviejukebox.themoviedb.model.MovieDB;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.GraphicTools;
import com.moviejukebox.tools.PropertiesUtil;

/**
 * Scanner for fanart files in local directory
 * 
 * @author Stuart.Boston
 * @version 1.0, 10th December 2008 - Initial code
 * @version 1.1, 19th July 2009 - Added Internet search
 */
public class FanartScanner {

    protected static Logger logger = Logger.getLogger("moviejukebox");
    protected static String[] fanartExtensions;
    protected static String fanartToken;
    protected static boolean fanartOverwrite;

    static {

        // We get valid extensions
        StringTokenizer st = new StringTokenizer(PropertiesUtil.getProperty("fanart.scanner.fanartExtensions", "jpg,jpeg,gif,bmp,png"), ",;| ");
        Collection<String> extensions = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            extensions.add(st.nextToken());
        }
        fanartExtensions = extensions.toArray(new String[] {});

        fanartToken = PropertiesUtil.getProperty("fanart.scanner.fanartToken", ".fanart");

        fanartOverwrite = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.forceFanartOverwrite", "false"));
    }

    public static void scan(MovieImagePlugin backgroundPlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        String localFanartBaseFilename = FileTools.makeSafeFilename(movie.getBaseName());
        String fullFanartFilename = null;
        String foundExtension = null;
        File localFanartFile = null;
        boolean foundLocalFanart = false;

        // Look for the videoname.fanartToken.Extension
        fullFanartFilename = FileTools.getParentFolder(movie.getFile());

        fullFanartFilename += File.separator + localFanartBaseFilename + fanartToken;
        foundExtension = findFanartFile(fullFanartFilename, fanartExtensions);
        if (!foundExtension.equals("")) {
            // The filename and extension was found
            fullFanartFilename += foundExtension;

            localFanartFile = new File(fullFanartFilename); // Double check it still works
            if (localFanartFile.exists()) {
                logger.finest("FanartScanner: File " + fullFanartFilename + " found");
                foundLocalFanart = true;
            }
        }

        // if no fanart has been found, try the foldername.fanartToken.Extension
        if (!foundLocalFanart) {
            localFanartBaseFilename = movie.getFile().getParent();
            localFanartBaseFilename = localFanartBaseFilename.substring(localFanartBaseFilename.lastIndexOf(File.separator) + 1);

            // Checking for the MovieFolderName.*
            fullFanartFilename = movie.getFile().getParent() + File.separator + localFanartBaseFilename + fanartToken;
            foundExtension = findFanartFile(fullFanartFilename, fanartExtensions);
            if (!foundExtension.equals("")) {
                // The filename and extension was found
                fullFanartFilename += foundExtension;
                localFanartFile = new File(fullFanartFilename);
                if (localFanartFile.exists()) {
                    logger.finest("FanartScanner: File " + fullFanartFilename + " found");
                    foundLocalFanart = true;
                }
            }
        }

        // If we've found the fanart, copy it to the jukebox, otherwise download it.
        if (foundLocalFanart) {
            if (movie.getFanartFilename().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setFanartFilename(movie.getBaseName() + fanartToken + foundExtension);
            }
            if (movie.getFanartURL().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setFanartURL(localFanartFile.toURI().toString());
            }
            String fanartFilename = FileTools.makeSafeFilename(movie.getFanartFilename());
            String finalDestinationFileName = jukeboxDetailsRoot + File.separator + fanartFilename;
            String destFileName = tempJukeboxDetailsRoot + File.separator + fanartFilename;

            File finalDestinationFile = new File(finalDestinationFileName);
            File fullFanartFile = new File(fullFanartFilename);

            // Local Fanart is newer OR ForceFanartOverwrite OR DirtyFanart
            // Can't check the file size because the jukebox fanart may have been re-sized
            // This may mean that the local art is different to the jukebox art even if the local file date is newer
            if (FileTools.isNewer(fullFanartFile, finalDestinationFile) || fanartOverwrite || movie.isDirtyFanart()) {
                try {
                    BufferedImage fanartImage = GraphicTools.loadJPEGImage(new FileInputStream(fullFanartFile));
                    if (fanartImage != null) {
                        fanartImage = backgroundPlugin.generate(movie, fanartImage, "fanart", null);
                        if (Boolean.parseBoolean(PropertiesUtil.getProperty("fanart.perspective", "false"))) {
                        	destFileName = destFileName.subSequence(0, destFileName.lastIndexOf(".") + 1) + "png";
                        	movie.setFanartFilename(destFileName);
                        }
                        GraphicTools.saveImageToDisk(fanartImage, destFileName);
                        logger.finer("FanartScanner: " + fullFanartFilename + " has been copied to " + destFileName);
                    } else {
                        movie.setFanartFilename(Movie.UNKNOWN);
                        movie.setFanartURL(Movie.UNKNOWN);
                    }
                } catch (Exception error) {
                    logger.finer("FanartScanner: Failed loading fanart : " + fullFanartFilename);
                }
            } else {
                logger.finer("FanartScanner: " + finalDestinationFileName + " already exists");
            }
        } else {
            // logger.finer("FanartScanner : No local Fanart found for " + movie.getBaseName() + " attempting to download");
            downloadFanart(backgroundPlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);
        }
    }

    private static void downloadFanart(MovieImagePlugin backgroundPlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        if (movie.getFanartURL() != null && !movie.getFanartURL().equalsIgnoreCase(Movie.UNKNOWN)) {
            String safeFanartFilename = FileTools.makeSafeFilename(movie.getFanartFilename());
            String fanartFilename = jukeboxDetailsRoot + File.separator + safeFanartFilename;
            File fanartFile = new File(fanartFilename);
            String tmpDestFileName = tempJukeboxDetailsRoot + File.separator + safeFanartFilename;
            File tmpDestFile = new File(tmpDestFileName);

            // Do not overwrite existing fanart unless ForceFanartOverwrite = true
            if ((!fanartFile.exists() && !tmpDestFile.exists()) || fanartOverwrite) {
                fanartFile.getParentFile().mkdirs();

                try {
                    logger.finest("Fanart Scanner: Downloading fanart for " + movie.getBaseName() + " to " + tmpDestFileName + " [calling plugin]");

                    FileTools.downloadImage(fanartFile, URLDecoder.decode(movie.getFanartURL(), "UTF-8"));
                    BufferedImage fanartImage = GraphicTools.loadJPEGImage(new FileInputStream(fanartFilename));

                    if (fanartImage != null) {
                        fanartImage = backgroundPlugin.generate(movie, fanartImage, null, null);
                        GraphicTools.saveImageToDisk(fanartImage, tmpDestFileName);
                    } else {
                        movie.setFanartFilename(Movie.UNKNOWN);
                        movie.setFanartURL(Movie.UNKNOWN);
                    }
                } catch (Exception error) {
                    logger.finer("Fanart Scanner: Failed to download fanart : " + movie.getFanartURL() + " removing from movie details");
                    movie.setFanartFilename(Movie.UNKNOWN);
                    movie.setFanartURL(Movie.UNKNOWN);
                }
            } else {
                logger.finest("Fanart Scanner: Fanart exists for " + movie.getBaseName());
            }
        }
    }

    /***
     * Pass in the filename and a list of extensions, this function will scan for the filename plus extensions and return the extension
     * 
     * @param filename
     * @param extensions
     * @return extension of fanart that was found
     */
    private static String findFanartFile(String fullFanartFilename, String[] fanartExtensions) {
        File localFanartFile;
        boolean foundLocalFanart = false;

        for (String extension : fanartExtensions) {
            localFanartFile = new File(fullFanartFilename + "." + extension);
            if (localFanartFile.exists()) {
                logger.finest("The file " + fullFanartFilename + "." + extension + " found");
                fullFanartFilename = "." + extension;
                foundLocalFanart = true;
                break;
            }
        }

        // If we've found the filename with extension, return it, otherwise return ""
        if (foundLocalFanart) {
            return fullFanartFilename;
        } else {
            return "";
        }
    }

    /**
     * Get the Fanart for the movie from TheMovieDB.org
     * 
     * @author Stuart.Boston
     * @param movie
     *            The movie bean to get the fanart for
     * @return A string URL pointing to the fanart
     */
    public static String getFanartURL(Movie movie) {
        String API_KEY = PropertiesUtil.getProperty("TheMovieDB");
        String language = PropertiesUtil.getProperty("themoviedb.language", "en");
        String imdbID = null;
        String tmdbID = null;
        TheMovieDb TMDb;
        MovieDB moviedb = null;

        TMDb = new TheMovieDb(API_KEY);

        //TODO move these Ids to a preferences file.
        imdbID = movie.getId("imdb");
        tmdbID = movie.getId("themoviedb");

        if (tmdbID != null && !tmdbID.equalsIgnoreCase(Movie.UNKNOWN)) {
            moviedb = TMDb.moviedbGetInfo(tmdbID, language);
        } else if (imdbID != null && !imdbID.equalsIgnoreCase(Movie.UNKNOWN)) {
            moviedb = TMDb.moviedbImdbLookup(imdbID, language);
        } else {
            moviedb = TMDb.moviedbSearch(movie.getTitle(), language);
        }

        // Check that the returned movie bean isn't null
        if (moviedb == null) {
        	logger.finer("FanartScanner: Error getting fanart for " + movie.getBaseName());
        	return Movie.UNKNOWN;
        }
        
        String fanartUrl = moviedb.getFirstArtwork(Artwork.ARTWORK_TYPE_BACKDROP, Artwork.ARTWORK_SIZE_ORIGINAL).getUrl();
        if (fanartUrl == null || fanartUrl.equals(MovieDB.UNKNOWN)) {
            return Movie.UNKNOWN;
        } else {
            movie.setDirtyFanart(true);
            return fanartUrl;
        }
    }
}