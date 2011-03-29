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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import com.moviejukebox.model.ExtraFile;
import com.moviejukebox.model.IMovieBasicInformation;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.ThreadExecutor;
import com.moviejukebox.tools.WebStats;

/**
 * @author iuk
 *
 */

public class ComingSoonPlugin extends ImdbPlugin {

    public static String COMINGSOON_PLUGIN_ID = "comingsoon";
    public static String COMINGSOON_NOT_PRESENT = "na";
    private static String COMINGSOON_BASE_URL = "http://www.comingsoon.it/";
    private static String COMINGSOON_SEARCH_URL = "Film/Scheda/Trama/?";
    private static String COMINGSOON_VIDEO_URL = "Film/Scheda/Video/?";
    private static String COMINGSOON_KEY_PARAM= "key=";
    
    private static int COMINGSOON_MAX_DIFF = 1000;
    
    private static int COMINGSOON_MAX_SEARCH_PAGES = 5;
    

    private static int COMINGSOON_RESTORE_TITLE         = 1 << 0;
    private static int COMINGSOON_RESTORE_ORIGINALTITLE = 1 << 1;
    private static int COMINGSOON_RESTORE_PLOT          = 1 << 2;
    private static int COMINGSOON_RESTORE_OUTLINE       = 1 << 3;
    private static int COMINGSOON_RESTORE_RATING        = 1 << 4;
    private static int COMINGSOON_RESTORE_RUNTIME       = 1 << 5;
    private static int COMINGSOON_RESTORE_COUNTRY       = 1 << 6;
    private static int COMINGSOON_RESTORE_YEAR          = 1 << 7;
    private static int COMINGSOON_RESTORE_COMPANY       = 1 << 8;
    private static int COMINGSOON_RESTORE_GENRES        = 1 << 9;
    private static int COMINGSOON_RESTORE_CAST          = 1 << 10;
    private static int COMINGSOON_RESTORE_DIRECTORS     = 1 << 11;
    private static int COMINGSOON_RESTORE_WRITERS       = 1 << 12;
            
    private static int COMINGSOON_RESTORE_ALL           = (2 << 13) - 1;
    
    protected int preferredPlotLength;
    protected int preferredOutlineLength;
    protected String scanImdb;
    protected int preferImdbMask = 0;
    protected String searchId;
    
    protected boolean trailersScannerEnable;
    protected String trailerMaxResolution;
    protected String trailerPreferredFormat;
    protected boolean trailerSetExchange;
    protected boolean trailerDownload;
    protected String trailerLabel;

    public ComingSoonPlugin() {
        super();
        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "Italy");
        preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");
        preferredOutlineLength = PropertiesUtil.getIntProperty("plugin.outline.maxlength", "500");

        searchId = PropertiesUtil.getProperty("comingsoon.id.search", "comingsoon,yahoo");
        
        scanImdb = PropertiesUtil.getProperty("comingsoon.imdb.scan", "always"); 
        String preferImdbFor = PropertiesUtil.getProperty("comingsoon.imdb.perferredFor", "rating,runtime,country,year,company,cast,writers");
        if (preferImdbFor.length() > 0) {
            StringTokenizer st = new StringTokenizer(preferImdbFor, ",");
            
            if (st.hasMoreTokens()) {
               while (st.hasMoreTokens()) {
                   preferImdbMask |= getInfoMask(st.nextToken());
               }
            } else {
                preferImdbMask |= getInfoMask(preferImdbFor);
            }
        }
        
        trailersScannerEnable = PropertiesUtil.getBooleanProperty("trailers.scanner.enable", "true");
        trailerMaxResolution = PropertiesUtil.getProperty("comingsoon.trailer.resolution", "");
        trailerPreferredFormat = PropertiesUtil.getProperty("comingsoon.trailer.preferredFormat", "wmv,mov");
        trailerSetExchange = PropertiesUtil.getBooleanProperty("comingsoon.trailer.setExchange", "false");
        trailerDownload = PropertiesUtil.getBooleanProperty("comingsoon.trailer.download", "false");
        trailerLabel = PropertiesUtil.getProperty("comingsoon.trailer.label", "ita");
    }
    
    private int getInfoMask(String infoDescription) {
        if (infoDescription.equalsIgnoreCase("title")) {
            return COMINGSOON_RESTORE_TITLE;
        } else if (infoDescription.equalsIgnoreCase("originaltitle")){
            return COMINGSOON_RESTORE_ORIGINALTITLE;
        } else if (infoDescription.equalsIgnoreCase("plot")){
            return COMINGSOON_RESTORE_PLOT;
        } else if (infoDescription.equalsIgnoreCase("outline")){
            return COMINGSOON_RESTORE_OUTLINE;
        } else if (infoDescription.equalsIgnoreCase("rating")){
            return COMINGSOON_RESTORE_RATING;
        } else if (infoDescription.equalsIgnoreCase("runtime")){
            return COMINGSOON_RESTORE_RUNTIME;
        } else if (infoDescription.equalsIgnoreCase("country")){
            return COMINGSOON_RESTORE_COUNTRY;
        } else if (infoDescription.equalsIgnoreCase("year")){
            return COMINGSOON_RESTORE_YEAR;
        } else if (infoDescription.equalsIgnoreCase("company")){
            return COMINGSOON_RESTORE_COMPANY;
        } else if (infoDescription.equalsIgnoreCase("genres")){
            return COMINGSOON_RESTORE_GENRES;
        } else if (infoDescription.equalsIgnoreCase("cast")){
            return COMINGSOON_RESTORE_CAST;
        } else if (infoDescription.equalsIgnoreCase("directors")){
            return COMINGSOON_RESTORE_DIRECTORS;
        } else if (infoDescription.equalsIgnoreCase("writers")){
            return COMINGSOON_RESTORE_WRITERS;
        } else {
            logger.info("ComingSoon: unknown value \"" + infoDescription + "\" for property comingsoon.perferimdbfor");
            return 0;
        }
    }

    @Override
    public boolean scan(Movie movie) {

        String comingSoonId = movie.getId(COMINGSOON_PLUGIN_ID);

        // First we try on comingsoon.it
        
        if (StringTools.isNotValidString(comingSoonId)) {
            comingSoonId = getComingSoonId(movie.getTitle(), movie.getYear());
            movie.setId(COMINGSOON_PLUGIN_ID, comingSoonId);
            
            if (StringTools.isNotValidString(comingSoonId)) {
                logger.debug("ComingSoon: unable to find id on first scan");
            }
        }

        // Then we use IMDB to get complete information. We back up movie infos, since ComingSoon is fetched later and will eventually need
        // to ovverride IMDB infos.
               
        Movie preImdb = backupMovieInfo(movie);

        boolean firstScanImdb = false;
        
        if (scanImdb.equalsIgnoreCase("always") || (scanImdb.equalsIgnoreCase("fallback") && (StringTools.isNotValidString(comingSoonId) || comingSoonId.equals(COMINGSOON_NOT_PRESENT)))) {
            logger.debug("ComingSoon: Checking IMDB");
            firstScanImdb = super.scan(movie);      
            if (StringTools.isNotValidString(comingSoonId) && firstScanImdb) {
                // We try to fetch again ComingSoon, hopefully with more info
                logger.debug("ComingSoon: First search on ComingSoon was KO, retrying after succesful query to IMDB");
                comingSoonId = getComingSoonId(movie.getTitle(), movie.getYear());
                movie.setId(COMINGSOON_PLUGIN_ID, comingSoonId);
            }
            
            if (StringTools.isNotValidString(comingSoonId)) {
                logger.debug("ComingSoon: unable to find id on second scan");
            }
        }
        
        Movie postImdb = backupMovieInfo(movie);

        boolean scanComingSoon = false;
        if (StringTools.isValidString(comingSoonId)) {

            logger.debug("ComingSoon: Fetching movie data from ComingSoon");
            
            // Restore pre-IMDB infos except title.
            logger.debug("ComingSoon: restoring pre-IMDB infos with override");
            restoreMovieInfo(preImdb, movie, COMINGSOON_RESTORE_ALL ^ (COMINGSOON_RESTORE_TITLE | COMINGSOON_RESTORE_ORIGINALTITLE), true);
            
            scanComingSoon = updateComingSoonMediaInfo(movie);
            
            // Fill UNKNOWN vales with post-IMDB infos
            logger.debug("ComingSoon: restoring post-IMDB infos w/o override");
            restoreMovieInfo(postImdb, movie, COMINGSOON_RESTORE_ALL ^ (COMINGSOON_RESTORE_TITLE | COMINGSOON_RESTORE_ORIGINALTITLE), false);
            
            // Replace values where IMDB is preferred
            logger.debug("ComingSoon: restoring post-IMDB infos with protected override");
            restoreMovieInfo(postImdb, movie, preferImdbMask, true, true);
        }
        
        Movie postComingSoon = backupMovieInfo(movie);

        if (!firstScanImdb && scanComingSoon && scanImdb.equalsIgnoreCase("always")) {
            // Scan was successful on ComingSoon but not on IMDB, let's try again with more info

            logger.debug("ComingSoon: First scan on IMDB KO, retrying after succesful scan on ComingSoon");
            
            // Restore pre-ComingSoon infos except title and year.
            logger.debug("ComingSoon: restoring pre-IMDB infos with override");
            restoreMovieInfo(postImdb, movie, COMINGSOON_RESTORE_ALL ^ (COMINGSOON_RESTORE_TITLE | COMINGSOON_RESTORE_ORIGINALTITLE | COMINGSOON_RESTORE_YEAR), true);
            
            // Set title to original title, more likely to be found on IMDB
            if (StringTools.isValidString(movie.getOriginalTitle())) {
                movie.setTitle(movie.getOriginalTitle());
            }            

            super.scan(movie);

            logger.debug("ComingSoon: restoring comingsoon informations where needed");
            // restoreMovieInfo(postComingSoon, movie, COMINGSOON_RESTORE_TITLE | COMINGSOON_RESTORE_ORIGINALTITLE, true);
            restoreMovieInfo(postComingSoon, movie, COMINGSOON_RESTORE_ALL ^ preferImdbMask , true, true);
           

        }
        
        if (StringTools.isNotValidString(movie.getOriginalTitle())) {
            movie.setOriginalTitle(movie.getTitle());
        }
        
        if (trailersScannerEnable) {
            generateTrailer(movie);
        }
        
        return scanComingSoon || firstScanImdb; 
    }
    
    protected String getComingSoonId(String movieName, String year) {
        return getComingSoonId(movieName, year, searchId);
    }
    
    protected String getComingSoonId(String movieName, String year, String searchIdPreference) {
        
        if (searchIdPreference.equalsIgnoreCase("comingsoon")) {
            return  getComingSoonIdFromComingSoon(movieName, year);
        } else if (searchIdPreference.equalsIgnoreCase("yahoo")) {
            return getComingSoonIdFromSearch("http://search.yahoo.com/search?vc=&p=", movieName, year);
        } else if (searchIdPreference.equalsIgnoreCase("google")) {
            return getComingSoonIdFromSearch("http://www.google.com/search?hl=it&q=", movieName, year);
        } else if (searchIdPreference.indexOf(",") > 0) {
            StringTokenizer st = new StringTokenizer(searchIdPreference, ",");
            while (st.hasMoreTokens()) {
                String id = getComingSoonId(movieName, year, st.nextToken().trim());
                if (StringTools.isValidString(id)) {
                    return id;
                }
            }
        }
        return Movie.UNKNOWN;
    }

    protected String getComingSoonIdFromSearch(String searchUrl, String movieName, String year) {
        try {
            String comingSoonId = Movie.UNKNOWN;
                        
            StringBuffer sb = new StringBuffer(searchUrl);
            sb.append("\"" + URLEncoder.encode(movieName, "UTF-8") + "\"");              

            sb.append("+site%3Acomingsoon.it");

            logger.debug("ComingSoon: Fetching ComingSoon search URL: " + sb.toString());
            String xml = webBrowser.request(sb.toString());
            
           
            int beginIndex = xml.indexOf(COMINGSOON_BASE_URL + COMINGSOON_SEARCH_URL);
            if (beginIndex > 0) {
                comingSoonId = getComingSoonIdFromURL(new String(xml.substring(beginIndex, xml.indexOf('"', beginIndex))));
                logger.debug("ComingSoon: Found ComingSoon ID: " + comingSoonId);
            }
            return comingSoonId;
            
            
        } catch (Exception error) {
            logger.error("ComingSoon: Failed retreiving ComingSoon Id for movie : " + movieName);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
            return Movie.UNKNOWN;
        }
    }

    protected String getComingSoonIdFromComingSoon(String movieName, String year) {
        return getComingSoonIdFromComingSoon(movieName, year, COMINGSOON_MAX_DIFF);
    }
        
    protected String getComingSoonIdFromComingSoon(String movieName, String year, int scoreToBeat) {
        try {
            if (scoreToBeat == 0) {
                return Movie.UNKNOWN;
            }
            
            String comingSoonId = Movie.UNKNOWN;
            
            StringBuffer sb = new StringBuffer("http://www.comingsoon.it/Film/Database/?titoloFilm=");
            sb.append(URLEncoder.encode(movieName, "iso-8859-1"));              

            if (StringTools.isValidString(year)) {
                sb.append("&anno=" + year);
            }
            
            int searchPage = 0;
            
            while (searchPage++ < COMINGSOON_MAX_SEARCH_PAGES) {
                
                StringBuffer sbPage = new StringBuffer(sb);
                if (searchPage > 1) {
                    sbPage.append("&p="+searchPage);
                }

                logger.debug("ComingSoon: Fetching ComingSoon search URL: " + sbPage.toString());
                String xml = webBrowser.request(sbPage.toString(), Charset.forName("iso-8859-1"));
                
                ArrayList<String[]> movieList = parseComingSoonSearchResults(xml);
                
                if (movieList.size() > 0) {
                
                    for (int i = 0; i < movieList.size() && scoreToBeat > 0; i++) {
                        String lId = (String) movieList.get(i)[0];
                        String lTitle = (String) movieList.get(i)[1];
                        String lOrig = (String) movieList.get(i)[2];
                        //String lYear = (String) movieList.get(i)[3];
                        int difference = compareTitles(movieName, lTitle);
                        int differenceOrig = compareTitles(movieName, lOrig);
                        difference = (differenceOrig < difference ? differenceOrig : difference);
                        if (difference < scoreToBeat) {
                            if (difference == 0) {
                                logger.debug("ComingSoon: Found perfect match for: " + lTitle + ", " + lOrig);
                                searchPage = COMINGSOON_MAX_SEARCH_PAGES; //End loop
                            } else {
                                logger.debug("ComingSoon: Found a match for: " + lTitle + ", " + lOrig + ", difference " + difference);
                            }
                            comingSoonId = lId;
                            scoreToBeat = difference;
                        }
                    }
                } else {
                    break;
                }
            }
            
            if (StringTools.isValidString(year) && scoreToBeat > 0) {
                logger.debug("ComingSoon: Perfect match not found, trying removing by year...");
                String newComingSoonId = getComingSoonIdFromComingSoon(movieName, Movie.UNKNOWN, scoreToBeat);
                comingSoonId = (StringTools.isNotValidString(newComingSoonId) ? comingSoonId : newComingSoonId);
            }
            
            if (StringTools.isValidString(comingSoonId)) {
                logger.debug("ComingSoon: Found ComingSoon ID: " + comingSoonId);
            }

            return comingSoonId;
            
        } catch (Exception error) {
            logger.error("ComingSoon: Failed retreiving ComingSoon Id for movie : " + movieName);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
            return Movie.UNKNOWN;
        }
    }
    
    private ArrayList<String[]> parseComingSoonSearchResults(String xml) {
        
        /* Search results end with "Trovati NNN Film" (found NNN movies). 
           After this string, more movie URL are found, so we have to set a boundary
        */

        ArrayList<String[]> listaFilm = new ArrayList<String[]>();
        int trovatiIndex = xml.indexOf("Trovati");
        int moviesFound = -1;
        
        while (trovatiIndex >= 0 && moviesFound < 0) {
            int filmIndex = xml.indexOf("Film", trovatiIndex);
            if (filmIndex - trovatiIndex < 15 && filmIndex > 0) {
                moviesFound = Integer.parseInt(new String(xml.substring(trovatiIndex + 8, filmIndex - 1)));
            } else {
                trovatiIndex = xml.indexOf("Trovati", trovatiIndex + 1);
            }
        }
        
        if (moviesFound < 0) {
            logger.error("ComingSoon: couldn't find 'Trovati NNN Film' string. Search page layout probably changed");
            return listaFilm;
        } else {
            logger.debug("ComingSoon: search found " + moviesFound + " movies");
        }
        
        if (moviesFound == 0) {
            return listaFilm;
        }
        
        int beginIndex = xml.indexOf("<div id=\"BoxFilm\">");
        
        while (beginIndex >= 0 && beginIndex < trovatiIndex) {
            int urlIndex = xml.indexOf(COMINGSOON_SEARCH_URL, beginIndex);
            logger.debug("ComingSoon: Found movie URL " + new String(xml.substring(urlIndex, xml.indexOf('"', urlIndex))));
            String comingSoonId = getComingSoonIdFromURL (new String(xml.substring(urlIndex, xml.indexOf('"', urlIndex))));
            
            int nextIndex = xml.indexOf("<div id=\"BoxFilm\">", beginIndex + 1);
            
            String search;
            if (nextIndex > 0 && nextIndex < trovatiIndex) {
                search = new String(xml.substring(beginIndex, nextIndex));
            } else {
                search = new String(xml.substring(beginIndex, trovatiIndex));
            }

            String title = HTMLTools.extractTag(search, "class=\"titoloFilm\"", 0, "<>", false).trim();
            String originalTitle = HTMLTools.extractTag(search, "class=\"titoloFilm2\">", "<").trim();
            if (originalTitle.startsWith("(")) {
                originalTitle = new String(originalTitle.substring(1, originalTitle.length() - 1)).trim();
            } else if (originalTitle.length() == 0) {
                originalTitle = Movie.UNKNOWN;
            }

            String year = Movie.UNKNOWN;
            int beginYearIndex = search.indexOf("ANNO PROD:</span>");
            if (beginYearIndex > 0) {
                year = HTMLTools.extractTag(new String(search.substring(beginYearIndex)), "<b").trim();
            }
            
            String[] movieData = {comingSoonId, title, originalTitle, year};
            listaFilm.add(movieData);

            beginIndex = nextIndex;
            
        }
        
        return listaFilm;
        
    }
    
    private String getComingSoonIdFromURL (String url) {
        int beginIndex = url.indexOf(COMINGSOON_KEY_PARAM);
        StringTokenizer st = new StringTokenizer(new String(url.substring(beginIndex + COMINGSOON_KEY_PARAM.length())), "&/\"");
        String comingSoonId = st.nextToken();
        
        return comingSoonId;
    }
    
    /**
     * Returns difference between two titles.
     * Since ComingSoon returns strange results on some researches, difference is defined as follows:
     * abs(word count difference) - (searchedTitle word count - matched words);
     * 
     * @param searchedTitle
     * @param returnedTitle
     * @return
     */
    private int compareTitles(String searchedTitle, String returnedTitle) {
        if (StringTools.isNotValidString(returnedTitle)) {
            return COMINGSOON_MAX_DIFF;
        }
        
        logger.debug("ComingSoon: Comparing " + searchedTitle + " and " + returnedTitle);
        
        StringTokenizer st1 = new StringTokenizer(searchedTitle);
        int lastMatchedWord = -1;
        int searchedWordCount = st1.countTokens();
        int returnedWordCount = 0;
        int matchingWords = 0;
        
        while (st1.hasMoreTokens()) {
            String candidate1 = st1.nextToken();
            
            candidate1.replaceAll("[,.\\!\\?\"']", "");

            boolean gotMatch = false;
            int wordCounter = 0;
            StringTokenizer st2 = new StringTokenizer(returnedTitle);
            returnedWordCount = st2.countTokens();
            while (st2.hasMoreTokens() && ! gotMatch) {
                String candidate2 = st2.nextToken();

                if (wordCounter > lastMatchedWord) {
                    if (candidate1.equalsIgnoreCase(candidate2)) {
                        gotMatch = true;
                        matchingWords++;
                        lastMatchedWord = wordCounter;
                    }
                }
                wordCounter++;
            }
        }
        int difference = (searchedWordCount - returnedWordCount > 0 ? searchedWordCount - returnedWordCount : returnedWordCount - searchedWordCount) + (searchedWordCount - matchingWords); 
        
        if (returnedTitle.indexOf('-') >= 0) {
            StringTokenizer st2 = new StringTokenizer(returnedTitle, "-");
            while (st2.hasMoreTokens() && difference > 0) {
                int newDiff = compareTitles(searchedTitle, st2.nextToken().trim());
                difference = newDiff < difference ? newDiff : difference;
            }
        }
        
        return difference;

    }
    
    protected boolean updateComingSoonMediaInfo(Movie movie) {
        
        if (movie.getId(COMINGSOON_PLUGIN_ID).equalsIgnoreCase(COMINGSOON_NOT_PRESENT)) {
            return false;
        }
        
        try {
            String movieURL = COMINGSOON_BASE_URL + COMINGSOON_SEARCH_URL +  COMINGSOON_KEY_PARAM + movie.getId(COMINGSOON_PLUGIN_ID);
            logger.debug("ComingSoon: Querying ComingSoon for " + movieURL);
            String xml = webBrowser.request(movieURL, Charset.forName("iso-8859-1"));
            
            // TITLE & ORIGINAL TITLE
            
            if (!movie.isOverrideTitle()) {
                String title = HTMLTools.extractTag(xml, "<h1 class='titoloFilm'", 0, "<>", false).trim();
                String originalTitle = HTMLTools.extractTag(xml, "<h1 class='titoloFilm2'", 0, "<>", false).trim();
                if (StringTools.isNotValidString(originalTitle)) {
                    // Comingsoon layout slightly changed at some point and original title became h2
                    originalTitle = HTMLTools.extractTag(xml, "<h2 class='titoloFilm2'", 0, "<>", false).trim();
                }
                if (originalTitle.startsWith("(")) {
                    originalTitle = new String(originalTitle.substring(1, originalTitle.length() - 1)).trim();
                }
                
                if (StringTools.isNotValidString(title)) {
                    logger.error("ComingSoon: No title found at ComingSoon page. HTML layout has changed?");
                    return false;
                }
                
                title = correctCapsTitle(title);

                if (StringTools.isValidString(originalTitle)) {
                    originalTitle = correctCapsTitle(originalTitle);
                }
                
                movie.setTitle(title);
                movie.setOriginalTitle(originalTitle);
            }
            
            // RATING
            
            if (movie.getRating() == -1) {
                String rating = HTMLTools.extractTag(xml, "<li class=\"current-rating\"", 1, "<>", false).trim();
                logger.debug("ComingSoon: found rating " + rating);
                if (StringTools.isValidString(rating)) {
                    rating = new String(rating.substring(rating.indexOf(" ") + 1, rating.indexOf("/")));
                    int ratingInt = (int) (Float.parseFloat(rating.replace(',','.')) * 20); // Rating is 0 to 5, we normalize to 100
                    if (ratingInt > 0) {
                        movie.setRating(ratingInt);
                    }
                }
            }
            
            // RELEASE DATE
            
            if (StringTools.isNotValidString(movie.getReleaseDate())) {
                String releaseDate = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">USCITA CINEMA: ", "<br />"));
                movie.setReleaseDate(releaseDate);
            }
            
            // RUNTIME
            
            if (StringTools.isNotValidString(movie.getRuntime())) {
                String runTime = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">DURATA: ", "<br />"));
                if (StringTools.isValidString(runTime)) {
                    StringTokenizer st = new StringTokenizer(runTime);
                    movie.setRuntime(st.nextToken());
                }

            }
            
            // COUNTRY AND YEAR
            
            String countryYear = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">PAESE: ", "<br />"));
            String country = Movie.UNKNOWN;
            String year = Movie.UNKNOWN;
            
            if (StringTools.isValidString(countryYear)) {

                year = new String(countryYear.substring(countryYear.length() - 4, countryYear.length()));
                StringTokenizer st = new StringTokenizer(new String(countryYear.substring(0, countryYear.length() - 5)), ",");
                // Last country seems to be the more appropriate
                while (st.hasMoreTokens()) {
                    country = st.nextToken().trim();
                }
                

                logger.debug("ComingSoon: found countryYear " + countryYear + ", country " + country + ", year " + year);
                
                if (!movie.isOverrideYear() && Integer.parseInt(year) > 1900) {
                    movie.setYear(year);
                }
                
                if (StringTools.isNotValidString(movie.getCountry()) && country.length() > 0 && StringTools.isValidString(country)) {
                    movie.setCountry(country);
                }
            }

            // COMPANY
            if (StringTools.isNotValidString(movie.getCompany())) {
                // TODO: Add more than one company when available in Movie model
                String companies = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">PRODUZIONE: ", "<br />"));
                StringTokenizer st = new StringTokenizer(companies, ",");
                if (st.hasMoreTokens()) {
                    movie.setCompany(st.nextToken().trim());
                } else {
                    movie.setCompany(companies.trim());
                }
                
            }
            
            // GENRES
            
            if (movie.getGenres().isEmpty()) {
            
                String genreList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">GENERE: ", "<br />"));
                if (StringTools.isValidString(genreList)) {
                    Collection<String> genres = new ArrayList<String>();
                    
                    StringTokenizer st = new StringTokenizer(genreList, ",");
                    while (st.hasMoreTokens()) {
                        genres.add(st.nextToken().trim());
                    }
                    movie.setGenres(genres);
                }
            }

            // PLOT AND OUTLINE
            
            if (StringTools.isNotValidString(movie.getPlot())) {
                
                int beginIndex = xml.indexOf("<span class='vociFilm'>Trama del film");
                if (beginIndex < 0) {
                    logger.error("ComingSoon: No plot found at ComingSoon page. HTML layout has changed?");
                    return false;
                }
    
                beginIndex = xml.indexOf("</span>", beginIndex);
                if (beginIndex < 0) {
                    logger.error("ComingSoon: No plot found at ComingSoon page. HTML layout has changed?");
                    return false;
                }
    
                int endIndex = xml.indexOf("</div>", beginIndex);
                if (endIndex < 0) {
                    logger.error("ComingSoon: No plot found at ComingSoon page. HTML layout has changed?");
                    return false;
                }
                
                String xmlPlot = new String(xml.substring(beginIndex + 7, endIndex - 1)).trim();
                xmlPlot = HTMLTools.stripTags(xmlPlot).trim();
                
                /*
                 *  There are sometimes two markers in the plot to differentiate between the 
                 *  plot (TRAMA LUNGA) and the outline (TRAMA BREVE). We should extract these
                 *  and use them appropriately
                 */
                
                String plot = "";
                String outline = "";
                int plotStart = xmlPlot.toUpperCase().indexOf("TRAMA LUNGA");
                int outlineStart = xmlPlot.toUpperCase().indexOf("TRAMA BREVE");
                
                if (plotStart == -1 && outlineStart == -1) {
                    // We've found neither, so the plot stays the same
                    plot = xmlPlot;
                    outline = xmlPlot;
                } else {
                    // We've found at least one of the plots
                    if (outlineStart == -1 && plotStart > 0) {
                        // We'll assume that the outline is at the beginning of the plot
                        outline = new String(xmlPlot.substring(0, plotStart));
                    } else {
                        outline = new String(xmlPlot.substring(11, plotStart));
                    }
                    plot = new String(xmlPlot.substring(plotStart + 11));
                }
                
                plot = StringTools.trimToLength(plot, preferredPlotLength, true, plotEnding);
                outline = StringTools.trimToLength(outline, preferredOutlineLength, true, plotEnding);

                movie.setPlot(plot);
                movie.setOutline(outline);
            }

            // CAST
            
            if (movie.getCast().isEmpty()) {
                String castList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">ATTORI: ", "Ruoli ed Interpreti"));
                
                if (castList.contains(",")) {
                    StringTokenizer st = new StringTokenizer(castList,",");
                    while (st.hasMoreTokens()) {
                        movie.addActor(st.nextToken());
                    }
                } else {
                    movie.addActor(castList);
                }
            }
            
            // DIRECTOR(S)
            
            if (movie.getDirectors().isEmpty()) {
                String directorList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">REGIA: ", "<br />"));
                
                if (directorList.contains(",")) {
                    StringTokenizer st = new StringTokenizer(directorList,",");
                    while (st.hasMoreTokens()) {
                        movie.addDirector(st.nextToken());
                    }
                } else {
                    movie.addDirector(directorList);
                }
            }

            // WRITER(S)
            
            if (movie.getWriters().isEmpty()) {
                String writerList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">SCENEGGIATURA: ", "<br />"));
                
                if (writerList.contains(",")) {
                    StringTokenizer st = new StringTokenizer(writerList,",");
                    while (st.hasMoreTokens()) {
                        movie.addWriter(st.nextToken());
                    }
                } else {
                    movie.addWriter(writerList);
                }
            }
            
            return true;
            
        } catch (Exception error) {
            logger.error("ComingSoon: Failed retreiving ComingSoon data for movie : " + movie.getId(COMINGSOON_PLUGIN_ID));
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
            return false;
        } 

    }
    
    /**
     * ComingSoon has some titles all caps. We normalize them
     * @param title
     * @return
     */
    private String correctCapsTitle(String title) {
        if (title.equals(title.toUpperCase())) {
            StringBuffer sb = new StringBuffer();
            StringTokenizer st = new StringTokenizer(title);
            while (st.hasMoreTokens()) {
                String word = st.nextToken();
                sb.append(new String(word.substring(0, 1)).toUpperCase());
                if (word.length() > 1) {
                    sb.append(new String(word.substring(1)).toLowerCase());
                }
                if (st.hasMoreTokens()) {
                    sb.append(' ');
                }
            }
            return sb.toString();
        } else {
            return title;
        }
        
    }

    @Override
    public void scanNFO(String nfo, Movie movie) {
        super.scanNFO(nfo, movie); // use IMDB as basis

        int beginIndex = nfo.indexOf("?key=");
        if (beginIndex != -1) {
            StringTokenizer st = new StringTokenizer(new String(nfo.substring(beginIndex + 5)), "/ \n,:!&é\"'(--è_çà)=$");
            movie.setId(COMINGSOON_PLUGIN_ID, st.nextToken());
            logger.debug("ComingSoon Id found in nfo = " + movie.getId(COMINGSOON_PLUGIN_ID));
        } else {
            logger.debug("No ComingSoon Id found in nfo!");
        }
    }
    
    private Movie backupMovieInfo(Movie originalMovie) {
        Movie backup = new Movie();
        
        backup.setTitle(originalMovie.getTitle());
        backup.setOriginalTitle(originalMovie.getOriginalTitle());
        backup.setPlot(originalMovie.getPlot());
        backup.setOutline(originalMovie.getOutline());
        backup.setRating(originalMovie.getRating());
        backup.setRuntime(originalMovie.getRuntime());
        backup.setCountry(originalMovie.getCountry());
        backup.setYear(originalMovie.getYear());
        backup.setCompany(originalMovie.getCompany());
        backup.setGenres(originalMovie.getGenres());
        backup.setCast(originalMovie.getCast());
        backup.setDirectors(originalMovie.getDirectors());
        backup.setWriters(originalMovie.getWriters());
        
        return backup;
    }
    
    private void restoreMovieInfo(Movie sourceMovie, Movie targetMovie, int what, boolean override) {
        restoreMovieInfo(sourceMovie, targetMovie, what, override, false);
    }
    
    private void restoreMovieInfo(Movie sourceMovie, Movie targetMovie, int what, boolean override, boolean protect) {
        
        if ((what & COMINGSOON_RESTORE_TITLE) > 0 && (override || StringTools.isNotValidString(targetMovie.getTitle()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getTitle())) {
                logger.debug("ComingSoon: restoring title");
                targetMovie.setTitle(sourceMovie.getTitle());
            }
        }
        if ((what & COMINGSOON_RESTORE_ORIGINALTITLE) > 0 && (override || StringTools.isNotValidString(targetMovie.getOriginalTitle()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getOriginalTitle())) {
                logger.debug("ComingSoon: restoring original title");
                targetMovie.setOriginalTitle(sourceMovie.getOriginalTitle());
            }
        }
        if ((what & COMINGSOON_RESTORE_PLOT) > 0 && (override || StringTools.isNotValidString(targetMovie.getPlot()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getPlot())) {
                logger.debug("ComingSoon: restoring plot");
                targetMovie.setPlot(sourceMovie.getPlot());
            }
        }
        if ((what & COMINGSOON_RESTORE_OUTLINE) > 0 && (override || StringTools.isNotValidString(targetMovie.getOutline()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getOutline())) {
                logger.debug("ComingSoon: restoring outline");
                targetMovie.setOutline(sourceMovie.getOutline());
            }
        }
        if ((what & COMINGSOON_RESTORE_RATING) > 0 && (override || targetMovie.getRating() < 0)) {
            if (!protect || sourceMovie.getRating() >= 0) {
                logger.debug("ComingSoon: restoring rating");
                targetMovie.setRating(sourceMovie.getRating());
            }
        }
        if ((what & COMINGSOON_RESTORE_RUNTIME) > 0 && (override || StringTools.isNotValidString(targetMovie.getRuntime()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getRuntime())) {
                logger.debug("ComingSoon: restoring runtime");
                targetMovie.setRuntime(sourceMovie.getRuntime());
            }
        }
        if ((what & COMINGSOON_RESTORE_COUNTRY) > 0 && (override || StringTools.isNotValidString(targetMovie.getCountry()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getCountry())) {
                logger.debug("ComingSoon: restoring country");
                targetMovie.setCountry(sourceMovie.getCountry());
            }
        }
        if ((what & COMINGSOON_RESTORE_YEAR) > 0 && (override || StringTools.isNotValidString(targetMovie.getYear()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getYear())) {
                logger.debug("ComingSoon: restoring year");
                targetMovie.setYear(sourceMovie.getYear());
            }
        }
        if ((what & COMINGSOON_RESTORE_COMPANY) > 0 && (override || StringTools.isNotValidString(targetMovie.getCompany()))) {
            if (!protect || StringTools.isValidString(sourceMovie.getCompany())) {
                logger.debug("ComingSoon: restoring company");
                targetMovie.setCompany(sourceMovie.getCompany());
            }
        }
        if ((what & COMINGSOON_RESTORE_GENRES) > 0 && (override || targetMovie.getGenres().isEmpty())) {
            if (!protect || !sourceMovie.getGenres().isEmpty()) {
                logger.debug("ComingSoon: restoring genres");
                targetMovie.setGenres(sourceMovie.getGenres());
            }
        }
        if ((what & COMINGSOON_RESTORE_CAST) > 0 && (override || targetMovie.getCast().isEmpty())) {
            if (!protect || !sourceMovie.getCast().isEmpty()) {
                logger.debug("ComingSoon: restoring cast");
                targetMovie.setCast(sourceMovie.getCast());
            }
        }
        if ((what & COMINGSOON_RESTORE_DIRECTORS) > 0 && (override || targetMovie.getDirectors().isEmpty())) {
            if (!protect || !sourceMovie.getDirectors().isEmpty()) {
                logger.debug("ComingSoon: restoring directors");
                targetMovie.setDirectors(sourceMovie.getDirectors());
            }
        }
        if ((what & COMINGSOON_RESTORE_WRITERS) > 0 && (override || targetMovie.getWriters().isEmpty())) {
            if (!protect || !sourceMovie.getWriters().isEmpty()) {
                logger.debug("ComingSoon: restoring writers");
                targetMovie.setWriters(sourceMovie.getWriters());
            }
        }
    }
    
    protected void generateTrailer(Movie movie) {
        if (trailerMaxResolution.length() == 0) {
            return;
        }
        if (movie.isExtra() || movie.isTVShow()) {
            return;
        }
        
        if (movie.isTrailerExchange()) {
            logger.debug("ComingSoon: Movie has previously been checked for trailers, skipping");
            return;
        }
        
        if (!movie.getExtraFiles().isEmpty()) {
            logger.debug("ComingSoon: Movie has trailers, skipping");
            return;
        }
        
        String trailerUrl = getTrailerUrl(movie);
        
        if (StringTools.isNotValidString(trailerUrl)) {
            logger.debug("ComingSoon: no trailer found");
            if (trailerSetExchange) {
                movie.setTrailerExchange(true);
            }
            return;
        }
        
        logger.debug("ComingSoon: found trailer at URL " + trailerUrl);
        
        MovieFile tmf = new MovieFile();
        tmf.setTitle("TRAILER-" + trailerLabel);
        
        if (trailerDownload) {
            
            // Copied from AppleTrailersPlugin.java
            
            MovieFile mf = movie.getFirstFile();
            String parentPath = mf.getFile().getParent();
            String name = mf.getFile().getName();
            String basename;

            if (mf.getFilename().toUpperCase().endsWith("/VIDEO_TS")) {
                parentPath += File.separator + name;
                basename = name;
            } else if (mf.getFile().getAbsolutePath().toUpperCase().contains("BDMV")) {
                parentPath = new String(parentPath.substring(0, parentPath.toUpperCase().indexOf("BDMV") - 1));
                basename = new String(parentPath.substring(parentPath.lastIndexOf(File.separator) + 1));
            } else {
                int index = name.lastIndexOf(".");
                basename = index == -1 ? name : new String(name.substring(0, index));
            }
            
            String trailerExt = new String(trailerUrl.substring(trailerUrl.lastIndexOf(".")));
            String trailerBasename = FileTools.makeSafeFilename(basename + ".[TRAILER-" + trailerLabel + "]" + trailerExt);
            String trailerFileName = parentPath + File.separator + trailerBasename;
            
            int slash = mf.getFilename().lastIndexOf("/");
            String playPath = slash == -1 ? mf.getFilename() : new String(mf.getFilename().substring(0, slash));
            String trailerPlayFileName = playPath + "/" + HTMLTools.encodeUrl(trailerBasename);
            
            logger.debug("ComingSoon: Found trailer: " + trailerUrl);
            logger.debug("ComingSoon: Download path: " + trailerFileName);
            logger.debug("ComingSoon:      Play URL: " + trailerPlayFileName);
            
            File trailerFile = new File(trailerFileName);
            
            // Check if the file already exists - after jukebox directory was deleted for example
            if (trailerFile.exists()) {
                logger.debug("ComingSoon: Trailer file (" + trailerPlayFileName + ") already exist for " + movie.getBaseName());
            
                tmf.setFilename(trailerPlayFileName);
                movie.addExtraFile(new ExtraFile(tmf));
            } else if (trailerDownload(movie, trailerUrl, trailerFile)) {
                tmf.setFilename(trailerPlayFileName);
                movie.addExtraFile(new ExtraFile(tmf));
            }
            movie.setTrailerExchange(true);
            
        } else {
            tmf.setFilename(trailerUrl);
            movie.addExtraFile(new ExtraFile(tmf));
            movie.setTrailerExchange(true);
        }
        

    }
    
    protected String getTrailerUrl(Movie movie) {
        String trailerUrl = Movie.UNKNOWN;
        String comingSoonId = movie.getId("comingsoon");
        if (StringTools.isNotValidString(comingSoonId)) {
            return Movie.UNKNOWN;
        }
        
        try {
            String searchUrl = COMINGSOON_BASE_URL + COMINGSOON_VIDEO_URL + COMINGSOON_KEY_PARAM + comingSoonId;
            logger.debug("ComingSoon: searching for trailer at URL " + searchUrl);
            String xml = webBrowser.request(searchUrl);
            if (xml.indexOf(COMINGSOON_SEARCH_URL + COMINGSOON_KEY_PARAM + comingSoonId) < 0) {
                // No link to movie page found. We have been redirected to the general video page
                logger.debug("ComingSoon: no video found for movie " + movie.getTitle());
                return Movie.UNKNOWN;
            }
            
            String xmlUrl = Movie.UNKNOWN;
            
            if (trailerPreferredFormat.indexOf(",") < 0) {
                xmlUrl = parseVideoPage(xml, trailerPreferredFormat); 
            } else {
                StringTokenizer st = new StringTokenizer(trailerPreferredFormat, ",");
                while (st.hasMoreTokens()) {
                    xmlUrl = parseVideoPage(xml, st.nextToken());
                    if (StringTools.isValidString(xmlUrl)) {
                        break;
                    }
                }
            }
            
            if (StringTools.isNotValidString(xmlUrl)) {
                logger.debug("No downloadable trailer found for movie: " + movie.getTitle());
                return Movie.UNKNOWN;
            }
            
            String trailerXml = webBrowser.request(xmlUrl);
            int beginUrl = trailerXml.indexOf("http://");
            if (beginUrl >= 0) {
                trailerUrl = new String(trailerXml.substring(beginUrl, trailerXml.indexOf("\"", beginUrl)));
            } else {
                logger.error("ComingSoon: cannot find trailer URL in XML. Layout changed?");
            }
            

        } catch (Exception error) {
            logger.error("ComingSoon: Failed retreiving trailer for movie: " + movie.getTitle());
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
            return Movie.UNKNOWN;
        }
        
        return trailerUrl;
    }
    
    private String parseVideoPage(String xml, String format) {
        String trailerUrl = Movie.UNKNOWN;
        
        int indexOfTrailer = -1;
        String extension;
        
        if (format.equalsIgnoreCase("mov")) {
            extension = "qtl";
        } else if (format.equalsIgnoreCase("wmv")) {
            extension = "wvx";
        } else {
            logger.info("ComingSoon: unknown trailer format " + format);
            return Movie.UNKNOWN;
        }

        if (trailerMaxResolution.equalsIgnoreCase("1080p")) {
            indexOfTrailer = xml.indexOf("1080P." + extension);
        }

        if (indexOfTrailer < 0 && (trailerMaxResolution.equalsIgnoreCase("720p") || trailerMaxResolution.equalsIgnoreCase("1080p"))) {
            indexOfTrailer = xml.indexOf("720P." + extension);
        }
        
        if (indexOfTrailer < 0 && (trailerMaxResolution.equalsIgnoreCase("480p") || trailerMaxResolution.equalsIgnoreCase("720p") || trailerMaxResolution.equalsIgnoreCase("1080p"))) {
            indexOfTrailer = xml.indexOf("480P." + extension);
        }
        
        if (indexOfTrailer >= 0 ) {
            int beginUrl = new String(xml.substring(0, indexOfTrailer)).lastIndexOf("http://");
            trailerUrl = new String(xml.substring(beginUrl, xml.indexOf("\"", beginUrl)));
            logger.debug("ComingSoon: found trailer XML URL " + trailerUrl);
        }
        
        return trailerUrl;
    }
    
    private boolean trailerDownload(final IMovieBasicInformation movie, String trailerUrl, File trailerFile) {
        
        // Copied from AppleTrailersPlugin.java
        
        URL url;
        try {
            url = new URL(trailerUrl);
        } catch (MalformedURLException e) {
            return false;
        }

        ThreadExecutor.enterIO(url);
        HttpURLConnection connection = null;
        Timer timer = new Timer();
        try {
            logger.info("ComingSoon: Download trailer for " + movie.getBaseName());
            final WebStats stats = WebStats.make(url);
            // after make!
            timer.schedule(new TimerTask() {
                private String lastStatus = "";
                public void run() {
                    String status = stats.calculatePercentageComplete();
                    // only print if percentage changed
                    if (status.equals(lastStatus)) {
                        return;
                    }
                    lastStatus = status;
                    // this runs in a thread, so there is no way to output on one line...
                    // try to keep it visible at least...
                    System.out.println("Downloading trailer for " + movie.getTitle() + ": " + stats.statusString());
                }
            }, 1000, 1000);

            connection = (HttpURLConnection) (url.openConnection());
            //connection.setRequestProperty("User-Agent", "QuickTime/7.6.2");
            InputStream inputStream = connection.getInputStream();

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                logger.error("ComingSoon: Download Failed");
                return false;
            }

            FileTools.copy(inputStream, new FileOutputStream(trailerFile), stats);
            System.out.println("Downloading trailer for " + movie.getTitle() + ": " + stats.statusString()); // Output the final stat information (100%)

            return true;

        } catch (Exception error) {
            logger.error("ComingSoon: Download Exception");
            return false;
        } finally {
            timer.cancel();         // Close the timer
            if(connection != null){
                connection.disconnect();
            }
            ThreadExecutor.leaveIO();
        }
    }

}
