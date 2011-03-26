/*
 *      Copyright (c) 2004-2011 YAMJ Members
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moviejukebox.model.Identifiable;
import com.moviejukebox.model.ImdbSiteDataDefinition;
import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.scanner.artwork.FanartScanner;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import static com.moviejukebox.tools.StringTools.*;
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
    protected ImdbSiteDataDefinition siteDef;
    protected ImdbInfo imdbInfo;
    protected static final String plotEnding = "...";

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
        extractCertificationFromMPAA = PropertiesUtil.getBooleanProperty("imdb.getCertificationFromMPAA", "true");
        getFullInfo = PropertiesUtil.getBooleanProperty("imdb.full.info", "false");
    }

    @Override
    public boolean scan(Movie movie) {
        String imdbId = movie.getId(IMDB_PLUGIN_ID);
        if (isNotValidString(imdbId)) {
            imdbId = imdbInfo.getImdbId(movie.getTitle(), movie.getYear());
            movie.setId(IMDB_PLUGIN_ID, imdbId);
        }

        boolean retval = true;
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
                movie.setId(IMDB_PLUGIN_ID, "tt" + imdbID);
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
            String yearPattern = ".\\((?:TV.|VIDEO.)?(\\d{4})(?:/[^\\)]+)?\\)";
            Pattern pattern = Pattern.compile(yearPattern);
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                // If we've found a year, set it in the movie
                movie.setYear(matcher.group(1));
                
                // Remove the year from the title. Removes "(TV)" and "(TV YEAR)"
                title = title.replaceAll(yearPattern, "");
            }
            
            // Check for the new version and correct the title if found.
            if (title.toLowerCase().endsWith(" - imdb")) {
                title = title.substring(0, title.length() - 7);
                imdbNewVersion = true;
            } else {
                imdbNewVersion = false;
            }
                
            if (!movie.isOverrideTitle()) {
                movie.setTitle(title);
                movie.setOriginalTitle(title);
            }
            
            if (imdbNewVersion) {
                returnStatus = updateInfoNew(movie, xml);
            } else {
                returnStatus = updateInfoOld(movie, xml);
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
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
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
    private boolean updateInfoOld(Movie movie, String xml) throws MalformedURLException, IOException {
        if (movie.getRating() == -1) {
            String rating = HTMLTools.extractTag(xml, "<div class=\"starbar-meta\">", "</b>").replace(",", ".");
            movie.setRating(parseRating(HTMLTools.stripTags(rating)));
        }

        if (movie.getTop250() == -1) {
            try {
                movie.setTop250(Integer.parseInt(HTMLTools.extractTag(xml, "Top 250: #")));
            } catch (NumberFormatException error) {
                movie.setTop250(-1);
            }
        }

        if (movie.getDirectors().isEmpty()) {
            // Note this is a hack for the change to IMDB for Issue 875
            ArrayList<String> tempDirectors = null;
            // Issue 1261 : Allow multiple text matching for one "element".
            String[] directorMatches = siteDef.getDirector().split("\\|");

            for (String directorMatch : directorMatches) {
                tempDirectors = HTMLTools.extractTags(xml, "<h5>" + directorMatch, "</div>", "<a href=\"/name/", "</a>");
                if (!tempDirectors.isEmpty()) {
                    // We found a match, so stop search.
                    break;
                }
            }

            if (!tempDirectors.isEmpty()) {
                movie.setDirectors(tempDirectors);
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
                movie.addGenre(Library.getIndexingGenre(cleanSeeMore(genre)));
            }
        }

        if (movie.getQuote().equals(Movie.UNKNOWN)) {
            for (String quote : HTMLTools.extractTags(xml, "<h5>" + siteDef.getQuotes() + ":</h5>", "</div>", "<a href=\"/name/nm", "</a class=\"")) {
                if (quote != null) {
                    quote = HTMLTools.stripTags(quote);
                    movie.setQuote(cleanSeeMore(quote));
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

                    imdbOutline = outline.trim();
                    if (isValidString(imdbOutline)) {
                        imdbOutline = trimToLength(imdbOutline, preferredPlotLength, true, plotEnding);
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

        if (movie.getCast().isEmpty()) {
            movie.setCast(HTMLTools.extractTags(xml, "<table class=\"cast\">", "</table>", "<td class=\"nm\"><a href=\"/name/", "</a>"));
        }

        /** Check for writer(s) **/
        if (movie.getWriters().isEmpty()) {
            movie.setWriters(HTMLTools.extractTags(xml, "<h5>" + siteDef.getWriter(), "</div>", "<a href=\"/name/", "</a>"));
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
        if (movie.getRating() == -1) {
            String rating = HTMLTools.extractTag(xml, "star-bar-user-rate\">", "</span>").replace(",", ".");
            movie.setRating(parseRating(HTMLTools.stripTags(rating)));
        }

        // TOP250
        if (movie.getTop250() == -1) {
            try {
                movie.setTop250(Integer.parseInt(HTMLTools.extractTag(xml, "Top 250 #")));
            } catch (NumberFormatException error) {
                movie.setTop250(-1);
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
            for (String genre : HTMLTools.extractTags(xml, siteDef2.getGenre() + ":</h4>", "</div>", "<a href=\"", "</a>")) {
                movie.addGenre(Library.getIndexingGenre(HTMLTools.removeHtmlTags(genre)));
            }
        }

        // QUOTE
        if (movie.getQuote().equals(Movie.UNKNOWN)) {
            for (String quote : HTMLTools.extractTags(xml, "<h4>" + siteDef2.getQuotes() + "</h4>", "<span class=\"", "<a ", "<br")) {
                if (quote != null) {
                    quote = HTMLTools.stripTags(quote);
                    movie.setQuote(cleanSeeMore(quote));
                    break;
                }
            }
        }

        // OUTLINE
        if (movie.getOutline().equals(Movie.UNKNOWN)) {
            // The new outline is at the end of the review section with no preceding text
            String imdbOutline = HTMLTools.extractTag(xml, "reviews</a>", "<div class=\"txt-block\">");
            imdbOutline = HTMLTools.removeHtmlTags(imdbOutline).trim();
            
            if (isValidString(imdbOutline)) {
                String searchText = " reviews";
                int beginIndex = imdbOutline.indexOf(searchText);
                if (beginIndex > 0) {
                    imdbOutline = imdbOutline.substring(beginIndex + searchText.length());
                }
                
                // See if the outline has the "metacritic" text and remove it
                searchText = "Metacritic.com)";
                beginIndex = imdbOutline.indexOf(searchText);
                if (beginIndex > 0) {
                    imdbOutline = imdbOutline.substring(beginIndex + searchText.length());
                }
                
                imdbOutline = trimToLength(imdbOutline, preferredPlotLength, true, plotEnding);
            } else {
                // ensure the outline is set to unknown if it's blank or null
                imdbOutline = Movie.UNKNOWN;
            }
            movie.setOutline(imdbOutline);
        }
        
        // PLOT
        if (movie.getPlot().equals(Movie.UNKNOWN)) {
            // The new plot is now called Storyline
            String imdbPlot = HTMLTools.extractTag(xml, "<h2>" + siteDef2.getPlot() + "</h2>", "<em class=\"nobr\">");
            imdbPlot = HTMLTools.removeHtmlTags(imdbPlot).trim();
            
            // This plot didn't work, look for another version
            if (isNotValidString(imdbPlot)) {
                imdbPlot = HTMLTools.extractTag(xml, "<h2>" + siteDef2.getPlot() + "</h2>", "<span class=\"");
                imdbPlot = HTMLTools.removeHtmlTags(imdbPlot).trim();
            }
            
            // See if the plot has the "metacritic" text and remove it
            int pos = imdbPlot.indexOf("Metacritic.com)");
            if (pos > 0) {
                imdbPlot = imdbPlot.substring(pos + "Metacritic.com)".length());
            }
            
            // Check the length of the plot is OK
            if (isValidString(imdbPlot)) {
                imdbPlot = trimToLength(imdbPlot, preferredPlotLength, true, plotEnding);
            } else {
                // The plot might be blank or null so set it to UNKNOWN
                imdbPlot = Movie.UNKNOWN;
            }
            
            // Update the plot with the found plot, or the outline if not found
            if (isValidString(imdbPlot)) {
                movie.setPlot(imdbPlot);
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

        // CAST
        if (movie.getCast().isEmpty()) {
            peopleList = HTMLTools.extractTags(xml, "<table class=\"cast_list\">", "</table>", "<td class=\"name\"", "</td>"); 
            String castMember;
            
            if (peopleList.isEmpty()) {
                // Try an alternative search
                peopleList = HTMLTools.extractTags(xml, "<table class=\"cast_list\">", "</table>", "/name/nm", "</a>", true);
            }
            
            // Clean up the cast list that is returned
            for (Iterator<String> iter = peopleList.iterator(); iter.hasNext();) {
                castMember = iter.next();
                if (castMember.indexOf("src=") > -1) {
                    iter.remove();
                } else {
                    // Add the cleaned up cast member to the movie
                    movie.addActor(HTMLTools.stripTags(castMember));
                }
            }
        }

        // DIRECTOR(S)
        if (movie.getDirectors().isEmpty()) {
            peopleList = parseNewPeople(xml, siteDef2.getDirector().split("\\|"));
            movie.setDirectors(peopleList);
        }

        // WRITER(S)
        if (movie.getWriters().isEmpty()) {
            peopleList = parseNewPeople(xml, siteDef2.getWriter().split("\\|")); 
            String writer;
            
            for (Iterator<String> iter = peopleList.iterator(); iter.hasNext();) {
                writer = iter.next();
                // Clean up by removing the phrase "and ? more credits"
                if (writer.indexOf("more credit") == -1) {
                    movie.addWriter(writer);
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
        
        // TV SHOW
        if (movie.isTVShow()) {
            updateTVShowInfo(movie);
        }

        return true;
    }
    
    /**
     * Process a list of people in the source XML
     * @param sourceXml
     * @param singleCategory    The singular version of the category, e.g. "Writer"
     * @param pluralCategory    The plural version of the category, e.g. "Writers"
     * @return
     */
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
            return (int)(Float.parseFloat(st.nextToken()) * 10);
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
    public void scanNFO(String nfo, Movie movie) {
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
                }
            }
        }
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
        String title = movie.getTitle();
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
    protected static String cleanSeeMore(String uncleanString) {
        int pos = uncleanString.indexOf("more");
        
        // First let's check if "more" exists in the string
        if (pos > 0) {
            if (uncleanString.endsWith("more")) {
                return uncleanString.substring(0, uncleanString.length() - 4);
            }

            pos = uncleanString.toLowerCase().indexOf("see more");
            if (pos > 0) {
                return uncleanString.substring(0, pos).trim();
            }
        } else {
            return uncleanString.trim();
        }
        
        return uncleanString;
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
     * Get the IMDb URL with a specific site definition
     * @param movie
     * @param siteDefinition
     * @return
     */
    protected String getImdbUrl(Movie movie, ImdbSiteDataDefinition siteDefinition) {
        return siteDefinition.getSite() + "title/" + movie.getId(IMDB_PLUGIN_ID) + "/";
    }
}
