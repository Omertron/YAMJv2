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
package com.moviejukebox.plugin;

import com.moviejukebox.model.Identifiable;
import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.SystemTools;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import org.apache.log4j.Logger;

public class FilmUpITPlugin extends ImdbPlugin {

    private static final Logger logger = Logger.getLogger(FilmUpITPlugin.class);
    public static String FILMUPIT_PLUGIN_ID = "filmupit";

    public FilmUpITPlugin() {
        super();
        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "Italy");
    }

    @Override
    public String getPluginID() {
        return FILMUPIT_PLUGIN_ID;
    }
    private int preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");

    /**
     * Scan FilmUp.IT HTML page for the specified movie
     */
    private boolean updateMovieInfo(Movie movie) {
        try {
            String xml = webBrowser.request("http://filmup.leonardo.it/sc_" + movie.getId(FILMUPIT_PLUGIN_ID) + ".htm");

            if (!movie.isOverrideTitle()) {
                movie.setTitle(removeHtmlTags(extractTag(xml, "<font face=\"arial, helvetica\" size=\"3\"><b>", "</b>")));
            }
            // limit plot to FILMUPIT_PLUGIN_PLOT_LENGTH_LIMIT char
            if (movie.getPlot().equals(Movie.UNKNOWN)) {
                String tmpPlot = removeHtmlTags(extractTag(xml, "Trama:<br>", "</font><br>"));
                tmpPlot = StringTools.trimToLength(tmpPlot, preferredPlotLength, true, plotEnding);
                movie.setPlot(tmpPlot);
            }

            if (movie.getDirector().equals(Movie.UNKNOWN)) {
                movie.addDirector(removeHtmlTags(removeHtmlTags(extractTag(xml,
                        "Regia:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">", "</font></td></tr>"))));
            }

            if (movie.getReleaseDate().equals(Movie.UNKNOWN)) {
                movie.setReleaseDate(removeHtmlTags(extractTag(xml,
                        "Data di uscita:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">", "</font></td></tr>")));
            }

            if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                movie.setRuntime(removeHtmlTags(extractTag(xml, "Durata:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">",
                        "</font></td></tr>")));
            }

            if (movie.getCountry().equals(Movie.UNKNOWN)) {
                movie.setCountry(removeHtmlTags(extractTag(xml, "Nazione:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">",
                        "</font></td></tr>")));
            }

            if (movie.getCompany().equals(Movie.UNKNOWN)) {
                movie.setCompany(removeHtmlTags(extractTag(xml,
                        "Distribuzione:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">", "</font></td></tr>")));
            }

            if (movie.getGenres().isEmpty()) {
                for (String tmpGenre : extractTag(xml, "Genere:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">",
                        "</font></td></tr>").split(",")) {
                    for (String genre : tmpGenre.split("/")) {
                        movie.addGenre(genre.trim());
                    }
                }
            }

            if (!movie.isOverrideYear()) {
                movie.setYear(removeHtmlTags(extractTag(xml, "Anno:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">",
                        "</font></td></tr>")));
            }

            if (movie.getCast().isEmpty()) {
                for (String actor : removeHtmlTags(
                        extractTag(xml, "Cast:&nbsp;</font></td><td valign=\"top\"><font face=\"arial, helvetica\" size=\"2\">", "</font></td></tr>")).split(",")) {
                    movie.addActor(actor.trim());
                }
            }

            String opinionsPageID = extractTag(xml, "/opinioni/op.php?uid=", "\"");
            if (StringTools.isValidString(opinionsPageID)) {
                int pageID = Integer.parseInt(opinionsPageID);
                updateRate(movie, pageID);
                logger.debug("Opinions page UID = " + pageID);
            }

            if (downloadFanart && StringTools.isNotValidString(movie.getFanartURL())) {
                movie.setFanartURL(getFanartURL(movie));
                if (StringTools.isValidString(movie.getFanartURL())) {
                    movie.setFanartFilename(movie.getBaseName() + fanartToken + "." + fanartExtension);
                }
            }

        } catch (IOException error) {
            logger.error("Failed retreiving FilmUP infos for movie : " + movie.getId(FILMUPIT_PLUGIN_ID));
            logger.error(SystemTools.getStackTrace(error));
        }
        return true;
    }

    private void updateRate(Movie movie, int opinionsPageID) {
        String baseUrl = "http://filmup.leonardo.it/opinioni/op.php?uid=";
        try {
            String xml = webBrowser.request(baseUrl + opinionsPageID);
            float rating = Float.parseFloat(extractTag(xml, "Media Voto:&nbsp;&nbsp;&nbsp;</td><td align=\"left\"><b>", "</b>")) * 10;
            movie.addRating(FILMUPIT_PLUGIN_ID, (int) rating);
        } catch (IOException error) {
            logger.error("Failed retreiving rating for : " + movie.getId(FILMUPIT_PLUGIN_ID));
            logger.error(SystemTools.getStackTrace(error));
        }
    }

    /*
     *
     *
     * private int parseRating(String rating) { int index =
     * rating.indexOf("etoile_"); try { return (int)
     * (Float.parseFloat(rating.substring(index + 7, index + 8)) / 4.0 * 100); }
     * catch (Exception error) { return -1; } }
     */
    @Override
    public boolean scan(Movie mediaFile) {
        boolean retval = false;
        try {
            String filmUpITId = mediaFile.getId(FILMUPIT_PLUGIN_ID);
            if (StringTools.isNotValidString(filmUpITId)) {
                filmUpITId = getFilmUpITId(mediaFile.getTitle(), mediaFile.getYear(), mediaFile);
            }

            // we also get imdb Id for extra infos
            if (StringTools.isNotValidString(mediaFile.getId(IMDB_PLUGIN_ID))) {
                mediaFile.setId(IMDB_PLUGIN_ID, imdbInfo.getImdbId(mediaFile.getTitle(), mediaFile.getYear()));
                logger.debug("Found imdbId = " + mediaFile.getId(IMDB_PLUGIN_ID));
            }

            if (StringTools.isValidString(filmUpITId)) {
                mediaFile.setId(FILMUPIT_PLUGIN_ID, filmUpITId);
                if (mediaFile.isTVShow()) {
                    super.scan(mediaFile);
                } else {
                    retval = updateMovieInfo(mediaFile);
                }
            } else {
                // If no FilmUpITId found fallback to Imdb
                logger.debug("No Filmup Id available, we fall back to ImdbPlugin");
                retval = super.scan(mediaFile);
            }
        } catch (ParseException error) {
            // If no FilmUpITId found fallback to Imdb
            logger.debug("Parse error in FilmUpITPlugin we fall back to ImdbPlugin");
            retval = super.scan(mediaFile);
        }
        return retval;
    }

    /**
     * retrieve the FilmUpITId matching the specified movie name. This routine
     * is base on a FilmUpIT search.
     *
     * @throws ParseException
     */
    private String getFilmUpITId(String movieName, String year, Identifiable mediaFile) throws ParseException {
        String filmUpITId = Movie.UNKNOWN;

        try {
            StringBuilder sb = new StringBuilder("http://filmup.leonardo.it/cgi-bin/search.cgi?ps=10&fmt=long&q=");
            sb.append(URLEncoder.encode(movieName.replace(' ', '+'), "iso-8859-1"));
            sb.append("&ul=%25%2Fsc_%25&x=0&y=0&m=any&wf=0020&wm=wrd&sy=0");
            String xml = webBrowser.request(sb.toString());

            String filmUpITStartResult;
            String filmUpITMediaPrefix;
            filmUpITStartResult = "<DT>1.";
            filmUpITMediaPrefix = "sc_";

            for (String searchResult : extractTags(xml, filmUpITStartResult, "<DD>", filmUpITMediaPrefix, ".htm")) {
                // logger.debug("SearchResult = " + searchResult);
                return searchResult;
            }

            logger.debug("No ID Found with request : " + sb.toString());
            return Movie.UNKNOWN;

        } catch (Exception error) {
            logger.error("Failed to retrieve FilmUp ID for movie : " + movieName);
            logger.error("We fall back to ImdbPlugin");
            throw new ParseException(filmUpITId, 0);
        }
    }

    protected String extractTag(String src, String tagStart, String tagEnd) {
        int beginIndex = src.indexOf(tagStart);
        if (beginIndex < 0) {
            // logger.debug("extractTag value= Unknown");
            return Movie.UNKNOWN;
        }
        try {
            String subString = src.substring(beginIndex + tagStart.length());
            int endIndex = subString.indexOf(tagEnd);
            if (endIndex < 0) {
                // logger.debug("extractTag value= Unknown");
                return Movie.UNKNOWN;
            }
            subString = subString.substring(0, endIndex);

            String value = HTMLTools.decodeHtml(subString.trim());
            // logger.debug("extractTag value=" + value);
            return value;
        } catch (Exception error) {
            logger.error("extractTag an exception occurred during tag extraction : " + error);
            return Movie.UNKNOWN;
        }
    }

    protected String removeHtmlTags(String src) {
        return src.replaceAll("\\<.*?>", "");
    }

    protected String removeOpenedHtmlTags(String src) {
        String result = src.replaceAll("^.*?>", "");
        result = result.replaceAll("<.*?$", "");
        // logger.debug("removeOpenedHtmlTags before=[" + src + "], after=["+ result + "]");
        return result;
    }

    protected ArrayList<String> extractTags(String src, String sectionStart, String sectionEnd, String startTag, String endTag) {
        ArrayList<String> tags = new ArrayList<String>();
        int index = src.indexOf(sectionStart);
        if (index == -1) {
            // logger.debug("extractTags no sectionStart Tags found");
            return tags;
        }
        index += sectionStart.length();
        int endIndex = src.indexOf(sectionEnd, index);
        if (endIndex == -1) {
            // logger.debug("extractTags no sectionEnd Tags found");
            return tags;
        }

        String sectionText = src.substring(index, endIndex);
        int lastIndex = sectionText.length();
        index = 0;
        int startLen = 0;
        int endLen = endTag.length();

        if (startTag != null) {
            index = sectionText.indexOf(startTag);
            startLen = startTag.length();
        }
        // logger.debug("extractTags sectionText = " + sectionText);
        // logger.debug("extractTags startTag = " + startTag);
        // logger.debug("extractTags startTag index = " + index);
        while (index != -1) {
            index += startLen;
            endIndex = sectionText.indexOf(endTag, index);
            if (endIndex == -1) {
                logger.debug("extractTags no endTag found");
                endIndex = lastIndex;
            }
            String text = sectionText.substring(index, endIndex);
            // logger.debug("extractTags Tag found text = [" + text+"]");

            // replaceAll used because trim() does not trim unicode space
            tags.add(HTMLTools.decodeHtml(text.trim()).replaceAll("^[\\s\\p{Zs}\\p{Zl}\\p{Zp}]*\\b(.*)\\b[\\s\\p{Zs}\\p{Zl}\\p{Zp}]*$", "$1"));
            endIndex += endLen;
            if (endIndex > lastIndex) {
                break;
            }
            if (startTag != null) {
                index = sectionText.indexOf(startTag, endIndex);
            } else {
                index = endIndex;
            }
        }
        return tags;
    }

    protected ArrayList<String> extractHtmlTags(String src, String sectionStart, String sectionEnd, String startTag, String endTag) {
        ArrayList<String> tags = new ArrayList<String>();
        int index = src.indexOf(sectionStart);
        if (index == -1) {
            // logger.debug("extractTags no sectionStart Tags found");
            return tags;
        }
        index += sectionStart.length();
        int endIndex = src.indexOf(sectionEnd, index);
        if (endIndex == -1) {
            // logger.debug("extractTags no sectionEnd Tags found");
            return tags;
        }

        String sectionText = src.substring(index, endIndex);
        int lastIndex = sectionText.length();
        index = 0;
        int endLen = endTag.length();

        if (startTag != null) {
            index = sectionText.indexOf(startTag);
        }
        // logger.debug("extractTags sectionText = " + sectionText);
        // logger.debug("extractTags startTag = " + startTag);
        // logger.debug("extractTags startTag index = " + index);
        while (index != -1) {
            endIndex = sectionText.indexOf(endTag, index);
            if (endIndex == -1) {
                endIndex = lastIndex;
            }
            endIndex += endLen;
            String text = sectionText.substring(index, endIndex);
            // logger.debug("extractTags Tag found text = " + text);
            tags.add(text);
            if (endIndex > lastIndex) {
                break;
            }
            if (startTag != null) {
                index = sectionText.indexOf(startTag, endIndex);
            } else {
                index = endIndex;
            }
        }
        return tags;
    }

    @Override
    public boolean scanNFO(String nfo, Movie movie) {
        // Always look for imdb id look for ttXXXXXX
        super.scanNFO(nfo, movie);

        boolean result = false;
        // If we use FilmUpIT plugin look for
        // http://www.FilmUpIT.fr/...=XXXXX.html
        logger.debug("Scanning NFO for Filmup Id");
        int beginIndex = nfo.indexOf("http://filmup.leonardo.it/");
        if (beginIndex != -1) {
            int beginIdIndex = nfo.indexOf("sc_", beginIndex);
            if (beginIdIndex != -1) {
                int endIdIndex = nfo.indexOf('.', beginIdIndex);
                if (endIdIndex != -1) {
                    logger.debug("Filmup Id found in nfo = " + nfo.substring(beginIdIndex + 3, endIdIndex));
                    movie.setId(FilmUpITPlugin.FILMUPIT_PLUGIN_ID, nfo.substring(beginIdIndex + 3, endIdIndex));
                    result = true;
                } else {
                    logger.debug("No Filmup Id found in nfo !");
                }
            } else {
                logger.debug("No Filmup Id found in nfo !");
            }
        } else {
            logger.debug("No Filmup Id found in nfo !");
        }
        return result;
    }
}
