/*
 *      Copyright (c) 2004-2012 YAMJ Members
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

/* Filmdelta.se plugin
 *
 * Contains code for an alternate plugin for fetching information on
 * movies in swedish
 *
 */
package com.moviejukebox.plugin;

import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.SystemTools;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;

/**
 * Plugin to retrieve movie data from Swedish movie database www.filmdelta.se Modified from imdb plugin and Kinopoisk
 * plugin written by Yury Sidorov.
 *
 * @author johan.klinge
 * @version 0.5, 30th April 2009
 */
public class FilmDeltaSEPlugin extends ImdbPlugin {

    private static final Logger logger = Logger.getLogger(FilmDeltaSEPlugin.class);
    private static final String logMessage = "FilmDeltaSEPlugin: ";
    public static String FILMDELTA_PLUGIN_ID = "filmdelta";
    protected TheTvDBPlugin tvdb;
    // Get properties for plotlength
    private int preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");
    private int preferredOutlineLength = PropertiesUtil.getIntProperty("plugin.outline.maxlength", "300");

    public FilmDeltaSEPlugin() {
        super();
        logger.debug(logMessage + "plugin created..");
    }

    @Override
    public String getPluginID() {
        return FILMDELTA_PLUGIN_ID;
    }

    @Override
    public boolean scan(Movie mediaFile) {
        boolean retval = false;
        boolean imdbScanned = false;
        String filmdeltaId = mediaFile.getId(FILMDELTA_PLUGIN_ID);
        String imdbId = mediaFile.getId(ImdbPlugin.IMDB_PLUGIN_ID);

        // if IMDB id is specified in the NFO scan imdb first
        // (to get a valid movie title and improve detection rate
        // for getFilmdeltaId-function)
        if (StringTools.isValidString(imdbId)) {
            super.scan(mediaFile);
            imdbScanned = true;
        }
        // get filmdeltaId (if not already set in nfo)
        if (StringTools.isNotValidString(filmdeltaId)) {
            // find a filmdeltaId (url) from google
            filmdeltaId = getFilmdeltaId(mediaFile.getTitle(), mediaFile.getYear(), mediaFile.getSeason());
            if (StringTools.isValidString(filmdeltaId)) {
                mediaFile.setId(FILMDELTA_PLUGIN_ID, filmdeltaId);
            }
        }
        // always scrape info from imdb or tvdb
        if (mediaFile.isTVShow()) {
            tvdb = new TheTvDBPlugin();
            retval = tvdb.scan(mediaFile);
        } else if (!imdbScanned) {
            retval = super.scan(mediaFile);
        }

        // only scrape filmdelta if a valid filmdeltaId was found
        // and the movie is not a tvshow
        if (StringTools.isValidString(filmdeltaId) && !mediaFile.isTVShow()) {
            retval = updateFilmdeltaMediaInfo(mediaFile, filmdeltaId);
        }

        return retval;
    }

    /*
     * Find id from url in nfo. Format: - http://www.filmdelta.se/filmer/<digits>/<movie_name>/ OR -
     * http://www.filmdelta.se/prevsearch/<text>/filmer/<digits>/<movie_name>
     */
    @Override
    public boolean scanNFO(String nfo, Movie movie) {
        // Always look for imdb id look for ttXXXXXX
        super.scanNFO(nfo, movie);
        logger.debug(logMessage + "Scanning NFO for Filmdelta Id");

        boolean result = true;
        int beginIndex = nfo.indexOf("www.filmdelta.se/prevsearch");
        if (beginIndex != -1) {
            beginIndex = beginIndex + 27;
            String filmdeltaId = makeFilmDeltaId(nfo, beginIndex, 2);
            movie.setId(FilmDeltaSEPlugin.FILMDELTA_PLUGIN_ID, filmdeltaId);
            logger.debug(logMessage + "id found in nfo = " + movie.getId(FilmDeltaSEPlugin.FILMDELTA_PLUGIN_ID));
        } else if (nfo.indexOf("www.filmdelta.se/filmer") != -1) {
            beginIndex = nfo.indexOf("www.filmdelta.se/filmer") + 24;
            String filmdeltaId = makeFilmDeltaId(nfo, beginIndex, 0);
            movie.setId(FilmDeltaSEPlugin.FILMDELTA_PLUGIN_ID, filmdeltaId);
            logger.debug(logMessage + "id found in nfo = " + movie.getId(FilmDeltaSEPlugin.FILMDELTA_PLUGIN_ID));
        } else {
            logger.debug(logMessage + "no id found in nfo for movie: " + movie.getTitle());
            result = false;
        }
        return result;
    }

    /**
     * retrieve FilmDeltaID matching the specified movie name and year. This routine is based on a google request.
     */
    protected String getFilmdeltaId(String movieName, String year, int season) {
        try {
            StringBuilder sb = new StringBuilder("http://www.google.se/search?hl=sv&q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));
            if (StringTools.isValidString(year)) {
                sb.append("+").append(year);
            }
            sb.append(URLEncoder.encode("+site:filmdelta.se/filmer", "UTF-8"));
            String googleHtml = webBrowser.request(sb.toString());
            // String <ul><li is only present in the google page when
            // no matches are found so check if we got a page with results
            if (!googleHtml.contains("<ul><li")) {
                // we have a a google page with valid filmdelta links
                int beginIndex = googleHtml.indexOf("www.filmdelta.se/filmer/") + 24;
                String filmdeltaId = makeFilmDeltaId(googleHtml, beginIndex, 0);
                // regex to match that a valid filmdeltaId contains at least 3 numbers,
                // a dash, and one or more letters (may contain [-&;])
                if (filmdeltaId.matches("\\d{3,}/.+")) {
                    logger.debug(logMessage + "filmdelta id found = " + filmdeltaId);
                    return filmdeltaId;
                } else {
                    logger.info(logMessage + "Found a filmdeltaId but it's not valid. Id: " + filmdeltaId);
                    return Movie.UNKNOWN;
                }
            } else {
                // no valid results for the search
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.error(logMessage + "error retreiving Filmdelta Id for movie : " + movieName);
            logger.error(logMessage + "Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /*
     * Utility method to make a filmdelta id from a string containing a filmdelta url
     */
    private String makeFilmDeltaId(String nfo, int beginIndex, int skip) {
        StringTokenizer st = new StringTokenizer(new String(nfo.substring(beginIndex)), "/");
        for (int i = 0; i < skip; i++) {
            st.nextToken();
        }
        String result = st.nextToken() + "/" + st.nextToken();
        try {
            result = URLDecoder.decode(result, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.warn(logMessage + "in makeFilmDeltaId for string : " + nfo);
            logger.warn(logMessage + "Error : " + e.getMessage());
        }
        return result;
    }

    /*
     * Scan Filmdelta html page for the specified movie
     */
    protected boolean updateFilmdeltaMediaInfo(Movie movie, String filmdeltaId) {

        // fetch filmdelta html page for movie
        String fdeltaHtml = getFilmdeltaHtml(filmdeltaId);

        if (!fdeltaHtml.equals(Movie.UNKNOWN)) {
            getFilmdeltaTitle(movie, fdeltaHtml);
            getFilmdeltaPlot(movie, fdeltaHtml);
            // Genres - only fetch if there is no IMDb results
            if (movie.getGenres().isEmpty()) {
                getFilmdeltaGenres(movie, fdeltaHtml);
            }
            getFilmdeltaDirector(movie, fdeltaHtml);
            getFilmdeltaCast(movie, fdeltaHtml);
            getFilmdeltaCountry(movie, fdeltaHtml);
            getFilmdeltaYear(movie, fdeltaHtml);
            getFilmdeltaRating(movie, fdeltaHtml);
            getFilmdeltaRuntime(movie, fdeltaHtml);
        }
        return true;
    }

    private String getFilmdeltaHtml(String filmdeltaId) {
        String result = Movie.UNKNOWN;
        try {
            logger.debug(logMessage + "searchstring: " + "http://www.filmdelta.se/filmer/" + filmdeltaId);
            result = webBrowser.request("http://www.filmdelta.se/filmer/" + filmdeltaId + "/");
            // logger.debug("result from filmdelta: " + result);

            result = removeIllegalHtmlChars(result);

        } catch (Exception error) {
            logger.error(logMessage + "Failed retreiving movie data from filmdelta.se : " + filmdeltaId);
            logger.error(SystemTools.getStackTrace(error));
        }
        return result;
    }

    /*
     * utility method to remove illegal html characters from the page that is scraped by the webbrower.request(), ugly as hell, gotta be a better way to do
     * this..
     */
    protected String removeIllegalHtmlChars(String result) {
        String cleanResult = result.replaceAll("\u0093", "&quot;");
        cleanResult = cleanResult.replaceAll("\u0094", "&quot;");
        cleanResult = cleanResult.replaceAll("\u00E4", "&auml;");
        cleanResult = cleanResult.replaceAll("\u00E5", "&aring;");
        cleanResult = cleanResult.replaceAll("\u00F6", "&ouml;");
        cleanResult = cleanResult.replaceAll("\u00C4", "&Auml;");
        cleanResult = cleanResult.replaceAll("\u00C5", "&Aring;");
        cleanResult = cleanResult.replaceAll("\u00D6", "&Ouml;");
        return cleanResult;
    }

    private void getFilmdeltaTitle(Movie movie, String fdeltaHtml) {
        if (!movie.isOverrideTitle()) {
            String newTitle = HTMLTools.extractTag(fdeltaHtml, "title>", 0, "<");
            // check if everything is ok
            if (StringTools.isValidString(newTitle)) {
                //split the string so that we get the title at index 0
                String[] titleArray = newTitle.split("-\\sFilmdelta");
                newTitle = titleArray[0];
            } else {
                logger.debug(logMessage + "Error scraping title");
                return;
            }
            String originalTitle = HTMLTools.extractTag(fdeltaHtml, "riginaltitel</h4>", 2);
            logger.debug(logMessage + "scraped title: " + newTitle);
            logger.debug(logMessage + "scraped original title: " + originalTitle);
            if (!newTitle.equals(Movie.UNKNOWN)) {
                movie.setTitle(newTitle.trim());
            }
            if (!originalTitle.equals(Movie.UNKNOWN)) {
                movie.setOriginalTitle(originalTitle);
            }
        }
    }

    protected void getFilmdeltaPlot(Movie movie, String fdeltaHtml) {
        String plot = HTMLTools.extractTag(fdeltaHtml, "<div class=\"text\">", "</p>");
        //strip remaining html tags
        plot = HTMLTools.stripTags(plot);
        if (!plot.equals(Movie.UNKNOWN)) {
            plot = StringTools.trimToLength(plot, preferredPlotLength, true, plotEnding);
            movie.setPlot(plot);
            //CJK 2010-09-15 filmdelta.se has no outlines - set outline to same as plot
            plot = StringTools.trimToLength(plot, preferredOutlineLength, true, plotEnding);
            movie.setOutline(plot);
        } else {
            logger.info(logMessage + "error finding plot for movie: " + movie.getTitle());
        }
    }

    private void getFilmdeltaGenres(Movie movie, String fdeltaHtml) {
        LinkedList<String> newGenres = new LinkedList<String>();

        List<String> filmdeltaGenres = HTMLTools.extractTags(fdeltaHtml, "<h4>Genre</h4>", "</div>", "<h5>", "</h5>");
        for (String genre : filmdeltaGenres) {
            if (genre.length() > 0) {
                genre = new String(genre.substring(0, genre.length() - 5));
                newGenres.add(genre);
            }
        }
        if (!newGenres.isEmpty()) {
            movie.setGenres(newGenres);
            logger.debug(logMessage + "scraped genres: " + movie.getGenres().toString());
        }
    }

    private void getFilmdeltaDirector(Movie movie, String fdeltaHtml) {
        List<String> filmdeltaDirectors = HTMLTools.extractTags(fdeltaHtml, "<h4>Regiss&ouml;r</h4>", "</div>", "<h5>", "</h5>");
        StringBuilder newDirector = new StringBuilder();

        if (!filmdeltaDirectors.isEmpty()) {
            for (String dir : filmdeltaDirectors) {
                dir = new String(dir.substring(0, dir.length() - 4));
                newDirector.append(dir).append(Movie.SPACE_SLASH_SPACE);
            }

            movie.addDirector(newDirector.substring(0, newDirector.length() - 3));
            logger.debug(logMessage + "scraped director: " + movie.getDirector());
        }
    }

    private void getFilmdeltaCast(Movie movie, String fdeltaHtml) {
        Collection<String> newCast = new ArrayList<String>();

        for (String actor : HTMLTools.extractTags(fdeltaHtml, "<h4>Sk&aring;despelare</h4>", "</div>", "<h5>", "</h5>")) {
            String[] newActor = actor.split("</a>");
            newCast.add(newActor[0]);
        }
        if (newCast.size() > 0) {
            movie.setCast(newCast);
            logger.debug(logMessage + "scraped actor: " + movie.getCast().toString());
        }
    }

    private void getFilmdeltaCountry(Movie movie, String fdeltaHtml) {
        String country = HTMLTools.extractTag(fdeltaHtml, "Land, &aring;r, l&auml;ngd", 3);
        movie.setCountry(country);
        logger.debug(logMessage + "scraped country: " + movie.getCountry());
    }

    private void getFilmdeltaYear(Movie movie, String fdeltaHtml) {
        String year = HTMLTools.extractTag(fdeltaHtml, "Land, &aring;r, l&auml;ngd", 5);
        String[] newYear = year.split("\\s");
        if (newYear.length > 1 && !movie.isOverrideYear()) {
            movie.setYear(newYear[1]);
            logger.debug(logMessage + "scraped year: " + movie.getYear());
        } else {
            logger.debug(logMessage + "error scraping year for movie: " + movie.getTitle());
        }
    }

    private void getFilmdeltaRating(Movie movie, String fdeltaHtml) {
        String rating = HTMLTools.extractTag(fdeltaHtml, "<h4>Medlemmarna</h4>", 3, "<");
        int newRating;
        // check if valid rating string is found
        if (rating.indexOf("Snitt") != -1) {
            String[] result = rating.split(":");
            rating = result[result.length - 1];
            logger.debug(logMessage + "filmdelta rating: " + rating);
            // multiply by 20 to make comparable to IMDB-ratings
            newRating = (int) (Float.parseFloat(rating) * 20);
        } else {
            logger.warn(logMessage + "error finding filmdelta rating for movie " + movie.getTitle());
            return;
        }

        if (newRating != 0) {
            movie.addRating(FILMDELTA_PLUGIN_ID, newRating);
        }
    }

    private void getFilmdeltaRuntime(Movie movie, String fdeltaHtml) {
        // Run time
        String runtime = HTMLTools.extractTag(fdeltaHtml, "Land, &aring;r, l&auml;ngd", 7);
        String[] newRunTime = runtime.split("\\s");

        // Issue 1176 - Prevent lost of NFO Data
        if (newRunTime.length > 2 && movie.getRuntime().equals(Movie.UNKNOWN)) {
            movie.setRuntime(newRunTime[1]);
            logger.debug(logMessage + "scraped runtime: " + movie.getRuntime());
        }
    }
}
