/*
 *      Copyright (c) 2004-2014 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      This file is part of the Yet Another Movie Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 */
package com.moviejukebox.scanner.artwork;

import com.moviejukebox.model.DirtyFlag;
import com.moviejukebox.model.IImage;
import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.ImdbPlugin;
import com.moviejukebox.plugin.MovieImagePlugin;
import com.moviejukebox.plugin.TheMovieDbPlugin;
import com.moviejukebox.plugin.TheTvDBPlugin;
import com.moviejukebox.scanner.AttachmentScanner;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.GraphicTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.SystemTools;
import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.TheMovieDbApi;
import com.omertron.themoviedbapi.model.MovieDb;
import com.omertron.themoviedbapi.results.TmdbResultsList;
import com.omertron.thetvdbapi.model.Banner;
import com.omertron.thetvdbapi.model.BannerType;
import com.omertron.thetvdbapi.model.Banners;
import com.omertron.thetvdbapi.model.Series;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.sanselan.ImageReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scanner for fanart files in local directory
 *
 * @author Stuart.Boston
 * @version 1.0, 10th December 2008 - Initial code
 * @version 1.1, 19th July 2009 - Added Internet search
 */
public final class FanartScanner {

    private static final Logger LOG = LoggerFactory.getLogger(FanartScanner.class);
    private static final String LOG_MESSAGE = "FanartScanner: ";
    private static final Collection<String> FANART_EXT = Collections.synchronizedList(new ArrayList<String>());
    private static final String FANART_TOKEN;
    private static final boolean FANART_OVERWRITE;
    private static final boolean USE_FOLDER_IMAGE = PropertiesUtil.getBooleanProperty("fanart.scanner.useFolderImage", Boolean.FALSE);
    private static final Collection<String> IMAGE_NAMES = Collections.synchronizedList(new ArrayList<String>());
    private static final boolean ARTWORK_VALIDATE;
    private static final int ARTWORK_VALIDATE_MATCH;
    private static TheMovieDbApi TMDB = null;

    static {

        // We get valid extensions
        synchronized (FANART_EXT) {
            if (FANART_EXT.isEmpty()) {
                StringTokenizer st = new StringTokenizer(PropertiesUtil.getProperty("fanart.scanner.fanartExtensions", "jpg,jpeg,gif,bmp,png"), ",;| ");
                while (st.hasMoreTokens()) {
                    FANART_EXT.add(st.nextToken());
                }
            }
        }

        FANART_TOKEN = PropertiesUtil.getProperty("mjb.scanner.fanartToken", ".fanart");
        FANART_OVERWRITE = PropertiesUtil.getBooleanProperty("mjb.forceFanartOverwrite", Boolean.FALSE);

        // See if we use background.* or fanart.*
        synchronized (IMAGE_NAMES) {
            if (USE_FOLDER_IMAGE && IMAGE_NAMES.isEmpty()) {
                StringTokenizer st = new StringTokenizer(PropertiesUtil.getProperty("fanart.scanner.imageName", "fanart,backdrop,background"), ",;|");
                while (st.hasMoreTokens()) {
                    IMAGE_NAMES.add(st.nextToken());
                }
            }
        }

//        ARTWORK_WIDTH = PropertiesUtil.getIntProperty("fanart.width", 0);
//        ARTWORK_HEIGHT = PropertiesUtil.getIntProperty("fanart.height", 0);
        ARTWORK_VALIDATE = PropertiesUtil.getBooleanProperty("fanart.scanner.Validate", Boolean.TRUE);
        ARTWORK_VALIDATE_MATCH = PropertiesUtil.getIntProperty("fanart.scanner.ValidateMatch", 75);
//        ARTWORK_VALIDATE_ASPECT = PropertiesUtil.getBooleanProperty("fanart.scanner.ValidateAspect", Boolean.TRUE);

        try {
            TMDB = new TheMovieDbApi(PropertiesUtil.getProperty("API_KEY_TheMovieDB"));
        } catch (MovieDbException ex) {
            LOG.warn(LOG_MESSAGE + "Failed to initialise TheMovieDB API. Fanart will not be downloaded.");
            LOG.warn(SystemTools.getStackTrace(ex));
        }
    }

    private FanartScanner() {
        throw new UnsupportedOperationException("Class cannot be instantiated");
    }

    /**
     * Get the Fanart URL for the movie from the source sites
     *
     * @param movie
     * @return
     */
    public static String getFanartURL(Movie movie) {
        if (movie.isTVShow()) {
            return getTvFanartURL(movie);
        } else {
            return getMovieFanartURL(movie);
        }

    }

    public static boolean scan(MovieImagePlugin backgroundPlugin, Jukebox jukebox, Movie movie) {
        String localFanartBaseFilename = movie.getBaseFilename();
        String parentPath = FileTools.getParentFolder(movie.getFile());

        // Look for the videoname.fanartToken.Extension
        String fullFanartFilename = StringTools.appendToPath(parentPath, localFanartBaseFilename + FANART_TOKEN);
        File localFanartFile = FileTools.findFileFromExtensions(fullFanartFilename, FANART_EXT);
        boolean foundLocalFanart = localFanartFile.exists();

        // Try searching the fileCache for the filename.
        if (!foundLocalFanart) {
            Boolean searchInJukebox = Boolean.TRUE;
            // if the fanart URL is invalid, but the fanart filename is valid, then this is likely a recheck, so don't search on the jukebox folder
            if (StringTools.isNotValidString(movie.getFanartURL()) && StringTools.isValidString(movie.getFanartFilename())) {
                searchInJukebox = Boolean.FALSE;
            }
            localFanartFile = FileTools.findFilenameInCache(localFanartBaseFilename + FANART_TOKEN, FANART_EXT, jukebox, LOG_MESSAGE, searchInJukebox);
            if (localFanartFile != null) {
                foundLocalFanart = true;
            }
        }

        // if no fanart has been found, try the foldername.fanartToken.Extension
        if (!foundLocalFanart) {
            localFanartBaseFilename = FileTools.getParentFolderName(movie.getFile());

            // Checking for the MovieFolderName.*
            fullFanartFilename = StringTools.appendToPath(parentPath, localFanartBaseFilename + FANART_TOKEN);
            localFanartFile = FileTools.findFileFromExtensions(fullFanartFilename, FANART_EXT);
            foundLocalFanart = localFanartFile.exists();
        }

        // Check for fanart.* and background.* fanart.
        if (!foundLocalFanart && USE_FOLDER_IMAGE) {
            // Check for each of the farnartImageName.* files
            for (String fanartFilename : IMAGE_NAMES) {
                fullFanartFilename = StringTools.appendToPath(parentPath, fanartFilename);
                localFanartFile = FileTools.findFileFromExtensions(fullFanartFilename, FANART_EXT);
                foundLocalFanart = localFanartFile.exists();

                if (!foundLocalFanart && movie.isTVShow()) {
                    // Get the parent directory and check that
                    fullFanartFilename = StringTools.appendToPath(FileTools.getParentFolder(movie.getFile().getParentFile().getParentFile()), fanartFilename);
                    //System.out.println("SCANNER: " + fullFanartFilename);
                    localFanartFile = FileTools.findFileFromExtensions(fullFanartFilename, FANART_EXT);
                    foundLocalFanart = localFanartFile.exists();
                    if (foundLocalFanart) {
                        break;   // We found the artwork so quit the loop
                    }
                } else {
                    break;    // We found the artwork so quit the loop
                }
            }
        }

        // Check file attachments
        if (!foundLocalFanart) {
            localFanartFile = AttachmentScanner.extractAttachedFanart(movie);
            foundLocalFanart = (localFanartFile != null);
        }

        // If we've found the fanart, copy it to the jukebox, otherwise download it.
        if (foundLocalFanart && (localFanartFile != null)) {
            fullFanartFilename = localFanartFile.getAbsolutePath();
            LOG.debug(LOG_MESSAGE + "File " + fullFanartFilename + " found");

            if (StringTools.isNotValidString(movie.getFanartFilename())) {
                movie.setFanartFilename(movie.getBaseFilename() + FANART_TOKEN + "." + FileTools.getFileExtension(localFanartFile.getName()));
            }

            if (StringTools.isNotValidString(movie.getFanartURL())) {
                movie.setFanartURL(localFanartFile.toURI().toString());
            }
            String fanartFilename = movie.getFanartFilename();
            String finalDestinationFileName = StringTools.appendToPath(jukebox.getJukeboxRootLocationDetails(), fanartFilename);
            String destFileName = StringTools.appendToPath(jukebox.getJukeboxTempLocationDetails(), fanartFilename);

            File finalDestinationFile = FileTools.fileCache.getFile(finalDestinationFileName);
            File fullFanartFile = new File(fullFanartFilename);

            // Local Fanart is newer OR ForceFanartOverwrite OR DirtyFanart
            // Can't check the file size because the jukebox fanart may have been re-sized
            // This may mean that the local art is different to the jukebox art even if the local file date is newer
            if (FileTools.isNewer(fullFanartFile, finalDestinationFile) || FANART_OVERWRITE || movie.isDirty(DirtyFlag.FANART)) {
                try {
                    BufferedImage fanartImage = GraphicTools.loadJPEGImage(fullFanartFile);
                    if (fanartImage != null) {
                        fanartImage = backgroundPlugin.generate(movie, fanartImage, "fanart", null);
                        if (PropertiesUtil.getBooleanProperty("fanart.perspective", Boolean.FALSE)) {
                            destFileName = destFileName.subSequence(0, destFileName.lastIndexOf('.') + 1) + "png";
                            movie.setFanartFilename(destFileName);
                        }
                        GraphicTools.saveImageToDisk(fanartImage, destFileName);
                        LOG.debug(LOG_MESSAGE + fullFanartFilename + " has been copied to " + destFileName);
                    } else {
                        movie.setFanartFilename(Movie.UNKNOWN);
                        movie.setFanartURL(Movie.UNKNOWN);
                    }
                } catch (IOException error) {
                    LOG.debug(LOG_MESSAGE + "Failed loading fanart: " + fullFanartFilename);
                } catch (ImageReadException error) {
                    LOG.debug(LOG_MESSAGE + "Failed loading fanart: " + fullFanartFilename);
                }
            } else {
                LOG.debug(LOG_MESSAGE + finalDestinationFileName + " already exists");
            }
        } else {
            LOG.debug(LOG_MESSAGE + "No local Fanart found for " + movie.getBaseFilename() + " attempting to download");
            downloadFanart(backgroundPlugin, jukebox, movie);
        }

        return foundLocalFanart;
    }

    private static void downloadFanart(MovieImagePlugin backgroundPlugin, Jukebox jukebox, Movie movie) {
        if (StringTools.isValidString(movie.getFanartURL())) {
            String safeFanartFilename = movie.getFanartFilename();
            String fanartFilename = StringTools.appendToPath(jukebox.getJukeboxRootLocationDetails(), safeFanartFilename);
            File fanartFile = FileTools.fileCache.getFile(fanartFilename);
            String tmpDestFileName = StringTools.appendToPath(jukebox.getJukeboxTempLocationDetails(), safeFanartFilename);
            File tmpDestFile = new File(tmpDestFileName);

            // Do not overwrite existing fanart unless ForceFanartOverwrite = true
            if (FANART_OVERWRITE
                    || (!fanartFile.exists() && !tmpDestFile.exists())
                    || movie.isDirty(DirtyFlag.FANART)) {
                FileTools.makeDirsForFile(tmpDestFile);

                try {
                    LOG.debug(LOG_MESSAGE + "Downloading fanart for " + movie.getBaseFilename() + " to " + tmpDestFileName + " [calling plugin]");

                    FileTools.downloadImage(tmpDestFile, URLDecoder.decode(movie.getFanartURL(), "UTF-8"));
                    BufferedImage fanartImage = GraphicTools.loadJPEGImage(tmpDestFile);

                    if (fanartImage != null) {
                        fanartImage = backgroundPlugin.generate(movie, fanartImage, null, null);
                        GraphicTools.saveImageToDisk(fanartImage, tmpDestFileName);
                    } else {
                        movie.setFanartFilename(Movie.UNKNOWN);
                        movie.setFanartURL(Movie.UNKNOWN);
                    }
                } catch (IOException error) {
                    LOG.debug(LOG_MESSAGE + "Failed to download fanart: " + movie.getFanartURL() + " removing from movie details");
                    movie.setFanartFilename(Movie.UNKNOWN);
                    movie.setFanartURL(Movie.UNKNOWN);
                } catch (ImageReadException error) {
                    LOG.debug(LOG_MESSAGE + "Failed to download fanart: " + movie.getFanartURL() + " removing from movie details");
                    movie.setFanartFilename(Movie.UNKNOWN);
                    movie.setFanartURL(Movie.UNKNOWN);
                }
            } else {
                LOG.debug(LOG_MESSAGE + "Fanart exists for " + movie.getBaseFilename());
            }
        }
    }

    /**
     * Get the Fanart for the movie from TheMovieDB.org
     *
     * @author Stuart.Boston
     * @param movie The movie to get the fanart for
     * @return A string URL pointing to the fanart
     */
    private static String getMovieFanartURL(Movie movie) {
        // Unable to scan for fanart because TheMovieDB wasn't initialised
        if (TMDB == null) {
            return Movie.UNKNOWN;
        }

        String language = PropertiesUtil.getProperty("themoviedb.language", "en-US");
        MovieDb moviedb = null;

        String imdbID = movie.getId(ImdbPlugin.IMDB_PLUGIN_ID);
        String tmdbIDstring = movie.getId(TheMovieDbPlugin.TMDB_PLUGIN_ID);
        int tmdbID;

        if (StringUtils.isNumeric(tmdbIDstring)) {
            tmdbID = Integer.parseInt(tmdbIDstring);
        } else {
            tmdbID = 0;
        }

        if (tmdbID > 0) {
            try {
                moviedb = TMDB.getMovieInfo(tmdbID, language);
            } catch (MovieDbException ex) {
                LOG.debug(LOG_MESSAGE + "Failed to get fanart using TMDB ID: " + tmdbID + " - " + ex.getMessage());
                moviedb = null;
            }
        }

        if (moviedb == null && StringTools.isValidString(imdbID)) {
            try {
                // The ImdbLookup contains images
                moviedb = TMDB.getMovieInfoImdb(imdbID, language);
            } catch (MovieDbException ex) {
                LOG.debug(LOG_MESSAGE + "Failed to get fanart using IMDB ID: " + imdbID + " - " + ex.getMessage());
                moviedb = null;
            }
        }

        if (moviedb == null) {
            try {
                int movieYear = 0;
                if (StringTools.isValidString(movie.getYear()) && StringUtils.isNumeric(movie.getYear())) {
                    movieYear = Integer.parseInt(movie.getYear());
                }

                TmdbResultsList<MovieDb> result = TMDB.searchMovie(movie.getOriginalTitle(), movieYear, language, TheMovieDbPlugin.INCLUDE_ADULT, 0);
                List<MovieDb> movieList = result.getResults();

                for (MovieDb m : movieList) {
                    if (m.getTitle().equals(movie.getTitle())
                            || m.getTitle().equalsIgnoreCase(movie.getOriginalTitle())
                            || m.getOriginalTitle().equalsIgnoreCase(movie.getTitle())
                            || m.getOriginalTitle().equalsIgnoreCase(movie.getOriginalTitle())) {
                        if (StringTools.isNotValidString(movie.getYear())) {
                            // We don't have a year for the movie, so assume this is the correct movie
                            moviedb = m;
                            break;
                        } else if (m.getReleaseDate().contains(movie.getYear())) {
                            // found the movie name and year
                            moviedb = m;
                            break;
                        }
                    }
                }
            } catch (MovieDbException ex) {
                LOG.debug(LOG_MESSAGE + "Failed to get fanart using IMDB ID: " + imdbID + " - " + ex.getMessage());
                moviedb = null;
            }
        }

        // Check that the returned movie isn't null
        if (moviedb == null) {
            LOG.debug(LOG_MESSAGE + "Error getting fanart from TheMovieDB.org for " + movie.getBaseFilename());
            return Movie.UNKNOWN;
        }

        try {
            URL fanart = TMDB.createImageUrl(moviedb.getBackdropPath(), "original");
            if (fanart == null) {
                return Movie.UNKNOWN;
            } else {
                return fanart.toString();
            }
        } catch (MovieDbException ex) {
            LOG.debug(LOG_MESSAGE + "Error getting fanart from TheMovieDB.org for " + movie.getBaseFilename());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Get the Fanart for the movie from TheTVDb.com
     *
     * @author Stuart.Boston
     * @param movie The movie bean to get the fanart for
     * @return A string URL pointing to the fanart
     */
    private static String getTvFanartURL(Movie movie) {
        String url = null;

        String id = TheTvDBPlugin.findId(movie);

        if (StringTools.isNotValidString(id)) {
            return Movie.UNKNOWN;
        }
        Series series = TheTvDBPlugin.getSeries(id);
        if (series == null) {
            return Movie.UNKNOWN;
        }

        Banners banners = TheTvDBPlugin.getBanners(id);

        if (!banners.getFanartList().isEmpty()) {
            // Pick a fanart that is not likely to be the same as a previous one.
            int index = movie.getSeason();
            if (index < 0) {
                index = 0;
            }

            // Make sure that the index is not more than the list of available banners
            // We may still run into issues, if there are less HD than this number
            index = (index % banners.getFanartList().size());

            Banner bannerSD = null;
            Banner bannerHD = null;
            int countSD = 0;
            int countHD = 0;

            for (Banner banner : banners.getFanartList()) {
                if (banner.getBannerType2() == BannerType.FANART_HD) {
                    bannerHD = banner;  // Save the current banner
                    countHD++;
                    if (countHD >= index) {
                        // We have a HD banner, so quit
                        break;
                    }
                } else {
                    // This is a SD banner, So save it in case we can't find a HD one
                    if (countSD <= index) {
                        // Only update the banner if we want a later one
                        bannerSD = banner;
                    }
                }
            }

            if (bannerHD != null) {
                url = bannerHD.getUrl();
            } else if (bannerSD != null) {
                url = bannerSD.getUrl();
            }

        }

        if (StringTools.isNotValidString(url) && StringTools.isValidString(series.getFanart())) {
            url = series.getFanart();
        }

//        if (StringTools.isValidString(movie.getFanartURL())) {
//            String artworkFilename = movie.getBaseName() + fanartToken + "." + fanartExtension;
//            movie.setFanartFilename(artworkFilename);
//        }
        return url;
    }

    public static boolean validateArtwork(IImage artworkImage, int artworkWidth, int artworkHeight, boolean checkAspect) {
        @SuppressWarnings("rawtypes")
        Iterator readers = ImageIO.getImageReadersBySuffix("jpeg");
        ImageReader reader = (ImageReader) readers.next();
        int urlWidth, urlHeight;
        float urlAspect;

        if (!ARTWORK_VALIDATE) {
            return true;
        }

        if (StringTools.isNotValidString(artworkImage.getUrl())) {
            return false;
        }

        try {
            URL url = new URL(artworkImage.getUrl());
            InputStream in = url.openStream();
            ImageInputStream iis = ImageIO.createImageInputStream(in);
            reader.setInput(iis, true);
            urlWidth = reader.getWidth(0);
            urlHeight = reader.getHeight(0);

            if (in != null) {
                in.close();
            }

            if (iis != null) {
                iis.close();
            }
        } catch (IOException error) {
            LOG.debug(LOG_MESSAGE + "ValidateFanart error: " + error.getMessage() + ": can't open url");
            return false; // Quit and return a false fanart
        }

        urlAspect = (float) urlWidth / (float) urlHeight;

        if (checkAspect && urlAspect < 1.0) {
            LOG.debug(LOG_MESSAGE + "ValidateFanart " + artworkImage + " rejected: URL is portrait format");
            return false;
        }

        // Adjust fanart width / height by the ValidateMatch figure
        int newArtworkWidth = artworkWidth * (ARTWORK_VALIDATE_MATCH / 100);
        int newArtworkHeight = artworkHeight * (ARTWORK_VALIDATE_MATCH / 100);

        if (urlWidth < newArtworkWidth) {
            LOG.debug(LOG_MESSAGE + artworkImage + " rejected: URL width (" + urlWidth + ") is smaller than fanart width (" + newArtworkWidth + ")");
            return false;
        }

        if (urlHeight < newArtworkHeight) {
            LOG.debug(LOG_MESSAGE + artworkImage + " rejected: URL height (" + urlHeight + ") is smaller than fanart height (" + newArtworkHeight + ")");
            return false;
        }
        return true;
    }
}
