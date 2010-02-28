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

package com.moviejukebox.plugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;

public class FilmwebPlugin extends ImdbPlugin {

    public static String FILMWEB_PLUGIN_ID = "filmweb";
    private static Logger logger = Logger.getLogger("moviejukebox");
    private static Pattern googlePattern = Pattern.compile("\"(http://[^\"/?&]*filmweb.pl[^\"]*)\"");
    private static Pattern yahooPattern = Pattern.compile("http%3a(//[^\"/?&]*filmweb.pl[^\"]*)\"");
    private static Pattern filmwebPattern = Pattern.compile("searchResultTitle[^>]+\"(http://[^\"/?&]*filmweb.pl[^\"]*)\"");
    private static Pattern nfoPattern = Pattern.compile("http://[^\"/?&]*filmweb.pl[^\\s<>`\"\\[\\]]*");
    private static Pattern longPlotUrlPattern = Pattern.compile("http://[^\"/?&]*filmweb.pl[^\"]*/opisy");

    private static Pattern episodesUrlPattern = Pattern.compile("http://[^\"/?&]*filmweb.pl[^\"]*/odcinki");
    protected String filmwebPreferredSearchEngine;
    protected String filmwebPlot;

    public FilmwebPlugin() {
        super(); // use IMDB if filmweb doesn't know movie
        init();
    }
    private int preferredPlotLength = Integer.parseInt(PropertiesUtil.getProperty("plugin.plot.maxlength", "500"));

    public void init() {
        filmwebPreferredSearchEngine = PropertiesUtil.getProperty("filmweb.id.search", "filmweb");
        filmwebPlot = PropertiesUtil.getProperty("filmweb.plot", "short");
        try {
            // first request to filmweb site to skip welcome screen with ad banner
            webBrowser.request("http://www.filmweb.pl");
        } catch (IOException error) {
            logger.severe("Error : " + error.getMessage());
        }
    }

    public boolean scan(Movie mediaFile) {
        String filmwebUrl = mediaFile.getId(FILMWEB_PLUGIN_ID);
        if (filmwebUrl == null || filmwebUrl.equalsIgnoreCase(Movie.UNKNOWN)) {
            filmwebUrl = getFilmwebUrl(mediaFile.getTitle(), mediaFile.getYear());
            mediaFile.setId(FILMWEB_PLUGIN_ID, filmwebUrl);
        }

        boolean retval;
        if (!filmwebUrl.equalsIgnoreCase(Movie.UNKNOWN)) {
            retval = updateMediaInfo(mediaFile);
        } else {
            // use IMDB if filmweb doesn't know movie
            retval = super.scan(mediaFile);
        }
        return retval;
    }

    /**
     * retrieve the filmweb url matching the specified movie name and year.
     */
    public String getFilmwebUrl(String movieName, String year) {
        if ("google".equalsIgnoreCase(imdbInfo.getPreferredSearchEngine())) {
            return getFilmwebUrlFromGoogle(movieName, year);
        } else if ("yahoo".equalsIgnoreCase(imdbInfo.getPreferredSearchEngine())) {
            return getFilmwebUrlFromYahoo(movieName, year);
        } else if ("none".equalsIgnoreCase(imdbInfo.getPreferredSearchEngine())) {
            return Movie.UNKNOWN;
        } else {
            return getFilmwebUrlFromFilmweb(movieName, year);
        }
    }

    /**
     * retrieve the filmweb url matching the specified movie name and year. This routine is base on a yahoo request.
     */
    private String getFilmwebUrlFromYahoo(String movieName, String year) {
        try {
            StringBuffer sb = new StringBuffer("http://search.yahoo.com/search?p=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
                sb.append("+%28").append(year).append("%29");
            }

            sb.append("+site%3Afilmweb.pl&ei=UTF-8");

            String xml = webBrowser.request(sb.toString());
            Matcher m = yahooPattern.matcher(xml);
            if (m.find()) {
                return "http:" + m.group(1);
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.severe("Failed retreiving filmweb url for movie : " + movieName);
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * retrieve the filmweb url matching the specified movie name and year. This routine is base on a google request.
     */
    private String getFilmwebUrlFromGoogle(String movieName, String year) {
        try {
            StringBuffer sb = new StringBuffer("http://www.google.pl/search?hl=pl&q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
                sb.append("+%28").append(year).append("%29");
            }

            sb.append("+site%3Afilmweb.pl");

            String xml = webBrowser.request(sb.toString());
            Matcher m = googlePattern.matcher(xml);
            if (m.find()) {
                return m.group(1);
            } else {
                return Movie.UNKNOWN;
            }
        } catch (Exception error) {
            logger.severe("Failed retreiving filmweb url for movie : " + movieName);
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * retrieve the filmweb url matching the specified movie name and year. This routine is base on a filmweb request.
     */
    private String getFilmwebUrlFromFilmweb(String movieName, String year) {
        try {
            StringBuffer sb = new StringBuffer("http://www.filmweb.pl/szukaj/film?q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
                sb.append("&startYear=").append(year).append("&endYear=").append(year);
            }
            String xml = webBrowser.request(sb.toString());
            Matcher m = filmwebPattern.matcher(xml);
            if (m.find()) {
                return m.group(1);
            } else {
                return Movie.UNKNOWN;
            }
        } catch (Exception error) {
            logger.severe("Failed retreiving filmweb url for movie : " + movieName);
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Scan IMDB html page for the specified movie
     */
    protected boolean updateMediaInfo(Movie movie) {
        try {
            String xml = webBrowser.request(movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));

            if (HTMLTools.extractTag(xml, "<title>").contains("Serial")) {
                if (!movie.getMovieType().equals(Movie.TYPE_TVSHOW)) {
                    movie.setMovieType(Movie.TYPE_TVSHOW);
                    return false;
                }
            }

            if (!movie.isOverrideTitle()) {
                movie.setTitle(HTMLTools.extractTag(xml, "<title>", 0, "()></"));
                String metaTitle = HTMLTools.extractTag(xml, "<title>");
                if (metaTitle.contains("/")) {
                    String originalTitle = HTMLTools.extractTag(metaTitle, "/", 0, "()><");
                    if (originalTitle.endsWith(", The")) {
                        originalTitle = "The " + originalTitle.substring(0, originalTitle.length() - 5);
                    }
                    movie.setOriginalTitle(originalTitle);
                }
            }

            if (movie.getRating() == -1) {
                movie.setRating(parseRating(HTMLTools.getTextAfterElem(xml, "film-rating-precise")));
            }

            if (movie.getTop250() == -1) {
                try {
                    movie.setTop250(Integer.parseInt(HTMLTools.extractTag(xml, "top świat: #")));
                } catch (NumberFormatException error) {
                    movie.setTop250(-1);
                }
            }

            if (Movie.UNKNOWN.equals(movie.getDirector())) {
                movie.setDirector(HTMLTools.getTextAfterElem(xml, "yseria"));
            }

            if (Movie.UNKNOWN.equals(movie.getReleaseDate())) {
                movie.setReleaseDate(HTMLTools.getTextAfterElem(xml, "data premiery:"));
            }

            if (Movie.UNKNOWN.equals(movie.getRuntime())) {
                movie.setRuntime(HTMLTools.getTextAfterElem(xml, "czas trwania:"));
            }

            if (Movie.UNKNOWN.equals(movie.getCountry())) {
                movie.setCountry(StringUtils.join(HTMLTools.extractTags(xml, "produkcja:", "gatunek", "<a ", "</a>"), ", "));
            }

            if (movie.getGenres().isEmpty()) {
                String genres = HTMLTools.getTextAfterElem(xml, "gatunek:");
                if (!Movie.UNKNOWN.equals(genres)) {
                    for (String genre : genres.split(" *, *")) {
                        movie.addGenre(Library.getIndexingGenre(genre));
                    }
                }
            }

            if (Movie.UNKNOWN.equals(movie.getOutline())) {
                movie.setOutline(HTMLTools.getTextAfterElem(xml, "o-filmie-header", 1));
            }

            if (Movie.UNKNOWN.equals(movie.getPlot())) {
                String plot = Movie.UNKNOWN;
                if (filmwebPlot.equalsIgnoreCase("long")) {
                    plot = getLongPlot(xml);
                }
                // even if "long" is set we will default to the "short" one if none was found
                if (filmwebPlot.equalsIgnoreCase("short") || Movie.UNKNOWN.equals(plot)) {
                    plot = movie.getOutline();
                }

                if (plot.length() > preferredPlotLength) {
                    plot = plot.substring(0, Math.min(plot.length(), preferredPlotLength - 3)) + "...";
                }
                movie.setPlot(plot);
            }

            if (movie.getYear() == null || movie.getYear().isEmpty() || movie.getYear().equalsIgnoreCase(Movie.UNKNOWN)) {
                movie.setYear(HTMLTools.extractTag(xml, "<title>", 1, "()><"));
            }

            if (movie.getCast().isEmpty()) {
                movie.setCast(HTMLTools.extractTags(xml, "film-starring", "</table>", "<img ", "</a>"));
            }

            // Removing Poster info from plugins. Use of PosterScanner routine instead.
            

            // if (movie.getPosterURL() == null || movie.getPosterURL().equalsIgnoreCase(Movie.UNKNOWN)) {
            // movie.setPosterURL(getFilmwebPosterURL(movie, xml));
            // }

            if (movie.isTVShow()) {
                updateTVShowInfo(movie, xml);
            }

            if (downloadFanart && (movie.getFanartURL() == null || movie.getFanartURL().equalsIgnoreCase(Movie.UNKNOWN))) {
                movie.setFanartURL(getFanartURL(movie));
                if (movie.getFanartURL() != null && !movie.getFanartURL().equalsIgnoreCase(Movie.UNKNOWN)) {
                    movie.setFanartFilename(movie.getBaseName() + fanartToken + ".jpg");
                }
            }

        } catch (Exception error) {
            logger.severe("Failed retreiving filmweb informations for movie : " + movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID));
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.severe(eResult.toString());
        }
        return true;
    }

    private int parseRating(String rating) {
        try {
            return Math.round(Float.parseFloat(rating.replace(",", ".")) * 10);
        } catch (Exception error) {
            return -1;
        }
    }

    /**
     * Retrieves the long plot description from filmweb if it exists, else Movie.UNKNOWN
     * 
     * @return long plot
     */
    private String getLongPlot(String mainXML) {
        String plot;
        try {
            // searchs for long plot url
            String longPlotUrl;
            Matcher m = longPlotUrlPattern.matcher(mainXML);
            if (m.find()) {
                longPlotUrl = m.group();
            } else {
                return Movie.UNKNOWN;
            }
            String xml = webBrowser.request(longPlotUrl);
            plot = HTMLTools.getTextAfterElem(xml, "opisy-header", 2);
            if (plot.equalsIgnoreCase(Movie.UNKNOWN)) {
                plot = Movie.UNKNOWN;
            }
        } catch (Exception error) {
            plot = Movie.UNKNOWN;
        }

        return plot;
    }

    private String updateImdbId(Movie movie) {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        if (imdbId == null || imdbId.equalsIgnoreCase(Movie.UNKNOWN)) {
            imdbId = imdbInfo.getImdbId(movie.getTitle(), movie.getYear());
            movie.setId(IMDB_PLUGIN_ID, imdbId);
        }
        return imdbId;
    }



    public void scanTVShowTitles(Movie movie) {
        scanTVShowTitles(movie, null);
    }

    public void scanTVShowTitles(Movie movie, String mainXML) {
        if (!movie.isTVShow() || !movie.hasNewMovieFiles()) {
            return;
        }

        String filmwebUrl = movie.getId(FILMWEB_PLUGIN_ID);
        if (filmwebUrl == null || filmwebUrl.equalsIgnoreCase(Movie.UNKNOWN)) {
            // use IMDB if filmweb doesn't know episodes titles
            super.scanTVShowTitles(movie);
            return;
        }

        try {
            if (mainXML == null) {
                mainXML = webBrowser.request(filmwebUrl);
            }
            // searchs for episodes url
            Matcher m = episodesUrlPattern.matcher(mainXML);
            if (m.find()) {
                String episodesUrl = m.group();
                String xml = webBrowser.request(episodesUrl);
                for (MovieFile file : movie.getMovieFiles()) {
                    if (!file.isNewFile() || file.hasTitle()) {
                        // don't scan episode title if it exists in XML data
                        continue;
                    }
                    int fromIndex = xml.indexOf("sezon " + movie.getSeason());
                    boolean first = true;
                    StringBuilder sb = new StringBuilder();
                    for (int part = file.getFirstPart(); part <= file.getLastPart(); ++part) {
                        String episodeName = HTMLTools.getTextAfterElem(xml, "odcinek " + part, 1, fromIndex);
                        if (!episodeName.equals(Movie.UNKNOWN)) {
                            if (first) {
                                first = false;
                            } else {
                                sb.append(" / ");
                            }
                            sb.append(episodeName);
                        }
                    }
                    String title = sb.toString();
                    if (!"".equals(title)) {
                        file.setTitle(title);
                    }
                }
            } else {
                // use IMDB if filmweb doesn't know episodes titles
                updateImdbId(movie);
                super.scanTVShowTitles(movie);
            }
        } catch (IOException error) {
            logger.severe("Failed retreiving episodes titles for movie : " + movie.getTitle());
            logger.severe("Error : " + error.getMessage());
        }
    }

    protected void updateTVShowInfo(Movie movie, String mainXML) throws MalformedURLException, IOException {
        scanTVShowTitles(movie, mainXML);
    }

    public void scanNFO(String nfo, Movie movie) {
        super.scanNFO(nfo, movie); // use IMDB if filmweb doesn't know movie
        logger.finest("Scanning NFO for filmweb url");
        Matcher m = nfoPattern.matcher(nfo);
        boolean found = false;
        while (m.find()) {
            String url = m.group();
            if (!url.endsWith(".jpg") && !url.endsWith(".jpeg") && !url.endsWith(".gif") && !url.endsWith(".png") && !url.endsWith(".bmp")) {
                found = true;
                movie.setId(FILMWEB_PLUGIN_ID, url);
            }
        }
        if (found) {
            logger.finer("Filmweb url found in nfo = " + movie.getId(FILMWEB_PLUGIN_ID));
        } else {
            logger.finer("No filmweb url found in nfo !");
        }
    }
}
