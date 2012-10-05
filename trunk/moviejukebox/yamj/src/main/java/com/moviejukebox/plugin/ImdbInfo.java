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

import com.moviejukebox.imdbapi.ImdbApi;
import com.moviejukebox.imdbapi.model.ImdbMovieDetails;
import com.moviejukebox.imdbapi.search.SearchObject;
import com.moviejukebox.model.ImdbSiteDataDefinition;
import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

public class ImdbInfo {

    private static final Logger logger = Logger.getLogger(ImdbInfo.class);
    private static final String logMessage = "ImdbInfo: ";
    private static final String DEFAULT_SITE = "us";
    private static final String OBJECT_MOVIE = "movie";
    private static final String OBJECT_PERSON = "person";
    private static final Map<String, ImdbSiteDataDefinition> MATCHES_DATA_PER_SITE = new HashMap<String, ImdbSiteDataDefinition>();
    private final String imdbSite = PropertiesUtil.getProperty("imdb.site", DEFAULT_SITE);
    private String preferredSearchEngine;
    private WebBrowser webBrowser;
    private String objectType = OBJECT_MOVIE;
    private ImdbSiteDataDefinition siteDef;
    private static final String[] SEARCH_ORDER = {"Popular Titles", "Titles (Exact Matches)", "Titles (Partial Matches)", "Titles (Approx Matches)"};

    static {
        MATCHES_DATA_PER_SITE.put("us", new ImdbSiteDataDefinition("http://www.imdb.com/", "ISO-8859-1", "Director|Directed by", "Cast", "Release Date", "Runtime", "Country",
                "Company", "Genre", "Quotes", "Plot", "Rated", "Certification", "Original Air Date", "Writer|Writing credits", "Taglines"));

        MATCHES_DATA_PER_SITE.put("fr", new ImdbSiteDataDefinition("http://www.imdb.fr/", "ISO-8859-1", "R&#xE9;alisateur|R&#xE9;alis&#xE9; par", "Ensemble", "Date de sortie", "Dur&#xE9;e", "Pays",
                "Soci&#xE9;t&#xE9;", "Genre", "Citation", "Intrigue", "Rated", "Classification", "Date de sortie", "Sc&#xE9;naristes|Sc&#xE9;naristes", "Taglines"));

        MATCHES_DATA_PER_SITE.put("es", new ImdbSiteDataDefinition("http://www.imdb.es/", "ISO-8859-1", "Director|Dirigida por", "Reparto", "Fecha de Estreno", "Duraci&#xF3;n", "Pa&#xED;s",
                "Compa&#xF1;&#xED;a", "G&#xE9;nero", "Quotes", "Trama", "Rated", "Clasificaci&#xF3;n", "Fecha de Estreno", "Escritores|Cr&#xE9;ditos del gui&#xF3;n", "Taglines"));

        MATCHES_DATA_PER_SITE.put("de", new ImdbSiteDataDefinition("http://www.imdb.de/", "ISO-8859-1", "Regisseur|Regie", "Besetzung", "Premierendatum", "L&#xE4;nge", "Land",
                "Firma", "Genre", "Quotes", "Handlung", "Rated", "Altersfreigabe", "Premierendatum", "Guionista|Buch", "Taglines"));

        MATCHES_DATA_PER_SITE.put("it", new ImdbSiteDataDefinition("http://www.imdb.it/", "ISO-8859-1", "Regista|Registi|Regia di", "Cast", "Data di uscita", "Durata",
                "Nazionalit&#xE0;", "Compagnia", "Genere", "Quotes", "Trama", "Rated", "Certification", "Data di uscita", "Sceneggiatore|Scritto da", "Taglines"));

        MATCHES_DATA_PER_SITE.put("pt", new ImdbSiteDataDefinition("http://www.imdb.pt/", "ISO-8859-1", "Diretor|Dirigido por", "Elenco", "Data de Lan&#xE7;amento", "Dura&#xE7;&#xE3;o",
                "Pa&#xED;s", "Companhia", "G&#xEA;nero", "Quotes", "Argumento", "Rated", "Certifica&#xE7;&#xE3;o", "Data de Lan&#xE7;amento",
                "Roteirista|Cr&#xE9;ditos como roteirista", "Taglines"));

        // Use this as a workaround for English speakers abroad who get localised versions of imdb.com
        MATCHES_DATA_PER_SITE.put("labs", new ImdbSiteDataDefinition("http://akas.imdb.com/", "ISO-8859-1", "Director|Directors|Directed by", "Cast", "Release Date", "Runtime", "Country",
                "Production Co", "Genres", "Quotes", "Storyline", "Rated", "Certification", "Original Air Date", "Writer|Writers|Writing credits", "Taglines"));

        // TODO: Leaving this as labs.imdb.com for the time being, but will be updated to www.imdb.com
        MATCHES_DATA_PER_SITE.put("us2", new ImdbSiteDataDefinition("http://labs.imdb.com/", "ISO-8859-1", "Director|Directors|Directed by", "Cast", "Release Date", "Runtime", "Country",
                "Production Co", "Genres", "Quotes", "Storyline", "Rated", "Certification", "Original Air Date", "Writer|Writers|Writing credits", "Taglines"));

        // Not 100% sure these are correct
        MATCHES_DATA_PER_SITE.put("it2", new ImdbSiteDataDefinition("http://www.imdb.it/", "ISO-8859-1", "Regista|Registi|Regia di", "Attori", "Data di uscita", "Durata",
                "Nazionalit&#xE0;", "Compagnia", "Genere", "Quotes", "Trama", "Rated", "Certification", "Data di uscita", "Sceneggiatore|Scritto da", "Taglines"));
    }

    public void setPreferredSearchEngine(String preferredSearchEngine) {
        this.preferredSearchEngine = preferredSearchEngine;
    }

    public ImdbInfo() {
        webBrowser = new WebBrowser();

        preferredSearchEngine = PropertiesUtil.getProperty("imdb.id.search", "imdb");
        siteDef = MATCHES_DATA_PER_SITE.get(imdbSite);
        if (siteDef == null) {
            logger.warn(logMessage + "No site definition for " + imdbSite + " using the default instead " + DEFAULT_SITE);
            siteDef = MATCHES_DATA_PER_SITE.get(DEFAULT_SITE);
        }
    }

    /**
     * Retrieve the IMDb matching the specified movie name and year. This
     * routine is based on a IMDb request.
     */
    public String getImdbId(String movieName, String year) {
        objectType = OBJECT_MOVIE;
        if ("google".equalsIgnoreCase(preferredSearchEngine)) {
            return getImdbIdFromGoogle(movieName, year);
        } else if ("yahoo".equalsIgnoreCase(preferredSearchEngine)) {
            return getImdbIdFromYahoo(movieName, year);
        } else if ("none".equalsIgnoreCase(preferredSearchEngine)) {
            return Movie.UNKNOWN;
        } else {
            return getImdbIdFromImdbApi(movieName, year);
        }
    }

    /**
     * Get the IMDb ID for a person. Note: The job is not used in this search.
     *
     * @param movieName
     * @param job
     * @return
     */
    public String getImdbPersonId(String personName, String movieId) {
        try {
            if (StringTools.isValidString(movieId)) {
                StringBuilder sb = new StringBuilder(siteDef.getSite());
                sb.append("search/name?name=");
                sb.append(URLEncoder.encode(personName, siteDef.getCharset().displayName())).append("&role=").append(movieId);

                logger.debug(logMessage + "Querying IMDB for " + sb.toString());
                String xml = webBrowser.request(sb.toString());

                // Check if this is an exact match (we got a person page instead of a results list)
                Pattern titleregex = Pattern.compile(Pattern.quote("<link rel=\"canonical\" href=\"" + siteDef.getSite() + "name/(nm\\d+)/\""));
                Matcher titlematch = titleregex.matcher(xml);
                if (titlematch.find()) {
                    logger.debug(logMessage + "IMDb returned one match " + titlematch.group(1));
                    return titlematch.group(1);
                }

                String firstPersonId = HTMLTools.extractTag(HTMLTools.extractTag(xml, "<tr class=\"even detailed\">", "</tr>"), "<a href=\"/name/", "/\"");
                if (StringTools.isValidString(firstPersonId)) {
                    return firstPersonId;
                }
            }

            return getImdbPersonId(personName);
        } catch (Exception error) {
            logger.error(logMessage + "Failed retreiving IMDb Id for person : " + personName);
            logger.error(logMessage + "Error : " + error.getMessage());
        }

        return Movie.UNKNOWN;
    }

    /**
     * Get the IMDb ID for a person
     *
     * @param movieName
     * @return
     */
    public String getImdbPersonId(String personName) {
        objectType = OBJECT_PERSON;

        if ("google".equalsIgnoreCase(preferredSearchEngine)) {
            return getImdbIdFromGoogle(personName, Movie.UNKNOWN);
        } else if ("yahoo".equalsIgnoreCase(preferredSearchEngine)) {
            return getImdbIdFromYahoo(personName, Movie.UNKNOWN);
        } else if ("none".equalsIgnoreCase(preferredSearchEngine)) {
            return Movie.UNKNOWN;
        } else {
            return getImdbIdFromImdb(personName.toLowerCase(), Movie.UNKNOWN);
        }
    }

    /**
     * Retrieve the IMDb Id matching the specified movie name and year. This
     * routine is base on a yahoo request.
     *
     * @param movieName The name of the Movie to search for
     * @param year The year of the movie
     * @return The IMDb Id if it was found
     */
    private String getImdbIdFromYahoo(String movieName, String year) {
        try {
            StringBuilder sb = new StringBuilder("http://search.yahoo.com/search;_ylt=A1f4cfvx9C1I1qQAACVjAQx.?p=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (StringTools.isValidString(year)) {
                sb.append("+%28").append(year).append("%29");
            }

            sb.append("+site%3Aimdb.com&fr=yfp-t-501&ei=UTF-8&rd=r1");

            String xml = webBrowser.request(sb.toString());
            int beginIndex = xml.indexOf(objectType.equals(OBJECT_MOVIE) ? "/title/tt" : "/name/nm");
            StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 7), "/\"");
            String imdbId = st.nextToken();

            if (imdbId.startsWith(objectType.equals(OBJECT_MOVIE) ? "tt" : "nm")) {
                return imdbId;
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.error(logMessage + "Failed retreiving IMDb Id for movie : " + movieName);
            logger.error(logMessage + "Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Retrieve the IMDb matching the specified movie name and year. This
     * routine is base on a Google request.
     *
     * @param movieName The name of the Movie to search for
     * @param year The year of the movie
     * @return The IMDb Id if it was found
     */
    private String getImdbIdFromGoogle(String movieName, String year) {
        try {
            logger.debug(logMessage + "querying Google for " + movieName);

            StringBuilder sb = new StringBuilder("http://www.google.com/search?q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (StringTools.isValidString(year)) {
                sb.append("+%28").append(year).append("%29");
            }

            sb.append("+site%3Awww.imdb.com&meta=");

            logger.debug(logMessage + "Google search: " + sb.toString());

            String xml = webBrowser.request(sb.toString());
            String imdbId = Movie.UNKNOWN;

            int beginIndex = xml.indexOf(objectType.equals(OBJECT_MOVIE) ? "/title/tt" : "/name/nm");
            if (beginIndex > -1) {
                StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 7), "/\"");
                imdbId = st.nextToken();
            }

            if (imdbId.startsWith(objectType.equals(OBJECT_MOVIE) ? "tt" : "nm")) {
                logger.debug("Found IMDb ID: " + imdbId);
                return imdbId;
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.error(logMessage + "Failed retreiving IMDb Id for movie : " + movieName);
            logger.error(logMessage + "Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Retrieve the IMDb matching the specified movie name and year. This
     * routine is base on a IMDb request.
     */
    private String getImdbIdFromImdb(String movieName, String year) {
        /*
         * IMDb matches seem to come in several "flavours".
         *
         * Firstly, if there is one exact match it returns the matching IMDb page.
         *
         * If that fails to produce a unique hit then a list of possible matches are returned categorised as:
         *      Popular Titles (Displaying ? Results)
         *      Titles (Exact Matches) (Displaying ? Results)
         *      Titles (Partial Matches) (Displaying ? Results)
         *
         * We should check the Exact match section first, then the poplar titles and finally the partial matches.
         *
         * Note: That even with exact matches there can be more than 1 hit, for example "Star Trek"
         */

//        logger.info(logMessage + "Movie Name: '" + movieName + "' (" + year + ")"); // XXX DEBUG

        StringBuilder sb = new StringBuilder(siteDef.getSite());
        sb.append("find?q=");
        try {
            sb.append(URLEncoder.encode(movieName, siteDef.getCharset().displayName()));
        } catch (UnsupportedEncodingException ex) {
            // Failed to encode the movie name for some reason!
            logger.debug(logMessage + "Failed to encode movie name: " + movieName);
            sb.append(movieName);
        }

        if (StringTools.isValidString(year)) {
            sb.append("+%28").append(year).append("%29");
        }
        sb.append(";s=");
        sb.append(objectType.equals(OBJECT_MOVIE) ? "tt" : "nm");
        sb.append(";site=aka");

        logger.debug(logMessage + "Querying IMDB for " + sb.toString());
        String xml;
        try {
            xml = webBrowser.request(sb.toString());
        } catch (IOException ex) {
            logger.error(logMessage + "Failed retreiving IMDb Id for movie : " + movieName);
            logger.error(logMessage + "Error : " + ex.getMessage());
            return Movie.UNKNOWN;
        }

        // Check if this is an exact match (we got a movie page instead of a results list)
        Pattern titleregex = Pattern.compile(Pattern.quote("<link rel=\"canonical\" href=\"" + siteDef.getSite() + (objectType.equals(OBJECT_MOVIE) ? "title/" : "name/")) + (objectType.equals(OBJECT_MOVIE) ? "(tt\\d+)/\"" : "(nm\\d+)/\""));
        Matcher titlematch = titleregex.matcher(xml);
        if (titlematch.find()) {
            logger.debug(logMessage + "IMDb returned one match " + titlematch.group(1));
            return titlematch.group(1);
        }

        if (objectType.equals(OBJECT_MOVIE)) {
            String otherMovieName = HTMLTools.extractTag(HTMLTools.extractTag(xml, ";ttype=ep\">", "\"</a>.</li>"), "<b>", "</b>").toLowerCase();
            String formattedMovieName;
            if (StringTools.isValidString(otherMovieName)) {
                if (StringTools.isValidString(year) && otherMovieName.endsWith(")") && otherMovieName.contains("(")) {
                    otherMovieName = otherMovieName.substring(0, otherMovieName.lastIndexOf('(') - 1);
                    formattedMovieName = otherMovieName + "</a> (" + year + ")";
                } else {
                    formattedMovieName = otherMovieName + "</a>";
                }
            } else {
                sb = new StringBuilder();
                try {
                    sb.append(URLEncoder.encode(movieName, siteDef.getCharset().displayName()).replace("+", " "));
                } catch (UnsupportedEncodingException ex) {
                    logger.debug(logMessage + "Failed to encode movie name: " + movieName);
                    sb.append(movieName);
                }
                sb.append("</a>");
                if (StringTools.isValidString(year)) {
                    sb.append(" (").append(year).append(")");
                }
                otherMovieName = sb.toString();
                formattedMovieName = otherMovieName;
            }

//            logger.debug(logMessage + "Title search: '" + formattedMovieName + "'"); // XXX DEBUG
            for (String searchResult : HTMLTools.extractTags(xml, "<div class=\"media_strip_thumbs\">", "<div id=\"sidebar\">", ".src='/rg/find-" + (objectType.equals(OBJECT_MOVIE) ? "title" : "name") + "-", "</td>", false)) {
//                logger.debug(logMessage + "Title check  : '" + searchResult + "'"); // XXX DEBUG
                if (searchResult.toLowerCase().indexOf(formattedMovieName) != -1) {
//                    logger.debug(logMessage + "Title match  : '" + searchResult + "'"); // XXX DEBUG
                    return HTMLTools.extractTag(searchResult, "/images/b.gif?link=" + (objectType.equals(OBJECT_MOVIE) ? "/title/" : "/name/"), "/';\">");
                } else {
                    for (String otherResult : HTMLTools.extractTags(searchResult, "</';\">", "</p>", "<p class=\"find-aka\">", "</em>", false)) {
                        if (otherResult.toLowerCase().indexOf("\"" + otherMovieName + "\"") != -1) {
//                            logger.debug(logMessage + "Other Title match: '" + otherResult + "'"); // XXX DEBUG
                            return HTMLTools.extractTag(searchResult, "/images/b.gif?link=" + (objectType.equals(OBJECT_MOVIE) ? "/title/" : "/name/"), "/';\">");
                        }
                    }
                }
            }
        } else {
            String firstPersonId = HTMLTools.extractTag(HTMLTools.extractTag(xml, "<table><tr> <td valign=\"top\">", "</td></tr></table>"), "<a href=\"/name/", "/\"");
            if (StringTools.isValidString(firstPersonId)) {
                return firstPersonId;
            }
        }

        // If we don't have an ID try google
        logger.debug(logMessage + "Failed to find an exact match on IMDb, trying Google");
        return getImdbIdFromGoogle(movieName, year);
    }

    /**
     * Retrieve the IMDb ID using the ImdbAPI.
     *
     * @param movieName
     * @param year
     * @return
     */
    private String getImdbIdFromImdbApi(final String movieName, final String year) {
        Map<String, List<SearchObject>> result = ImdbApi.getSearch(movieName);

        if (result == null || result.isEmpty()) {
            logger.debug(logMessage + "No results found for " + movieName);
            return Movie.UNKNOWN;
        }

        String imdbId = Movie.UNKNOWN;
        ImdbMovieDetails imdbMovie;
        for (String searchType : SEARCH_ORDER) {
            imdbMovie = matchSearchObject(result.get(searchType), movieName, year);
            if (imdbMovie != null) {
                logger.debug(logMessage + "Match found in " + searchType + ": " + imdbMovie.getTitle() + " (" + imdbMovie.getYear() + ") = " + imdbMovie.getImdbId());
                imdbId = imdbMovie.getImdbId();
                break;
            }
        }

        return imdbId;
    }

    /**
     * Loops around the search results looking for a close match based on year
     *
     * @param resultList
     * @param movieName
     * @param year
     * @return
     */
    private ImdbMovieDetails matchSearchObject(final List<SearchObject> resultList, final String movieName, final String year) {
        if (resultList == null || (!resultList.isEmpty() && resultList.get(0).getClass() != ImdbMovieDetails.class)) {
            logger.debug(logMessage + "No valid results found for this search type");
            return null;
        }

        for (SearchObject searchResult : resultList) {
            ImdbMovieDetails imdbMovie = (ImdbMovieDetails) searchResult;
            logger.debug(logMessage + "Checking: " + imdbMovie.getTitle() + " (" + imdbMovie.getYear() + ") = " + imdbMovie.getImdbId());
            if (year.equals(String.valueOf(imdbMovie.getYear())) || StringTools.isNotValidString(year)) {
                return (ImdbMovieDetails) searchResult;
            }
        }

        return null;
    }

    public ImdbSiteDataDefinition getSiteDef() {
        return siteDef;
    }

    /**
     * Get a specific site definition from the list
     *
     * @param requiredSiteDef
     * @return The Site definition if found, null otherwise
     */
    public ImdbSiteDataDefinition getSiteDef(String requiredSiteDef) {
        return MATCHES_DATA_PER_SITE.get(requiredSiteDef);
    }

    public String getPreferredSearchEngine() {
        return preferredSearchEngine;
    }

    public String getImdbSite() {
        return imdbSite;
    }
}
