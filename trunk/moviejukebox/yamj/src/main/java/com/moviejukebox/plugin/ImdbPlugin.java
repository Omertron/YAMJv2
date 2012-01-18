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

import static com.moviejukebox.tools.StringTools.isNotValidString;
import static com.moviejukebox.tools.StringTools.isValidString;
import static com.moviejukebox.tools.StringTools.trimToLength;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.moviejukebox.model.Award;
import com.moviejukebox.model.AwardEvent;
import com.moviejukebox.model.Filmography;
import com.moviejukebox.model.Identifiable;
import com.moviejukebox.model.ImdbSiteDataDefinition;
import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.Person;
import com.moviejukebox.scanner.artwork.FanartScanner;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.SystemTools;
import com.moviejukebox.tools.WebBrowser;

public class ImdbPlugin implements MovieDatabasePlugin {

    public static String IMDB_PLUGIN_ID = "imdb";
    protected static Logger logger = Logger.getLogger("moviejukebox");
    protected String preferredCountry;
    private String imdbPlot;
    protected WebBrowser webBrowser;
    protected boolean downloadFanart;
    private boolean extractCertificationFromMPAA;
    private boolean getFullInfo;
    protected String fanartToken;
    protected String fanartExtension;
    private int preferredPlotLength;
    private int preferredBiographyLength;
    private int preferredFilmographyMax;
    private int preferredOutlineLength;
    private int peopleMax;
    private int actorMax;
    private int directorMax;
    private int writerMax;
    private int triviaMax;
    protected ImdbSiteDataDefinition siteDef;
    protected ImdbInfo imdbInfo;
    protected static final String plotEnding = "...";
    private boolean skipVG;
    private boolean skipTV;
    private boolean skipV;
    private List<String> jobsInclude;
    private boolean scrapeAwards;   // Should we scrape the award information
    private boolean scrapeWonAwards;// Should we scrape the won awards only
    private boolean scrapeBusiness; // Should we scrape the business information
    private boolean scrapeTrivia;   // Shoulw we scrape the trivia information

    public ImdbPlugin() {
        imdbInfo = new ImdbInfo();
        siteDef = imdbInfo.getSiteDef();

        webBrowser = new WebBrowser();

        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "USA");
        imdbPlot = PropertiesUtil.getProperty("imdb.plot", "short");
        downloadFanart = PropertiesUtil.getBooleanProperty("fanart.movie.download", "false");
        fanartToken = PropertiesUtil.getProperty("mjb.scanner.fanartToken", ".fanart");
        fanartExtension = PropertiesUtil.getProperty("fanart.format", "jpg");
        preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");
        preferredOutlineLength = PropertiesUtil.getIntProperty("plugin.outline.maxlength", "300");
        extractCertificationFromMPAA = PropertiesUtil.getBooleanProperty("imdb.getCertificationFromMPAA", "true");
        getFullInfo = PropertiesUtil.getBooleanProperty("imdb.full.info", "false");

        preferredBiographyLength = PropertiesUtil.getIntProperty("plugin.biography.maxlength", "500");
        preferredFilmographyMax = PropertiesUtil.getIntProperty("plugin.filmography.max", "20");
        peopleMax = PropertiesUtil.getIntProperty("plugin.people.maxCount", "15");
        actorMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.actor", "10");
        directorMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.director", "2");
        writerMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.writer", "3");
        skipVG = PropertiesUtil.getBooleanProperty("plugin.people.skip.VG", "true");
        skipTV = PropertiesUtil.getBooleanProperty("plugin.people.skip.TV", "false");
        skipV = PropertiesUtil.getBooleanProperty("plugin.people.skip.V", "false");
        jobsInclude = Arrays.asList(PropertiesUtil.getProperty("plugin.filmography.jobsInclude", "Director,Writer,Actor,Actress").split(","));

        triviaMax = PropertiesUtil.getIntProperty("plugin.trivia.maxCount", "15");

        String tmpAwards = PropertiesUtil.getProperty("mjb.scrapeAwards", "false");
        scrapeWonAwards = tmpAwards.equalsIgnoreCase("won");
        scrapeAwards = tmpAwards.equalsIgnoreCase("true") || scrapeWonAwards;

        scrapeBusiness = PropertiesUtil.getBooleanProperty("mjb.scrapeBusiness", "false");
        scrapeTrivia = PropertiesUtil.getBooleanProperty("mjb.scrapeTrivia", "false");
    }

    @Override
    public String getPluginID() {
        return IMDB_PLUGIN_ID;
    }

    @Override
    public boolean scan(Movie movie) {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        if (isNotValidString(imdbId)) {
            imdbId = imdbInfo.getImdbId(movie.getTitle(), movie.getYear());
            movie.setId(IMDB_PLUGIN_ID, imdbId);
        }

        boolean retval = false;
        if (isValidString(imdbId)) {
            retval = updateImdbMediaInfo(movie);
        }
        return retval;
    }

    protected String getPreferredValue(ArrayList<String> values) {
        String value = Movie.UNKNOWN;
        for (String text : values) {
            String country = null;

            int pos = text.indexOf(':');
            if (pos != -1) {
                country = text.substring(0, pos);
                text = text.substring(pos + 1);
            }
            pos = text.indexOf('(');
            if (pos != -1) {
                text = text.substring(0, pos).trim();
            }

            if (country == null) {
                if (value.equals(Movie.UNKNOWN)) {
                    value = text;
                }
            } else {
                if (country.equals(preferredCountry)) {
                    value = text;
                    // No need to continue scanning
                    break;
                }
            }
        }
        return HTMLTools.stripTags(value);
    }

    /**
     * Scan IMDB HTML page for the specified movie
     */
    private boolean updateImdbMediaInfo(Movie movie) {
        String imdbID = movie.getId(IMDB_PLUGIN_ID);
        boolean imdbNewVersion = false; // Used to fork the processing for the new version of IMDb
        boolean returnStatus = false;

        try {
            if (!imdbID.startsWith("tt")) {
                imdbID = "tt" + imdbID;
                // Correct the ID if it's wrong
                movie.setId(IMDB_PLUGIN_ID, imdbID);
            }

            String xml = getImdbUrl(movie);

            // Add the combined tag to the end of the request if required
            if (getFullInfo) {
                xml += "combined";
            }

            xml = webBrowser.request(xml, siteDef.getCharset());

            if (xml.contains("\"tv-extra\"")) {
                if (!movie.getMovieType().equals(Movie.TYPE_TVSHOW)) {
                    movie.setMovieType(Movie.TYPE_TVSHOW);
                    return false;
                }
            }

            // We can work out if this is the new site by looking for " - IMDb" at the end of the title
            String title = HTMLTools.extractTag(xml, "<title>");
            title = title.replaceAll(" \\([VG|V]\\)$", ""); // Remove the (VG) or (V) tags from the title

            //String yearPattern = ".\\((?:TV )?(\\d{4})(?:/[^\\)]+)?\\)";
            String yearPattern = "(?i).\\((?:TV.|VIDEO.)?(\\d{4})(?:/[^\\)]+)?\\)";
            Pattern pattern = Pattern.compile(yearPattern, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                // If we've found a year, set it in the movie
                movie.setYear(matcher.group(1));

                // Remove the year from the title. Removes "(TV)" and "(TV YEAR)"
                title = title.replaceAll(yearPattern, "");
            }

            // Set the version of IMDb to scrape
            imdbNewVersion = false;

            // Check for the new version and correct the title if found.
            if (title.toLowerCase().endsWith(" - imdb")) {
                title = new String(title.substring(0, title.length() - 7));
                imdbNewVersion = true;
            }

            if (title.toLowerCase().startsWith("imdb - ")) {
                title = new String(title.substring(7));
                imdbNewVersion = true;
            }

            String originalTitle = title;
            if (xml.indexOf("<span class=\"title-extra\">") > -1) {
                originalTitle = HTMLTools.extractTag(xml, "<span class=\"title-extra\">", "</span>");
                if (originalTitle.indexOf("(original title)") > -1) {
                    originalTitle = originalTitle.replace(" <i>(original title)</i>", "");
                } else {
                    originalTitle = title;
                }
            }

            if (!movie.isOverrideTitle()) {
                movie.setTitle(title);
                movie.setOriginalTitle(originalTitle);
            }

            if (imdbNewVersion) {
                returnStatus = updateInfoNew(movie, xml);
            } else {
                returnStatus = updateInfoOld(movie, xml);
            }

            if (scrapeAwards) {
                updateAwards(movie);        // Issue 1901: Awards
            }

            if (scrapeBusiness) {
                updateBusiness(movie);      // Issue 2012: Financial information about movie
            }

            if (scrapeTrivia) {
                updateTrivia(movie);        // Issue 2013: Add trivia
            }

            // TODO: Remove this check at some point when all skins have moved over to the new property
            downloadFanart = FanartScanner.checkDownloadFanart(movie.isTVShow());

            // TODO: Move this check out of here, it doesn't belong.
            if (downloadFanart && isNotValidString(movie.getFanartURL())) {
                movie.setFanartURL(getFanartURL(movie));
                if (isValidString(movie.getFanartURL())) {
                    movie.setFanartFilename(movie.getBaseName() + fanartToken + "." + fanartExtension);
                }
            }

        } catch (Exception error) {
            logger.error("Failed retreiving IMDb data for movie : " + movie.getId(IMDB_PLUGIN_ID));
            logger.error(SystemTools.getStackTrace(error));
        }
        return returnStatus;
    }

    /**
     * Process the old IMDb formatted web page
     * @param movie
     * @param xml
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    @Deprecated
    private boolean updateInfoOld(Movie movie, String xml) throws MalformedURLException, IOException {
        if (movie.getRating() == -1) {
            String rating = HTMLTools.extractTag(xml, "<div class=\"starbar-meta\">", "</b>").replace(",", ".");
            movie.addRating(IMDB_PLUGIN_ID, parseRating(HTMLTools.stripTags(rating)));
        }

        if (movie.getTop250() == -1) {
            try {
                movie.setTop250(Integer.parseInt(HTMLTools.extractTag(xml, "Top 250: #")));
            } catch (NumberFormatException error) {
                movie.setTop250(-1);
            }
        }

        if (movie.getReleaseDate().equals(Movie.UNKNOWN)) {
            movie.setReleaseDate(HTMLTools.extractTag(xml, "<h5>" + siteDef.getReleaseDate() + ":</h5>", 1));
        }

        if (movie.getRuntime().equals(Movie.UNKNOWN)) {
            movie.setRuntime(getPreferredValue(HTMLTools.extractTags(xml, "<h5>" + siteDef.getRuntime() + ":</h5>")));
        }

        if (movie.getCountry().equals(Movie.UNKNOWN)) {
            // HTMLTools.extractTags(xml, "<h5>" + siteDef.getCountry() + ":</h5>", "</div>", "<a href", "</a>")
            for (String country : HTMLTools.extractTags(xml, "<h5>" + siteDef.getCountry() + ":</h5>", "</div>")) {
                if (country != null) {
                    // TODO Save more than one country
                    movie.setCountry(HTMLTools.removeHtmlTags(country));
                    break;
                }
            }
        }

        if (movie.getCompany().equals(Movie.UNKNOWN)) {
            for (String company : HTMLTools.extractTags(xml, "<h5>" + siteDef.getCompany() + ":</h5>", "</div>", "<a href", "</a>")) {
                if (company != null) {
                    // TODO Save more than one company
                    movie.setCompany(company);
                    break;
                }
            }
        }

        if (movie.getGenres().isEmpty()) {
            for (String genre : HTMLTools.extractTags(xml, "<h5>" + siteDef.getGenre() + ":</h5>", "</div>")) {
                genre = HTMLTools.removeHtmlTags(genre);
                movie.addGenre(Library.getIndexingGenre(cleanStringEnding(genre)));
            }
        }

        if (movie.getQuote().equals(Movie.UNKNOWN)) {
            for (String quote : HTMLTools.extractTags(xml, "<h5>" + siteDef.getQuotes() + ":</h5>", "</div>", "<a href=\"/name/nm", "</a class=\"")) {
                if (quote != null) {
                    quote = HTMLTools.stripTags(quote);
                    movie.setQuote(cleanStringEnding(quote));
                    break;
                }
            }
        }

        String imdbOutline = Movie.UNKNOWN;
        int plotBegin = xml.indexOf(("<h5>" + siteDef.getPlot() + ":</h5>"));
        if (plotBegin > -1) {
            plotBegin += ("<h5>" + siteDef.getPlot() + ":</h5>").length();
            // search "<a " for the international variety of "more" oder "add synopsis"
            int plotEnd = xml.indexOf("<a ", plotBegin);
            int plotEndOther = xml.indexOf("</a>", plotBegin);
            if (plotEnd > -1 || plotEndOther > -1) {
                if ((plotEnd > -1 && plotEndOther < plotEnd) || plotEnd == -1) {
                    plotEnd = plotEndOther;
                }

                String outline = HTMLTools.stripTags(xml.substring(plotBegin, plotEnd)).trim();
                if (outline.length() > 0) {
                    if (outline.endsWith("|")) {
                        // Remove the bar character from the end of the plot
                        outline = outline.substring(0, outline.length() - 1);
                    }

                    if (isValidString(outline)) {
                        imdbOutline = cleanStringEnding(outline);
                        imdbOutline = trimToLength(imdbOutline, preferredOutlineLength, true, plotEnding);
                    } else {
                        // Ensure the outline isn't blank or null
                        imdbOutline = Movie.UNKNOWN;
                    }
                }
            }
        }

        if (movie.getOutline().equals(Movie.UNKNOWN)) {
            movie.setOutline(imdbOutline);
        }

        if (movie.getPlot().equals(Movie.UNKNOWN)) {
            String plot = Movie.UNKNOWN;
            if (imdbPlot.equalsIgnoreCase("long")) {
                plot = getLongPlot(movie);
            }

            // even if "long" is set we will default to the "short" one if none was found
            if (plot.equals(Movie.UNKNOWN)) {
                plot = imdbOutline;
            }

            movie.setPlot(plot);
        }

        String certification = movie.getCertification();
        if (certification.equals(Movie.UNKNOWN)) {
            if (extractCertificationFromMPAA) {
                String mpaa = HTMLTools.extractTag(xml, "<h5><a href=\"/mpaa\">MPAA</a>:</h5>", 1);
                if (!mpaa.equals(Movie.UNKNOWN)) {
                    String key = siteDef.getRated() + " ";
                    int pos = mpaa.indexOf(key);
                    if (pos != -1) {
                        int start = key.length();
                        pos = mpaa.indexOf(" on appeal for ", start);
                        if (pos == -1) {
                            pos = mpaa.indexOf(" for ", start);
                        }
                        if (pos != -1) {
                            certification = mpaa.substring(start, pos);
                        }
                    }
                }
            }

            if (isNotValidString(certification)) {
                certification = getPreferredValue(HTMLTools.extractTags(xml, "<h5>" + siteDef.getCertification() + ":</h5>", "</div>",
                        "<a href=\"/search/title?certificates=", "</a>"));
            }

            if (isNotValidString(certification)) {
                certification = Movie.NOTRATED;
            }

            movie.setCertification(certification);
        }

        // Get year of movie from IMDb site
        if (!movie.isOverrideYear()) {
            Pattern getYear = Pattern.compile("(?:\\s*" + "\\((\\d{4})(?:/[^\\)]+)?\\)|<a href=\"/year/(\\d{4}))");
            Matcher m = getYear.matcher(xml);
            if (m.find()) {
                String Year = m.group(1);
                if (Year == null || Year.isEmpty()) {
                    Year = m.group(2);
                }

                if (Year != null && !Year.isEmpty()) {
                    movie.setYear(Year);
                }
            }
        }

        if (!movie.isOverrideYear() && isNotValidString(movie.getYear())) {
            movie.setYear(HTMLTools.extractTag(xml, "<a href=\"/year/", 1));
            if (isNotValidString(movie.getYear())) {
                String fullReleaseDate = HTMLTools.getTextAfterElem(xml, "<h5>" + siteDef.getOriginalAirDate() + ":</h5>", 0);
                if (isValidString(fullReleaseDate)) {
                    movie.setYear(fullReleaseDate.split(" ")[2]);
                }
                // HTMLTools.extractTag(xml, "<h5>" + siteDef.getOriginal_air_date() + ":</h5>", 2, " ")
            }
        }

        String personXML = webBrowser.request(getImdbUrl(movie, siteDef) + "fullcredits", siteDef.getCharset());
        int peopleCount = 0;

        if (movie.getPerson("Directing").isEmpty() && directorMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            if (isValidString(personXML)) {
                peopleCount += extractDirectors(movie, personXML, siteDef);
            } else {
                // Note this is a hack for the change to IMDB for Issue 875
                //ArrayList<String> tempDirectors = null;
                // Issue 1261 : Allow multiple text matching for one "element".
                String[] directorMatches = siteDef.getDirector().split("\\|");

                int count = 0;
                boolean found = false;
                for (String directorMatch : directorMatches) {
                    for (String member : HTMLTools.extractTags(xml, "<h5>" + directorMatch, "</div>", "", "</a>")) {
                        int beginIndex = member.indexOf("<a href=\"/name/");
                        if (beginIndex > -1) {
                            String personID = member.substring(beginIndex + 15, member.indexOf("/\"", beginIndex));
                            movie.addDirector(IMDB_PLUGIN_ID + ":" + personID, member.substring(member.indexOf("\">", beginIndex) + 2), siteDef.getSite() + "name/" + personID + "/");
                            found = true;
                            count++;
                            if (count == directorMax) {
                                break;
                            }
                        }
                    }
                    if (found) {
                        // We found a match, so stop search.
                        break;
                    }
                }
                peopleCount += count;
            }
        }

        /** Check for writer(s) **/
        if (movie.getPerson("Writing").isEmpty() && writerMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            if (isValidString(personXML)) {
                peopleCount += extractWriters(movie, personXML, siteDef);
            } else {
                int count = 0;
                boolean found = false;
                for (String categoryMatch : siteDef.getWriter().split("\\|")) {
                    for (String member : HTMLTools.extractTags(xml, "<h5>" + categoryMatch, "</div>", "", "</a>")) {
                        int beginIndex = member.indexOf("<a href=\"/name/");
                        if (beginIndex > -1) {
                            String personID = member.substring(beginIndex + 15, member.indexOf("/\"", beginIndex));
                            movie.addWriter(IMDB_PLUGIN_ID + ":" + personID, member.substring(member.indexOf("\">", beginIndex) + 2), siteDef.getSite() + "name/" + personID + "/");
                            found = true;
                            count++;
                            if (count == writerMax) {
                                break;
                            }
                        }
                    }
                    if (found) {
                        // We found a match, so stop search.
                        break;
                    }
                }
                peopleCount += count;
            }
        }

        if (movie.getPerson("Actors").isEmpty() && actorMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            int count = 0;
            for (String actorBlock : HTMLTools.extractTags(xml, "<table class=\"cast\">", "</table>", "<td class=\"nm\"", "</tr>")) {
                String personID = actorBlock.substring(actorBlock.indexOf("\"/name/") + 7, actorBlock.indexOf("/\""));
                int beginIndex = actorBlock.indexOf("<td class=\"char\">");
                String character = Movie.UNKNOWN;
                if (beginIndex > 0) {
                    if (actorBlock.indexOf("<a href=\"/character/") > 0) {
                        character = actorBlock.substring(actorBlock.indexOf("/\">", beginIndex) + 3, actorBlock.indexOf("</a>", beginIndex));
                    } else {
                        character = actorBlock.substring(actorBlock.indexOf("\">", beginIndex) + 2, actorBlock.indexOf("</td>", beginIndex));
                    }
                }
                movie.addActor(IMDB_PLUGIN_ID + ":" + personID, actorBlock.substring(actorBlock.indexOf("\">") + 2, actorBlock.indexOf("</a>")), character, siteDef.getSite() + "name/" + personID + "/", Movie.UNKNOWN);
                count++;
                if (count == actorMax || peopleCount + count == peopleMax) {
                    break;
                }
            }
        }

        if (movie.isTVShow()) {
            updateTVShowInfo(movie);
        }

        // TODO: Remove this check at some point when all skins have moved over to the new property
        downloadFanart = FanartScanner.checkDownloadFanart(movie.isTVShow());

        if (downloadFanart && isNotValidString(movie.getFanartURL())) {
            movie.setFanartURL(getFanartURL(movie));
            if (isValidString(movie.getFanartURL())) {
                movie.setFanartFilename(movie.getBaseName() + fanartToken + "." + fanartExtension);
            }
        }

        return true;
    }

    /**
     * Process the new IMDb format web page
     * @param movie
     * @param xml
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private boolean updateInfoNew(Movie movie, String xml) throws MalformedURLException, IOException {
        logger.debug("ImdbPlugin: Detected new IMDb format for '" + movie.getBaseName() + "'");
        Collection<String> peopleList;
        String releaseInfoXML = Movie.UNKNOWN;  // Store the release info page for release info & AKAs
        ImdbSiteDataDefinition siteDef2;

        // If we are using sitedef=labs, there's no need to change it

        if (imdbInfo.getImdbSite().equals("labs")) {
            siteDef2 = this.siteDef;
        } else {
            // Overwrite the normal siteDef with a v2 siteDef if it exists
            siteDef2 = imdbInfo.getSiteDef(imdbInfo.getImdbSite() + "2");
            if (siteDef2 == null) {
                // c2 siteDef doesn't exist, so use labs to atleast return something
                logger.error("ImdbPlugin: No new format definition found for language '" + imdbInfo.getImdbSite() + "' using default language instead.");
                siteDef2 = imdbInfo.getSiteDef("labs");
            }
        }

        // RATING
        if (movie.getRating(IMDB_PLUGIN_ID) == -1) {
            String srtRating = HTMLTools.extractTag(xml, "star-box-giga-star\">", "</div>").replace(",", ".");
            int intRating = parseRating(HTMLTools.stripTags(srtRating));

            // Try another format for the rating
            if (intRating == -1) {
                srtRating = HTMLTools.extractTag(xml, "star-bar-user-rate\">", "</span>").replace(",", ".");
                intRating = parseRating(HTMLTools.stripTags(srtRating));
            }

            movie.addRating(IMDB_PLUGIN_ID, intRating);
        }

        // TOP250
        int currentTop250 = movie.getTop250();
        // Check to see if the top250 is empty or the movie needs re-checking (in which case overwrite it)
        if (currentTop250 == -1 || movie.isDirty(Movie.DIRTY_RECHECK)) {
            try {
                movie.setTop250(Integer.parseInt(HTMLTools.extractTag(xml, "Top 250 #")));
            } catch (NumberFormatException error) {
                // We failed to convert the value, so replace it with the old one.
                movie.setTop250(currentTop250);
            }
        }

        // RELEASE DATE
        if (movie.getReleaseDate().equals(Movie.UNKNOWN)) {
            // Load the release page from IMDb
            if (releaseInfoXML.equals(Movie.UNKNOWN)) {
                releaseInfoXML = webBrowser.request(getImdbUrl(movie, siteDef2) + "releaseinfo", siteDef2.getCharset());
            }
            movie.setReleaseDate(HTMLTools.stripTags(HTMLTools.extractTag(releaseInfoXML, "\">" + preferredCountry, "</a></td>")).trim());

            // Check to see if there's a 4 digit year in the release date and terminate at that point
            Matcher m = Pattern.compile(".*?\\d{4}+").matcher(movie.getReleaseDate());
            if (m.find()) {
                movie.setReleaseDate(m.group(0));
            }
        }

        // RUNTIME
        if (movie.getRuntime().equals(Movie.UNKNOWN)) {
            String runtime = siteDef2.getRuntime() + ":</h4>";
            ArrayList<String> runtimes = HTMLTools.extractTags(xml, runtime, "</div>", null, "|", false);
            runtime = getPreferredValue(runtimes);

            // Strip any extraneous characters from the runtime
            int pos = runtime.indexOf("min");
            if (pos > 0) {
                runtime = runtime.substring(0, pos + 3);
            }
            movie.setRuntime(runtime);
        }

        // COUNTRY
        if (movie.getCountry().equals(Movie.UNKNOWN)) {
            for (String country : HTMLTools.extractTags(xml, siteDef2.getCountry() + ":</h4>", "</div>", "<a href", "</a>")) {
                if (country != null) {
                    // TODO Save more than one country
                    movie.setCountry(HTMLTools.removeHtmlTags(country));
                    break;
                }
            }
        }

        // COMPANY
        if (movie.getCompany().equals(Movie.UNKNOWN)) {
            for (String company : HTMLTools.extractTags(xml, siteDef2.getCompany() + ":</h4>", "<span class", "<a ", "</a>")) {
                if (company != null) {
                    // TODO Save more than one company
                    movie.setCompany(company);
                    break;
                }
            }
        }

        // GENRES
        if (movie.getGenres().isEmpty()) {
            for (String genre : HTMLTools.extractTags(xml, siteDef2.getGenre() + ":</h4>", "</div>", "href=\"/genre/", "</a>")) {
                movie.addGenre(Library.getIndexingGenre(HTMLTools.removeHtmlTags(genre)));
            }
        }

        // QUOTE
        if (movie.getQuote().equals(Movie.UNKNOWN)) {
            for (String quote : HTMLTools.extractTags(xml, "<h4>" + siteDef2.getQuotes() + "</h4>", "<span class=\"", "<a ", "<br")) {
                if (quote != null) {
                    quote = HTMLTools.stripTags(quote);
                    movie.setQuote(cleanStringEnding(quote));
                    break;
                }
            }
        }

        // OUTLINE
        if (movie.getOutline().equals(Movie.UNKNOWN)) {
            // The new outline is at the end of the review section with no preceding text
            String imdbOutline = HTMLTools.extractTag(xml, "<p itemprop=\"description\">", "</p>");
            imdbOutline = cleanStringEnding(HTMLTools.removeHtmlTags(imdbOutline)).trim();

            if (isNotValidString(imdbOutline)) {
                // ensure the outline is set to unknown if it's blank or null
                imdbOutline = Movie.UNKNOWN;
            }
            movie.setOutline(imdbOutline);
        }


        // PLOT
        if (movie.getPlot().equals(Movie.UNKNOWN)) {
            String xmlPlot = Movie.UNKNOWN;

            if (imdbPlot.equalsIgnoreCase("long")) {
                // The new plot is now called Storyline
                xmlPlot = HTMLTools.extractTag(xml, "<h2>" + siteDef2.getPlot() + "</h2>", "<em class=\"nobr\">");
                xmlPlot = HTMLTools.removeHtmlTags(xmlPlot).trim();

                // This plot didn't work, look for another version
                if (isNotValidString(xmlPlot)) {
                    xmlPlot = HTMLTools.extractTag(xml, "<h2>" + siteDef2.getPlot() + "</h2>", "<span class=\"");
                    xmlPlot = HTMLTools.removeHtmlTags(xmlPlot).trim();
                }

                // This plot didn't work, look for another version
                if (isNotValidString(xmlPlot)) {
                    xmlPlot = HTMLTools.extractTag(xml, "<h2>" + siteDef2.getPlot() + "</h2>", "<p>");
                    xmlPlot = HTMLTools.removeHtmlTags(xmlPlot).trim();
                }

                // See if the plot has the "metacritic" text and remove it
                int pos = xmlPlot.indexOf("Metacritic.com)");
                if (pos > 0) {
                    xmlPlot = xmlPlot.substring(pos + "Metacritic.com)".length());
                }

                // Check the length of the plot is OK
                if (isValidString(xmlPlot)) {
                    xmlPlot = cleanStringEnding(xmlPlot);
                    xmlPlot = trimToLength(xmlPlot, preferredPlotLength, true, plotEnding);
                } else {
                    // The plot might be blank or null so set it to UNKNOWN
                    xmlPlot = Movie.UNKNOWN;
                }
            }

            // Update the plot with the found plot, or the outline if not found
            if (isValidString(xmlPlot)) {
                movie.setPlot(xmlPlot);
            } else {
                movie.setPlot(movie.getOutline());
            }
        }

        // CERTIFICATION
        String certification = movie.getCertification();
        if (certification.equals(Movie.UNKNOWN)) {
            String certXML = webBrowser.request(getImdbUrl(movie, siteDef2) + "parentalguide#certification", siteDef2.getCharset());
            if (extractCertificationFromMPAA) {
                String mpaa = HTMLTools.extractTag(certXML, "<h5><a href=\"/mpaa\">MPAA</a>:</h5>", 1);
                if (!mpaa.equals(Movie.UNKNOWN)) {
                    String key = siteDef2.getRated() + " ";
                    int pos = mpaa.indexOf(key);
                    if (pos != -1) {
                        int start = key.length();
                        pos = mpaa.indexOf(" on appeal for ", start);
                        if (pos == -1) {
                            pos = mpaa.indexOf(" for ", start);
                        }
                        if (pos != -1) {
                            certification = mpaa.substring(start, pos);
                        }
                    }
                }
            }

            if (isNotValidString(certification)) {
                certification = getPreferredValue(HTMLTools.extractTags(certXML, "<h5>" + siteDef2.getCertification() + ":</h5>", "</div>",
                        "<a href=\"/search/title?certificates=", "</a>"));
            }

            if (isNotValidString(certification)) {
                certification = Movie.NOTRATED;
            }

            movie.setCertification(certification);
        }

        // Get year of IMDb site
        if (!movie.isOverrideYear()) {
            Pattern getYear = Pattern.compile("(?:\\s*" + "\\((\\d{4})(?:/[^\\)]+)?\\)|<a href=\"/year/(\\d{4}))");
            Matcher m = getYear.matcher(xml);
            if (m.find()) {
                String Year = m.group(1);
                if (isNotValidString(Year)) {
                    Year = m.group(2);
                }

                if (isValidString(Year) && isNotValidString(movie.getYear())) {
                    movie.setYear(Year);
                }
            }
        }

        if (!movie.isOverrideYear() && (isNotValidString(movie.getYear()))) {
            movie.setYear(HTMLTools.extractTag(xml, "<a href=\"/year/", 1));

            if (isNotValidString(movie.getYear())) {
                String fullReleaseDate = HTMLTools.getTextAfterElem(xml, "<h5>" + siteDef2.getOriginalAirDate() + ":</h5>", 0);

                if (isValidString(fullReleaseDate)) {
                    movie.setYear(fullReleaseDate.split(" ")[2]);
                }
                // HTMLTools.extractTag(xml, "<h5>" + siteDef.getOriginal_air_date() + ":</h5>", 2, " ")
            }
        }

        String personXML = webBrowser.request(getImdbUrl(movie, siteDef2) + "fullcredits", siteDef2.getCharset());
        int peopleCount = 0;

        // DIRECTOR(S)
        if (movie.getPerson("Directing").isEmpty() && directorMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            peopleCount += extractDirectors(movie, personXML, siteDef2);
        }

        // WRITER(S)
        if (movie.getPerson("Writing").isEmpty() && writerMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            peopleCount += extractWriters(movie, personXML, siteDef2);
        }

        // CAST
        if (movie.getPerson("Actors").isEmpty() && actorMax > 0 && peopleCount < peopleMax) {
            // Issue 1897: Cast enhancement
            int count = 0;
            peopleList = HTMLTools.extractTags(xml, "<table class=\"cast_list\">", "</table>", "<td class=\"name\"", "</tr>");

            if (peopleList.isEmpty()) {
                // Try an alternative search
                peopleList = HTMLTools.extractTags(xml, "<table class=\"cast_list\">", "</table>", "<td class=\"nm\"", "</tr>", true);
            }

            if (peopleList.isEmpty()) {
                // old algorithm
                String castMember;

                peopleList = HTMLTools.extractTags(xml, "<table class=\"cast_list\">", "</table>", "/name/nm", "</a>", true);

                // Clean up the cast list that is returned
                for (Iterator<String> iter = peopleList.iterator(); iter.hasNext();) {
                    castMember = iter.next();
                    if (castMember.indexOf("src=") > -1) {
                        iter.remove();
                    } else {
                        // Add the cleaned up cast member to the movie
                        movie.addActor(HTMLTools.stripTags(castMember));
                        count++;
                        if (count == actorMax || peopleCount + count == peopleMax) {
                            break;
                        }
                    }
                }
            } else {
                for (String actorBlock : peopleList) {
                    String personID = actorBlock.substring(actorBlock.indexOf("\"/name/") + 7, actorBlock.indexOf("/\""));
                    int beginIndex = actorBlock.indexOf("<td class=\"character\">");
                    String name = HTMLTools.extractTag(actorBlock, "<a ", 1);
                    String character = Movie.UNKNOWN;
                    if (beginIndex > 0) {
                        if (actorBlock.indexOf("<a href=\"/character/") > 0) {
                            character = HTMLTools.extractTag(actorBlock, "<a href=\"/character/", 1);
                        } else {
                            character = HTMLTools.removeHtmlTags(actorBlock.substring(actorBlock.indexOf(">", beginIndex) + 2, actorBlock.indexOf("</td>", beginIndex)));
                            character = character.replaceAll("\\s+", " ").replaceAll("^\\s", "").replaceAll("\\s$", "");
                        }
                    }
                    movie.addActor(IMDB_PLUGIN_ID + ":" + personID, name, character, siteDef.getSite() + "name/" + personID + "/", Movie.UNKNOWN);
                    count++;
                    if (count == actorMax || peopleCount + count == peopleMax) {
                        break;
                    }
                }
            }
        }

        // ORIGINAL TITLE / AKAS

        // Load the AKA page from IMDb
        if (releaseInfoXML.equals(Movie.UNKNOWN)) {
            releaseInfoXML = webBrowser.request(getImdbUrl(movie) + "releaseinfo", siteDef2.getCharset());
        }

        // The AKAs are stored in the format "title", "country"
        // therefore we need to look for the preferredCountry and then work backwards

        // Just extract the AKA section from the page
        ArrayList<String> akaList = HTMLTools.extractTags(releaseInfoXML, "Also Known As (AKA)", "</table>", "<td>", "</td>", false);

        // Does the "original title" exist on the page?
        if (akaList.toString().indexOf("original title") > 0) {
            // This table comes back as a single list, so we have to save the last entry in case it's the one we need
            String previousEntry = "";
            boolean foundAka = false;
            for (String akaTitle : akaList) {
                if (akaTitle.indexOf("original title") == -1) {
                    // We've found the entry, so quit
                    foundAka = true;
                    break;
                } else {
                    previousEntry = akaTitle;
                }
            }

            if (foundAka && isValidString(previousEntry)) {
                movie.setOriginalTitle(HTMLTools.stripTags(previousEntry).trim());
            }
        }

        // TAGLINE
        if (StringTools.isNotValidString(movie.getTagline())) {
            for (String tagline : HTMLTools.extractTags(xml, "<h4 class=\"inline\">" + siteDef2.getTagline() + ":</h4>", "<span class=\"")) {
                if (tagline != null) {
                    tagline = HTMLTools.stripTags(tagline);
                    movie.setTagline(cleanStringEnding(tagline));
                    break;
                }
            }
        }

        // TV SHOW
        if (movie.isTVShow()) {
            updateTVShowInfo(movie);
        }

        return true;
    }

    private Integer extractDirectors(Movie movie, String personXML, ImdbSiteDataDefinition siteDef) {
        int count = 0;
        boolean found = false;
        for (String directorMatch : siteDef.getDirector().split("\\|")) {
            if (personXML.indexOf(">" + directorMatch + "</a>") >= 0) {
                for (String member : HTMLTools.extractTags(personXML, ">" + directorMatch + "</a>", "</table>", "<a ", "</a>", false)) {
                    int beginIndex = member.indexOf("href=\"/name/");
                    if (beginIndex > -1) {
                        String personID = member.substring(beginIndex + 12, member.indexOf("/\"", beginIndex));
                        movie.addDirector(IMDB_PLUGIN_ID + ":" + personID, member.substring(member.indexOf("\">", beginIndex) + 2), siteDef.getSite() + "name/" + personID + "/");
                        found = true;
                        count++;
                        if (count == directorMax) {
                            break;
                        }
                    }
                }
            }
            if (found) {
                // We found a match, so stop search.
                break;
            }
        }
        return count;
    }

    private Integer extractWriters(Movie movie, String personXML, ImdbSiteDataDefinition siteDef) {
        int count = 0;
        boolean found = false;
        for (String categoryMatch : siteDef.getWriter().split("\\|")) {
            if (personXML.indexOf(">" + categoryMatch + "</a>") >= 0) {
                for (String member : HTMLTools.extractTags(personXML, ">" + categoryMatch + "</a>", "</table>", "<a ", "</a>", false)) {
                    int beginIndex = member.indexOf("href=\"/name/");
                    if (beginIndex > -1) {
                        String personID = member.substring(beginIndex + 12, member.indexOf("/\"", beginIndex));
                        String name = member.substring(member.indexOf("\">", beginIndex) + 2);
                        if (name.indexOf("more credit") == -1) {
                            movie.addWriter(IMDB_PLUGIN_ID + ":" + personID, name, siteDef.getSite() + "name/" + personID + "/");
                            found = true;
                            count++;
                            if (count == writerMax) {
                                break;
                            }
                        }
                    }
                }
            }
            if (found) {
                // We found a match, so stop search.
                break;
            }
        }
        return count;
    }

    /**
     * Process a awards in the IMDb web page
     * @param movie
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private boolean updateAwards(Movie movie) throws MalformedURLException, IOException {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        String site = siteDef.getSite();
        if (!siteDef.getSite().contains(".imdb.com")) {
            site = "http://www.imdb.com/";
        }
        String awardXML = webBrowser.request(site + "title/" + imdbId + "/awards");
        if (awardXML.indexOf("Category/Recipient(s)") > 0) {
            Collection<AwardEvent> awards = new ArrayList<AwardEvent>();
            for (String awardBlock : HTMLTools.extractTags(awardXML, "<table style=\"margin-top: 8px; margin-bottom: 8px\" cellspacing=\"2\" cellpadding=\"2\" border=\"1\">", "</table>", "bgcolor=\"#ffffdb\"", "<td colspan=\"4\" align=\"center\" valign=\"top\"")) {
                String name = HTMLTools.extractTag(awardBlock, "<big><a href=", "</a></big>");
                name = name.substring(name.indexOf(">") + 1);

                AwardEvent event = new AwardEvent();
                event.setName(name);

                for (String yearBlock : HTMLTools.extractTags(awardBlock + "<end>", "</th>", "<end>", "<tr", "<td colspan=\"4\">")) {
                    if (yearBlock.indexOf("Sections/Awards") > 0) {
                        String tmpString = HTMLTools.extractTag(yearBlock, "<a href=", "</a>");
                        String yearStr = tmpString.substring(tmpString.indexOf(">") + 1).substring(0, 4);
                        int year = yearStr.equals("????") ? -1 : Integer.parseInt(yearStr);
                        int won = 0;
                        int nominated = 0;
                        tmpString = HTMLTools.extractTag(yearBlock.substring(yearBlock.indexOf("/" + (yearStr.equals("????") ? "0000" : yearStr) + "\">" + yearStr)), "<td rowspan=\"", "</b></td>");
                        int count = Integer.parseInt(tmpString.substring(0, tmpString.indexOf("\"")));
                        String title = tmpString.substring(tmpString.indexOf("<b>") + 3);
                        String namePattern = " align=\"center\" valign=\"middle\">";
                        name = HTMLTools.extractTag(yearBlock.substring(yearBlock.indexOf("<b>" + title + "</b>")), namePattern, "</td>");
                        if (title.equals("Won") || title.equals("2nd place")) {
                            won = count;
                            if (yearBlock.indexOf("<b>Nominated</b>") > 0) {
                                nominated = Integer.parseInt(HTMLTools.extractTag(yearBlock.substring(yearBlock.indexOf(namePattern + name + "</td>") + 1), "<td rowspan=\"", "\""));
                            }
                        } else if (title.equals("Nominated")) {
                            nominated = count;
                        }

                        if (!scrapeWonAwards || (won > 0)) {
                            Award award = new Award();
                            award.setName(name);
                            award.setYear(year);
                            if (won > 0) {
                                award.setWons(extractNominationNames(true, yearBlock, nominated));
                            }
                            if (nominated > 0) {
                                award.setNominations(extractNominationNames(false, yearBlock, nominated));
                            }
                            event.addAward(award);
                        }
                    }
                }
                if (event.getAwards().size() > 0) {
                    awards.add(event);
                }
            }
            if (awards.size() > 0) {
                movie.setAwards(awards);
            }
        }
        return true;
    }

    private Collection<String> extractNominationNames(boolean won, String yearBlock, Integer nominated) {
        Collection<String> wons = new ArrayList<String>();
        String blockContent;
        for (String nameBlock : HTMLTools.extractTags(yearBlock + "<end>", won ? "<b>Won</b>" : "<b>Nominated</b>", won && (nominated > 0) ? "<b>Nominated</b>" : "<end>", "<td valign=\"top\"", "</td>")) {
            if (nameBlock.indexOf("<a ") > -1 && nameBlock.indexOf("<a ") < nameBlock.indexOf("<br>") && nameBlock.indexOf("<small>") > 0) {
                blockContent = HTMLTools.extractTag(nameBlock, "<small>", "</small>");
            } else if (nameBlock.indexOf("<br>") > 0) {
                blockContent = nameBlock.substring(0, nameBlock.indexOf("<br>"));
            } else {
                blockContent = nameBlock;
            }
            blockContent = HTMLTools.removeHtmlTags(blockContent);
            if (isNotValidString(blockContent)) {
                blockContent = HTMLTools.removeHtmlTags(nameBlock);
            }
            blockContent = blockContent.replaceAll("^\\s+", "").replaceAll("\\s+$", "").replaceAll("\\s+", " ");
//            if (isValidString(blockContent)) {
                wons.add(blockContent);
//            }
        }
        return wons;
    }

    /**
     * Process financial information in the IMDb web page
     * @param movie
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private boolean updateBusiness(Movie movie) throws MalformedURLException, IOException, NumberFormatException {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        String site = siteDef.getSite();
        if (!siteDef.getSite().contains(".imdb.com")) {
            site = "http://www.imdb.com/";
        }
        String xml = webBrowser.request(site + "title/" + imdbId + "/business");
        if (isValidString(xml)) {
            String budget = HTMLTools.extractTag(xml, "<h5>Budget</h5>", "<br/>").replaceAll("\\s.*", "");
            movie.setBudget(budget);
            NumberFormat moneyFormat = NumberFormat.getNumberInstance(new Locale("US"));
            for (int i = 0; i < 2; i++) {
                for (String oWeek : HTMLTools.extractTags(xml, "<h5>" + (i == 0 ? "Opening Weekend" : "Gross") + "</h5", "<h5>", "", "<br/")) {
                    if (isValidString(oWeek)) {
                        String currency = oWeek.replaceAll("\\d+.*", "");
                        long value = Long.parseLong(oWeek.replaceAll("^\\D*\\s*", "").replaceAll("\\s.*", "").replaceAll(",", ""));
                        String country = HTMLTools.extractTag(oWeek, "(", ")");
                        if (country.equals("Worldwide") && !currency.equals("$")) {
                            continue;
                        }
                        String money = i == 0 ? movie.getOpenWeek(country) : movie.getGross(country);
                        if (isValidString(money)) {
                            long m = Long.parseLong(money.replaceAll("^\\D*\\s*", "").replaceAll(",", ""));
                            value = i == 0 ? value + m : value > m ? value : m;
                        }
                        if (i == 0) {
                            movie.setOpenWeek(country, currency + moneyFormat.format(value));
                        } else {
                            movie.setGross(country, currency + moneyFormat.format(value));
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Process trivia in the IMDb web page
     * @param movie
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private boolean updateTrivia(Movie movie) throws MalformedURLException, IOException {
        if (triviaMax == 0) {
            return false;
        }
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        String site = siteDef.getSite();
        if (!siteDef.getSite().contains(".imdb.com")) {
            site = "http://www.imdb.com/";
        }
        String xml = webBrowser.request(site + "title/" + imdbId + "/trivia");
        if (isValidString(xml)) {
            int i = 0;
            for (String tmp : HTMLTools.extractTags(xml, "<div class=\"list\">", "<div class=\"list\">", "<div class=\"sodatext\"", "</div>")) {
                if (i < triviaMax || triviaMax == -1) {
                    tmp = HTMLTools.removeHtmlTags(tmp);
                    tmp = tmp.trim();
                    movie.addDidYouKnow(tmp);
                    i++;
                } else {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Process a list of people in the source XML
     * @param sourceXml
     * @param singleCategory    The singular version of the category, e.g. "Writer"
     * @param pluralCategory    The plural version of the category, e.g. "Writers"
     * @return
     */
    @SuppressWarnings("unused")
    private Collection<String> parseNewPeople(String sourceXml, String[] categoryList) {
        Collection<String> people = new LinkedHashSet<String>();

        for (String category : categoryList) {
            if (sourceXml.indexOf(category + ":") >= 0) {
                people = HTMLTools.extractTags(sourceXml, category, "</div>", "<a ", "</a>");
            }
        }
        return people;
    }

    private int parseRating(String rating) {
        StringTokenizer st = new StringTokenizer(rating, "/ ()");
        try {
            return (int) (Float.parseFloat(st.nextToken()) * 10);
        } catch (Exception error) {
            return -1;
        }
    }

    /**
     * Get the fanart for the movie from the FanartScanner
     *
     * @param movie
     * @return
     */
    protected String getFanartURL(Movie movie) {
        return FanartScanner.getFanartURL(movie);
    }

    @Override
    public void scanTVShowTitles(Movie movie) {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);

        if (!movie.isTVShow() || !movie.hasNewMovieFiles() || isNotValidString(imdbId)) {
            return;
        }

        try {
            String xml = webBrowser.request(siteDef.getSite() + "title/" + imdbId + "/episodes");
            int season = movie.getSeason();
            for (MovieFile file : movie.getMovieFiles()) {
                if (!file.isNewFile() || file.hasTitle()) {
                    // don't scan episode title if it exists in XML data
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (int episode = file.getFirstPart(); episode <= file.getLastPart(); ++episode) {
                    String episodeName = HTMLTools.extractTag(xml, "Season " + season + ", Episode " + episode + ":", 2);

                    if (!episodeName.equals(Movie.UNKNOWN) && episodeName.indexOf("Episode #") == -1) {
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
        } catch (IOException error) {
            logger.error("Failed retreiving episodes titles for: " + movie.getTitle());
            logger.error("Error: " + error.getMessage());
        }
    }

    /**
     * Get the TV show information from IMDb
     *
     * @throws IOException
     * @throws MalformedURLException
     */
    protected void updateTVShowInfo(Movie movie) throws MalformedURLException, IOException {
        scanTVShowTitles(movie);
    }

    /**
     * Retrieves the long plot description from IMDB if it exists, else "UNKNOWN"
     *
     * @param movie
     * @return long plot
     */
    private String getLongPlot(Identifiable movie) {
        String plot = Movie.UNKNOWN;

        try {
            String xml = webBrowser.request(siteDef.getSite() + "title/" + movie.getId(IMDB_PLUGIN_ID) + "/plotsummary", siteDef.getCharset());

            String result = HTMLTools.extractTag(xml, "<p class=\"plotpar\">");
            if (isValidString(result) && result.indexOf("This plot synopsis is empty") < 0) {
                plot = result;
            }

            // Second parsing other site (fr/ es / etc ...)
            result = HTMLTools.getTextAfterElem(xml, "<div id=\"swiki.2.1\">");
            if (isValidString(result) && result.indexOf("This plot synopsis is empty") < 0) {
                plot = result;
            }

        } catch (Exception error) {
            plot = Movie.UNKNOWN;
        }

        plot = trimToLength(plot, preferredPlotLength, true, plotEnding);

        return plot;
    }

    @Override
    public boolean scanNFO(String nfo, Movie movie) {
        boolean result = true;
        logger.debug("Scanning NFO for Imdb Id");
        String id = searchIMDB(nfo, movie);
        if (isValidString(id)) {
            movie.setId(IMDB_PLUGIN_ID, id);
            logger.debug("IMDb Id found in nfo: " + movie.getId(IMDB_PLUGIN_ID));
        } else {
            int beginIndex = nfo.indexOf("/tt");
            if (beginIndex != -1) {
                StringTokenizer st = new StringTokenizer(nfo.substring(beginIndex + 1), "/ \n,:!&Ã©\"'(--Ã¨_Ã§Ã )=$");
                movie.setId(IMDB_PLUGIN_ID, st.nextToken());
                logger.debug("IMDb Id found in nfo: " + movie.getId(IMDB_PLUGIN_ID));
            } else {
                beginIndex = nfo.indexOf("/Title?");
                if (beginIndex != -1 && beginIndex + 7 < nfo.length()) {
                    StringTokenizer st = new StringTokenizer(nfo.substring(beginIndex + 7), "/ \n,:!&Ã©\"'(--Ã¨_Ã§Ã )=$");
                    movie.setId(IMDB_PLUGIN_ID, "tt" + st.nextToken());
                    logger.debug("IMDb Id found in nfo: " + movie.getId(IMDB_PLUGIN_ID));
                } else {
                    logger.debug("No IMDb Id found in nfo !");
                    result = false;
                }
            }
        }
        return result;
    }

    /**
     * Search for the IMDB Id in the NFO file
     * @param nfo
     * @param movie
     * @return
     */
    private String searchIMDB(String nfo, Movie movie) {
        final int flags = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
        String imdbPattern = ")[\\W].*?(tt\\d{7})";
        // Issue 1912 escape special regex characters in title
        String title = Pattern.quote(movie.getTitle());
        String id = Movie.UNKNOWN;

        Pattern patternTitle;
        Matcher matchTitle;

        try {
            patternTitle = Pattern.compile("(" + title + imdbPattern, flags);
            matchTitle = patternTitle.matcher(nfo);
            if (matchTitle.find()) {
                id = matchTitle.group(2);
            } else {
                String dir = FileTools.getParentFolderName(movie.getFile());
                Pattern patternDir = Pattern.compile("(" + dir + imdbPattern, flags);
                Matcher matchDir = patternDir.matcher(nfo);
                if (matchDir.find()) {
                    id = matchDir.group(2);
                } else {
                    String strippedNfo = nfo.replaceAll("(?is)[^\\w\\r\\n]", "");
                    String strippedTitle = title.replaceAll("(?is)[^\\w\\r\\n]", "");
                    Pattern patternStrippedTitle = Pattern.compile("(" + strippedTitle + imdbPattern, flags);
                    Matcher matchStrippedTitle = patternStrippedTitle.matcher(strippedNfo);
                    if (matchStrippedTitle.find()) {
                        id = matchTitle.group(2);
                    } else {
                        String strippedDir = dir.replaceAll("(?is)[^\\w\\r\\n]", "");
                        Pattern patternStrippedDir = Pattern.compile("(" + strippedDir + imdbPattern, flags);
                        Matcher matchStrippedDir = patternStrippedDir.matcher(strippedNfo);
                        if (matchStrippedDir.find()) {
                            id = matchTitle.group(2);
                        }
                    }
                }
            }
        } catch (Exception error) {
            logger.error("ImdbPlugin: Error locating the IMDb ID in the nfo file for " + movie.getBaseFilename());
            logger.error(error.getMessage());
        }

        return id;
    }

    /**
     * Remove the "see more" or "more" values from the end of a string
     * @param uncleanString
     * @return
     */
    protected static String cleanStringEnding(String uncleanString) {
        int pos = uncleanString.indexOf("more");
        // First let's check if "more" exists in the string
        if (pos > 0) {
            if (uncleanString.endsWith("more")) {
                return new String(uncleanString.substring(0, uncleanString.length() - 4));
            }

            pos = uncleanString.toLowerCase().indexOf("see more");
            if (pos > 0) {
                return new String(uncleanString.substring(0, pos).trim());
            }
        }

        pos = uncleanString.toLowerCase().indexOf("see full summary");
        if (pos > 0) {
            return new String(uncleanString.substring(0, pos).trim());
        }

        return new String(uncleanString.trim());
    }

    /**
     * Get the IMDb URL with the default site definition
     * @param movie
     * @return
     */
    protected String getImdbUrl(Movie movie) {
        return getImdbUrl(movie, siteDef);
    }

    /**
     * Get the IMDb URL with the default site definition
     * @param person
     * @return
     */
    protected String getImdbUrl(Person person) {
        return getImdbUrl(person, siteDef);
    }

    /**
     * Get the IMDb URL with a specific site definition
     * @param movie
     * @param siteDefinition
     * @return
     */
    protected String getImdbUrl(Movie movie, ImdbSiteDataDefinition siteDefinition) {
        return siteDefinition.getSite() + "title/" + movie.getId(IMDB_PLUGIN_ID) + "/";
    }

    /**
     * Get the IMDb URL with a specific site definition
     * @param person
     * @param siteDefinition
     * @return
     */
    protected String getImdbUrl(Person person, ImdbSiteDataDefinition siteDefinition) {
        return (siteDefinition.getSite().contains(".imdb.com") ? siteDefinition.getSite() : "http://www.imdb.com/") + "name/" + person.getId(IMDB_PLUGIN_ID) + "/";
    }

    @Override
    public boolean scan(Person person) {
        String imdbId = person.getId(IMDB_PLUGIN_ID);
        if (isNotValidString(imdbId)) {
            imdbId = imdbInfo.getImdbPersonId(person.getName(), person.getJob());
            person.setId(IMDB_PLUGIN_ID, imdbId);
        }

        boolean retval = true;
        if (isValidString(imdbId)) {
            retval = updateImdbPersonInfo(person);
        }
        return retval;
    }

    /**
     * Scan IMDB HTML page for the specified person
     */
    private boolean updateImdbPersonInfo(Person person) {
        String imdbID = person.getId(IMDB_PLUGIN_ID);
        boolean returnStatus = false;

        try {
            if (!imdbID.startsWith("nm")) {
                imdbID = "nm" + imdbID;
                // Correct the ID if it's wrong
                person.setId(IMDB_PLUGIN_ID, "nm" + imdbID);
            }

            String xml = getImdbUrl(person);

            xml = webBrowser.request(xml, siteDef.getCharset());

            // We can work out if this is the new site by looking for " - IMDb" at the end of the title
            String title = HTMLTools.extractTag(xml, "<title>");
            // Check for the new version and correct the title if found.
            if (title.toLowerCase().endsWith(" - imdb")) {
                title = title.substring(0, title.length() - 7);
            }
            if (title.toLowerCase().startsWith("imdb - ")) {
                title = new String(title.substring(7));
            }
            person.setName(title);

            returnStatus = updateInfoNew(person, xml);
        } catch (Exception error) {
            logger.error("Failed retreiving IMDb data for person : " + imdbID);
            logger.error(SystemTools.getStackTrace(error));
        }
        return returnStatus;
    }

    /**
     * Process the new IMDb format web page
     * @param person
     * @param xml
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private boolean updateInfoNew(Person person, String xml) throws MalformedURLException, IOException {
        person.setUrl(getImdbUrl(person));

        if (xml.indexOf("Alternate Names:") > -1) {
            String name = HTMLTools.extractTag(xml, "Alternate Names:</h4>", "</div>");
            if (isValidString(name)) {
                for (String item : name.split(" <span>\\|</span> ")) {
                    person.addAka(item);
                }
            }
        }

        if (xml.indexOf("<td id=\"img_primary\"") > -1) {
            String photoURL = HTMLTools.extractTag(xml, "<td id=\"img_primary\"", "</td>");
            if (photoURL.indexOf("http://ia.media-imdb.com/images") > -1) {
                photoURL = "http://ia.media-imdb.com/images" + HTMLTools.extractTag(photoURL, "<img src=\"http://ia.media-imdb.com/images", "\"");
                if (isValidString(photoURL)) {
                    person.setPhotoURL(photoURL);
                    person.setPhotoFilename();
                }
            }
        }

        // get personal information
        String xmlInfo = webBrowser.request(getImdbUrl(person) + "bio", siteDef.getCharset());

        String date = "";
        int beginIndex, endIndex;
        if (xmlInfo.indexOf("<h5>Date of Birth</h5>") > -1) {
            endIndex = xmlInfo.indexOf("<h5>Date of Death</h5>");
            beginIndex = xmlInfo.indexOf("<a href=\"/date/");
            if (beginIndex > -1 && (endIndex == -1 || beginIndex < endIndex)) {
                date = xmlInfo.substring(beginIndex + 18, beginIndex + 20) + "-" + xmlInfo.substring(beginIndex + 15, beginIndex + 17);
            }
            beginIndex = xmlInfo.indexOf("birth_year=", beginIndex);
            if (beginIndex > -1 && (endIndex == -1 || beginIndex < endIndex)) {
                if (!date.equals("")) {
                    date += "-";
                }
                date += xmlInfo.substring(beginIndex + 11, beginIndex + 15);
            }

            beginIndex = xmlInfo.indexOf("birth_place=", beginIndex);
            String place = "";
            if (beginIndex > -1) {
                place = xmlInfo.substring(xmlInfo.indexOf("\">", beginIndex) + 2, xmlInfo.indexOf("</a>", beginIndex));
                if (isValidString(place)) {
                    person.setBirthPlace(place);
                }
            }
        }

        if (xmlInfo.indexOf("<h5>Date of Death</h5>") > -1) {
            endIndex = xmlInfo.indexOf("<h5>Mini Biography</h5>");
            beginIndex = xmlInfo.indexOf("<a href=\"/date/");
            String dDate = "";
            if (beginIndex > -1 && (endIndex == -1 || beginIndex < endIndex)) {
                dDate = xmlInfo.substring(beginIndex + 18, beginIndex + 20) + "-" + xmlInfo.substring(beginIndex + 15, beginIndex + 17);
            }
            beginIndex = xmlInfo.indexOf("death_date=", beginIndex);
            if (beginIndex > -1 && (endIndex == -1 || beginIndex < endIndex)) {
                if (!dDate.equals("")) {
                    dDate += "-";
                }
                dDate += xmlInfo.substring(beginIndex + 11, beginIndex + 15);
            }
            if (!dDate.equals("")) {
                date += "/" + dDate;
            }
        }

        if (!date.equals("")) {
            person.setYear(date);
        }

        beginIndex = xmlInfo.indexOf("<h5>Birth Name</h5>");
        if (beginIndex > -1) {
            String name = xmlInfo.substring(beginIndex + 19, xmlInfo.indexOf("<br/>", beginIndex));
            if (isValidString(name)) {
                person.addAka(name);
            }
        }

        beginIndex = xmlInfo.indexOf("<h5>Nickname</h5>");
        if (beginIndex > -1) {
            String name = xmlInfo.substring(beginIndex + 17, xmlInfo.indexOf("<br/>", beginIndex));
            if (isValidString(name)) {
                person.addAka(name);
            }
        }

        String biography = Movie.UNKNOWN;
        if (xmlInfo.indexOf("<h5>Mini Biography</h5>") > -1) {
            biography = HTMLTools.extractTag(xmlInfo, "<h5>Mini Biography</h5>", "<b>IMDb Mini Biography By: </b>");
        }
        if (!isValidString(biography) && xmlInfo.indexOf("<h5>Trivia</h5>") > -1) {
            biography = HTMLTools.extractTag(xmlInfo, "<h5>Trivia</h5>", "<br/>");
        }
        if (isValidString(biography)) {
            biography = HTMLTools.removeHtmlTags(biography);
            biography = trimToLength(biography, preferredBiographyLength, true, plotEnding);
            person.setBiography(biography);
        }

        // get known movies
        xmlInfo = webBrowser.request(getImdbUrl(person) + "filmoyear", siteDef.getCharset());
        if (xmlInfo.indexOf("<div id=\"tn15content\">") > -1) {
            int count = HTMLTools.extractTags(xmlInfo, "<div id=\"tn15content\">", "</div>", "<li>", "</li>").size();
            person.setKnownMovies(count);
        }

        // get filmography
        xmlInfo = webBrowser.request(getImdbUrl(person) + "filmorate", siteDef.getCharset());
        if (xmlInfo.indexOf("<div class=\"filmo\">") > -1) {
            String fg = HTMLTools.extractTag(xml, "<div id=\"filmography\">", "<div class=\"article\" >");
            TreeMap<Float, Filmography> filmography = new TreeMap<Float, Filmography>();
            Pattern tvPattern = Pattern.compile("( \\(#\\d+\\.\\d+\\))|(: Episode #\\d+\\.\\d+)");
            for (String department : HTMLTools.extractTags(xmlInfo, "<div id=\"tn15content\">", "<style>", "<div class=\"filmo\"", "</div>")) {
                String job = HTMLTools.removeHtmlTags(HTMLTools.extractTag(department, "<h5>", "</h5>"));
                if (!jobsInclude.contains(job)) {
                    continue;
                }
                for (String item : HTMLTools.extractTags(department, "<ol", "</ol>", "<li", "</li>")) {
                    beginIndex = item.indexOf("<h6>");
                    if (beginIndex > -1) {
                        item = item.substring(0, beginIndex);
                    }
                    int rating = (int) (Float.valueOf(HTMLTools.extractTag(item, "(", ")")).floatValue() * 10);
                    String id = HTMLTools.extractTag(item, "<a href=\"/title/", "/\">");
                    String name = HTMLTools.extractTag(item, "/\">", "</a>").replaceAll("\"", "");
                    String year = Movie.UNKNOWN;
                    beginIndex = item.indexOf("</a> (");
                    if (beginIndex > -1) {
                        year = HTMLTools.extractTag(item.substring(beginIndex), "(", ")");
                    }
                    if ((skipVG && name.endsWith("(VG)")) || (skipTV && name.endsWith("(TV)")) || (skipV && name.endsWith("(V)"))) {
                        continue;
                    } else if (skipTV) {
                        Matcher tvMatcher = tvPattern.matcher(name);
                        if (tvMatcher.find()) {
                            continue;
                        }
                        beginIndex = fg.indexOf("href=\"/title/" + id);
                        if (beginIndex > -1) {
                            beginIndex = fg.indexOf("</b>", beginIndex);
                            if (beginIndex > -1 && fg.indexOf("<br/>", beginIndex) > -1) {
                                String tail = fg.substring(beginIndex + 4, fg.indexOf("<br/>", beginIndex));
                                if (tail.contains("(TV series)") || tail.contains("(TV mini-series)") || tail.contains("(TV movie)")) {
                                    continue;
                                }
                            }
                        }
                    }
                    String URL = siteDef.getSite() + "title/" + id + "/";
                    String character = Movie.UNKNOWN;
                    if (job.equalsIgnoreCase("actor") || job.equalsIgnoreCase("actress")) {
                        beginIndex = fg.indexOf("href=\"/title/" + id);
                        if (beginIndex > -1) {
                            int brIndex = fg.indexOf("<br/>", beginIndex);
                            int divIndex = fg.indexOf("<div", beginIndex);
                            int hellipIndex = fg.indexOf("&hellip;", beginIndex);
                            if (divIndex > -1) {
                                if (brIndex > -1 && brIndex < divIndex) {
                                    character = fg.substring(brIndex + 5, divIndex);
                                    character = HTMLTools.removeHtmlTags(character);
                                } else if (hellipIndex > -1 && hellipIndex < divIndex) {
                                    character = fg.substring(hellipIndex + 8, divIndex);
                                    character = HTMLTools.removeHtmlTags(character);
                                }
                            }
                        }
                        if (isNotValidString(character)) {
                            String movieXML = webBrowser.request(URL + "fullcredits");
                            beginIndex = movieXML.indexOf("Cast</a>");
                            if (beginIndex > -1) {
                                character = HTMLTools.extractTag(movieXML.substring(beginIndex), "<a href=\"/name/" + person.getId(), "</td></tr>");
                                character = character.substring(character.lastIndexOf("\">") + 2).replace("</a>", "").replace("\"", "'").replaceAll("^\\s+", "");
                            }
                        }
                        if (isNotValidString(character)) {
                            character = Movie.UNKNOWN;
                        }
                    }

                    float key = 101 - (rating + Float.valueOf("0." + id.substring(2)).floatValue());

                    if (filmography.get(key) == null) {
                        Filmography film = new Filmography();
                        film.setId(id);
                        film.setName(name);
                        film.setYear(year);
                        film.setJob(job);
                        film.setCharacter(character);
                        film.setDepartment();
                        film.setRating(Integer.toString(rating));
                        film.setUrl(URL);
                        filmography.put(key, film);
                    }
                }
            }
            Iterator<Float> iterFilm = filmography.keySet().iterator();
            Object obj;
            int count = 0;
            while (iterFilm.hasNext() && count < preferredFilmographyMax) {
                obj = iterFilm.next();
                person.addFilm(filmography.get(obj));
                count++;
            }
        }

        int version = person.getVersion();
        person.setVersion(++version);
        return true;
    }
}
