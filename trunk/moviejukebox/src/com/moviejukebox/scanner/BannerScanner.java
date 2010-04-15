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

/**
 *  Banner Scanner
 *
 * Routines for locating and downloading Banner for videos
 *
 */
package com.moviejukebox.scanner;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.MovieImagePlugin;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.GraphicTools;
import com.moviejukebox.tools.PropertiesUtil;

/**
 * Scanner for banner files in local directory
 * 
 * @author Stuart.Boston
 * @version 1.0, 25th August 2009 - Initial code copied from FanartScanner.java
 * 
 */
public class BannerScanner {

    protected static Logger logger = Logger.getLogger("moviejukebox");
    protected static String[] bannerExtensions;
    protected static String bannerToken;
    protected static boolean bannerOverwrite;

    static {

        // We get valid extensions
        StringTokenizer st = new StringTokenizer(PropertiesUtil.getProperty("banner.scanner.bannerExtensions", "jpg,jpeg,gif,bmp,png"), ",;| ");
        Collection<String> extensions = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            extensions.add(st.nextToken());
        }
        bannerExtensions = extensions.toArray(new String[] {});

        bannerToken = PropertiesUtil.getProperty("banner.scanner.bannerToken", ".banner");

        bannerOverwrite = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.forcePosterOverwrite", "false"));
    }

    /**
     * Scan for local banners and download if necessary
     * 
     * @param imagePlugin
     * @param jukeboxDetailsRoot
     * @param tempJukeboxDetailsRoot
     * @param movie
     */
    public static boolean scan(MovieImagePlugin imagePlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        String localBannerBaseFilename = movie.getBaseName();
        String fullBannerFilename = null;
        File localBannerFile = null;
        boolean foundLocalBanner = false;

        // Look for the banner.bannerToken.Extension
        if (movie.getFile().isDirectory()) { // for VIDEO_TS
            fullBannerFilename = movie.getFile().getPath();
        } else {
            fullBannerFilename = movie.getFile().getParent();
        }

        fullBannerFilename += File.separator + localBannerBaseFilename + bannerToken;
        localBannerFile = findBannerFile(fullBannerFilename, bannerExtensions);
        foundLocalBanner = localBannerFile.exists();

        // if no banner has been found, try the foldername.bannerToken.Extension
        if (!foundLocalBanner) {
            // FIXME: localBannerBaseFilename is used nowhere.
            localBannerBaseFilename = movie.getFile().getParent();
            localBannerBaseFilename = localBannerBaseFilename.substring(localBannerBaseFilename.lastIndexOf(File.separator) + 1);

            // Checking for the MovieFolderName.*
            fullBannerFilename = movie.getFile().getParent() + File.separator + movie.getBaseFilename() + bannerToken;
            localBannerFile = findBannerFile(fullBannerFilename, bannerExtensions);
            foundLocalBanner = localBannerFile.exists();
        }

        // If we've found the banner, copy it to the jukebox, otherwise download it.
        if (foundLocalBanner) {
            fullBannerFilename = localBannerFile.getAbsolutePath();
            logger.finest("BannerScanner: File " + fullBannerFilename + " found");
            if (movie.getBannerFilename().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setBannerFilename(movie.getBaseName() + bannerToken + "." + PropertiesUtil.getProperty("banners.format", "jpg"));
            }
            if (movie.getBannerURL().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setBannerURL(localBannerFile.toURI().toString());
            }
            String bannerFilename = movie.getBannerFilename();
            String finalDestinationFileName = jukeboxDetailsRoot + File.separator + bannerFilename;
            String destFileName = tempJukeboxDetailsRoot + File.separator + bannerFilename;

            File finalDestinationFile = FileTools.fileCache.getFile(finalDestinationFileName);
            File fullBannerFile = localBannerFile;

            // Local Banner is newer OR ForcePosterOverwrite OR DirtyPoster
            // Can't check the file size because the jukebox banner may have been re-sized
            // This may mean that the local art is different to the jukebox art even if the local file date is newer
            if (bannerOverwrite || movie.isDirtyPoster() || FileTools.isNewer(fullBannerFile, finalDestinationFile)) {
                try {
                    BufferedImage bannerImage = GraphicTools.loadJPEGImage(fullBannerFile);
                    if (bannerImage != null) {
                        bannerImage = imagePlugin.generate(movie, bannerImage, "banners", null);
                        GraphicTools.saveImageToDisk(bannerImage, destFileName);
                        logger.finer("BannerScanner: " + fullBannerFilename + " has been copied to " + destFileName);
                    } else {
                        movie.setBannerFilename(Movie.UNKNOWN);
                        movie.setBannerURL(Movie.UNKNOWN);
                    }
                } catch (Exception error) {
                    logger.finer("BannerScanner: Failed loading banner : " + fullBannerFilename);
                }
            } else {
                logger.finer("BannerScanner: " + finalDestinationFileName + " already exists");
            }
        } else {
            // logger.finer("BannerScanner : No local Banner found for " + movie.getBaseName() + " attempting to download");
            
            // Don't download banners for sets as they will use the first banner from the set
            if (!movie.isSetMaster()) 
                downloadBanner(imagePlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);
        }
        return foundLocalBanner;
    }

    /**
     * Download the banner from the URL.
     * Initially this is populated from TheTVDB plugin
     * 
     * @param imagePlugin  
     * @param jukeboxDetailsRoot   
     * @param tempJukeboxDetailsRoot
     * @param movie
     */
    private static void downloadBanner(MovieImagePlugin imagePlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        if (movie.getBannerURL() != null && !movie.getBannerURL().equalsIgnoreCase(Movie.UNKNOWN)) {
            String safeBannerFilename = movie.getBannerFilename();
            String bannerFilename = jukeboxDetailsRoot + File.separator + safeBannerFilename;
            File bannerFile = FileTools.fileCache.getFile(bannerFilename);
            String tmpDestFileName = tempJukeboxDetailsRoot + File.separator + safeBannerFilename;
            File tmpDestFile = new File(tmpDestFileName);

            // Do not overwrite existing banner unless ForceBannerOverwrite = true
            if (bannerOverwrite || movie.isDirtyBanner() || (!bannerFile.exists() && !tmpDestFile.exists())) {
                bannerFile.getParentFile().mkdirs();

                try {
                    logger.finest("Banner Scanner: Downloading banner for " + movie.getBaseName() + " to " + tmpDestFileName + " [calling plugin]");

                    // Download the banner using the proxy save downloadImage
                    FileTools.downloadImage(tmpDestFile, movie.getBannerURL());
                    BufferedImage bannerImage = GraphicTools.loadJPEGImage(tmpDestFile);

                    if (bannerImage != null) {
                        bannerImage = imagePlugin.generate(movie, bannerImage, "banners", null);
                        GraphicTools.saveImageToDisk(bannerImage, tmpDestFileName);
                    } else {
                        movie.setBannerFilename(Movie.UNKNOWN);
                        movie.setBannerURL(Movie.UNKNOWN);
                    }
                } catch (Exception error) {
                    logger.finer("Banner Scanner: Failed to download banner : " + movie.getBannerURL());
                }
            } else {
                logger.finest("Banner Scanner: Banner exists for " + movie.getBaseName());
            }
        }
        
        return;
    }

    /***
     * Pass in the filename and a list of extensions, this function will scan for the filename plus extensions and return the File
     * 
     * @param filename
     * @param extensions
     * @return always a File, to be tested with exists() for valid banner
     */
    private static File findBannerFile(String fullBannerFilename, String[] bannerExtensions) {
        File localBannerFile = null;

        for (String extension : bannerExtensions) {
            localBannerFile = FileTools.fileCache.getFile(fullBannerFilename + "." + extension);
            if (localBannerFile.exists()) {
                logger.finest("The file " + fullBannerFilename + "." + extension + " found");
                return localBannerFile;
            }
        }

        return localBannerFile != null ? localBannerFile : new File(fullBannerFilename+Movie.UNKNOWN); //just in case
    }
}