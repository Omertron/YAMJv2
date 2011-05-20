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

/*
 Plugin to retrieve movie data from Russian movie database www.kinopoisk.ru
 Written by Yury Sidorov.

 First the movie data is searched in IMDB and TheTvDB.
 After that the movie is searched in kinopoisk and movie data 
 is updated.

 It is possible to specify URL of the movie page on kinopoisk in 
 the .nfo file. In this case movie data will be retrieved from this page only.  
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import com.moviejukebox.model.Award;
import com.moviejukebox.model.AwardEvent;
import com.moviejukebox.model.ExtraFile;
import com.moviejukebox.model.Filmography;
import com.moviejukebox.model.IMovieBasicInformation;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.Person;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.ThreadExecutor;
import com.moviejukebox.tools.WebStats;


public class KinopoiskPlugin extends ImdbPlugin {

    public static String KINOPOISK_PLUGIN_ID = "kinopoisk";
    // Define plot length
    int preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");
    String preferredRating = PropertiesUtil.getProperty("kinopoisk.rating", "imdb");
    protected TheTvDBPlugin tvdb;

    // Copied from ComingSoonPlugin.java
    boolean trailersScannerEnable = PropertiesUtil.getBooleanProperty("trailers.scanner.enable", "true");
    boolean trailerSetExchange = PropertiesUtil.getBooleanProperty("kinopoisk.trailer.setExchange", "false");
    boolean trailerDownload = PropertiesUtil.getBooleanProperty("kinopoisk.trailer.download", "false");

    // Shows what name is on the first position with respect to divider
    String titleLeader = PropertiesUtil.getProperty("kinopoisk.title.leader", "english");
    String titleDivider = PropertiesUtil.getProperty("kinopoisk.title.divider", "-");

    // Set NFO information priority
    protected boolean NFOpriority = PropertiesUtil.getBooleanProperty("kinopoisk.NFOpriority", "false");
    protected boolean NFOplot = false;
    protected boolean NFOcast = false;
    protected boolean NFOgenres = false;
    protected boolean NFOdirectors = false;
    protected boolean NFOwriters = false;
    protected boolean NFOcertification = false;
    protected boolean NFOcountry = false;
    protected String NFOyear = "";
    protected boolean NFOtagline = false;
    protected boolean NFOrating = false;
    protected boolean NFOtop250 = false;
    protected boolean NFOcompany = false;
    protected boolean NFOrelease = false;
    protected boolean NFOfanart = false;
    protected boolean NFOposter = false;
    protected boolean NFOawards = false;

    // Set priority fanart & poster by kinopoisk.ru
    boolean fanArt = PropertiesUtil.getBooleanProperty("kinopoisk.fanart", "false");
    boolean poster = PropertiesUtil.getBooleanProperty("kinopoisk.poster", "false");
    boolean kadr = PropertiesUtil.getBooleanProperty("kinopoisk.kadr", "false");

    // Personal information
    int peopleMax = PropertiesUtil.getIntProperty("plugin.people.maxCount", "15");
    int actorMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.actor", "10");
    int directorMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.director", "2");
    int writerMax = PropertiesUtil.getIntProperty("plugin.people.maxCount.writer", "3");
    int filmographyMax = PropertiesUtil.getIntProperty("plugin.filmography.max", "20");
    int biographyLength = PropertiesUtil.getIntProperty("plugin.biography.maxlength", "500");
    int filmography = PropertiesUtil.getIntProperty("plugin.filmography.max", "20");
    boolean skipVG = PropertiesUtil.getBooleanProperty("plugin.people.skip.VG", "true");
    boolean skipTV = PropertiesUtil.getBooleanProperty("plugin.people.skip.TV", "false");
    boolean skipV = PropertiesUtil.getBooleanProperty("plugin.people.skip.V", "false");

    String skinHome = PropertiesUtil.getProperty("mjb.skin.dir", "./skins/default");
    String jukeboxTempLocationDetails = FileTools.getCanonicalPath(PropertiesUtil.getProperty("mjb.jukeboxTempDir", "./temp") + File.separator + PropertiesUtil.getProperty("mjb.detailsDirName", "Jukebox"));

    public KinopoiskPlugin() {
        super();
        preferredCountry = PropertiesUtil.getProperty("imdb.preferredCountry", "Russia");
        tvdb = new TheTvDBPlugin();
    }

    @Override
    public boolean scan(Movie mediaFile) {
        boolean retval = true;
        String kinopoiskId = mediaFile.getId(KINOPOISK_PLUGIN_ID);

        if (NFOpriority) {
            // checked NFO data
            NFOplot = StringTools.isValidString(mediaFile.getPlot());
            NFOcast = mediaFile.getCast().size() > 0;
            NFOgenres = mediaFile.getGenres().size() > 0;
            NFOdirectors = mediaFile.getDirectors().size() > 0;
            NFOwriters = mediaFile.getWriters().size() > 0;
            NFOcertification = StringTools.isValidString(mediaFile.getCertification());
            NFOcountry = StringTools.isValidString(mediaFile.getCountry());
            NFOyear = StringTools.isValidString(mediaFile.getYear())?mediaFile.getYear():"";
            NFOtagline = StringTools.isValidString(mediaFile.getTagline());
            NFOrating = mediaFile.getRating() > -1;
            NFOtop250 = mediaFile.getTop250() > -1;
            NFOcompany = StringTools.isValidString(mediaFile.getCompany());
            NFOrelease = StringTools.isValidString(mediaFile.getCompany());
            NFOfanart = StringTools.isValidString(mediaFile.getFanartURL());
            NFOposter = StringTools.isValidString(mediaFile.getPosterURL());
            NFOawards = mediaFile.getAwards().size() > 0;
        }

        if (StringTools.isNotValidString(kinopoiskId)) {
            // store original russian title
            String name = mediaFile.getOriginalTitle();

            // It's better to remove everything after dash (-) before call of English plugins...
            final String previousTitle = mediaFile.getTitle();
            int dash = previousTitle.indexOf(titleDivider);
            if (dash != -1) {
                if (titleLeader.equals("english")) {
                    mediaFile.setTitle(previousTitle.substring(0, dash));
                } else {
                    mediaFile.setTitle(previousTitle.substring(dash));
                }
            }
            // Get base info from imdb or tvdb
            if (!mediaFile.isTVShow()) {
                super.scan(mediaFile);
            } else {
                tvdb.scan(mediaFile);
            }

            String year = mediaFile.getYear();
            // Let's replace dash (-) by space ( ) in Title.
            name.replace(titleDivider, " ");
            kinopoiskId = getKinopoiskId(name, year, mediaFile.getSeason());

            if (StringTools.isValidString(year) && StringTools.isNotValidString(kinopoiskId)) {
                // Trying without specifying the year
                kinopoiskId = getKinopoiskId(name, Movie.UNKNOWN, mediaFile.getSeason());
            }
            mediaFile.setId(KINOPOISK_PLUGIN_ID, kinopoiskId);
        } else {
            // If ID is specified in NFO, set original title to unknown
            mediaFile.setTitle(Movie.UNKNOWN);
        }

        if (StringTools.isValidString(kinopoiskId)) {
            // Replace some movie data by data from Kinopoisk
            retval = updateKinopoiskMediaInfo(mediaFile, kinopoiskId);
        }

        if (trailersScannerEnable) {
            generateTrailer(mediaFile);
        }

        return retval;
    }

    @Override
    public void scanNFO(String nfo, Movie movie) {
        logger.debug("Scanning NFO for Kinopoisk Id");
        int beginIndex = nfo.indexOf("kinopoisk.ru/level/1/film/");
        if (beginIndex != -1) {
            StringTokenizer st = new StringTokenizer(nfo.substring(beginIndex + 26), "/");
            movie.setId(KinopoiskPlugin.KINOPOISK_PLUGIN_ID, st.nextToken());
            logger.debug("Kinopoisk Id found in nfo = " + movie.getId(KinopoiskPlugin.KINOPOISK_PLUGIN_ID));
        } else {
            logger.debug("No Kinopoisk Id found in nfo !");
        }
        super.scanNFO(nfo, movie);
    }

    /**
     * Retrieve Kinopoisk matching the specified movie name and year. This routine is base on a Google request.
     */
    private String getKinopoiskId(String movieName, String year, int season) {
        try {
            String kinopoiskId = "";
            String sb = movieName;
            // Unaccenting letters
            sb = Normalizer.normalize(sb, Normalizer.Form.NFD);
            sb = sb.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

            sb = "&m_act[find]=" + URLEncoder.encode(sb, "UTF-8").replace(" ", "+");

            if (season != -1) {
                sb = sb + "&m_act[content_find]=serial";
            } else {
                if (StringTools.isValidString(year)) {
                    try {
                        // Search for year +/-1, since the year is not always correct
                        int y = Integer.parseInt(year);
                        sb = sb + "&m_act[from_year]=" + Integer.toString(y - 1);
                        sb = sb + "&m_act[to_year]=" + Integer.toString(y + 1);
                    } catch (Exception ignore) {
                        sb = sb + "&m_act[year]=" + year;
                    }
                }
            }

            sb = "http://www.kinopoisk.ru/index.php?level=7&from=forma&result=adv&m_act[from]=forma&m_act[what]=content" + sb;
            String xml = webBrowser.request(sb);

            // Checking for zero results
            //if (xml.indexOf("найдено 0 результатов") >= 0) {
            if (xml.indexOf("class=\"search_results\"") < 0) {
                return Movie.UNKNOWN;
            }

            // Checking if we got the movie page directly
            int beginIndex = xml.indexOf("id_film = ");
            if (beginIndex == -1) {
                // It's search results page, searching a link to the movie page
                //beginIndex = xml.indexOf("<!-- результаты поиска");
                beginIndex = xml.indexOf("class=\"search_results\"");
                if (beginIndex == -1) {
                    return Movie.UNKNOWN;
                }

                //beginIndex = xml.indexOf("href=\"/level/1/film/", beginIndex);
                beginIndex = xml.indexOf("/level/1/film/", beginIndex);
                if (beginIndex == -1) {
                    return Movie.UNKNOWN;
                }

                //StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 20), "/\"");
                StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 14), "/\"");
                kinopoiskId = st.nextToken();
            } else {
                // It's the movie page
                StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 10), ";");
                kinopoiskId = st.nextToken();
            }

            if (!kinopoiskId.equals("")) {
                // Check if ID is integer
                try {
                    Integer.parseInt(kinopoiskId);
                } catch (Exception ignore) {
                    return Movie.UNKNOWN;
                }
                return kinopoiskId;
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.error("Failed retreiving Kinopoisk Id for movie : " + movieName);
            logger.error("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    /**
     * Scan Kinopoisk html page for the specified movie
     */
    private boolean updateKinopoiskMediaInfo(Movie movie, String kinopoiskId) {
        try {
            String originalTitle = movie.getTitle();
            String newTitle = originalTitle;
            String xml = webBrowser.request("http://www.kinopoisk.ru/level/1/film/" + kinopoiskId);

            // Work-around for issue #649
            xml = xml.replace((CharSequence)"&#133;", (CharSequence)"&hellip;");
            xml = xml.replace((CharSequence)"&#151;", (CharSequence)"&mdash;");

            // Title
            if (!movie.isOverrideTitle()) {
                newTitle = HTMLTools.extractTag(xml, "class=\"moviename-big\">", 0, "<>");
                if (!newTitle.equals(Movie.UNKNOWN)) {
                    int i = newTitle.indexOf("(сериал");
                    if (i >= 0) {
                        newTitle = newTitle.substring(0, i);
                        movie.setMovieType(Movie.TYPE_TVSHOW);
                    }
                    newTitle = newTitle.replace('\u00A0', ' ').trim();
                    if (movie.getSeason() != -1) {
                        newTitle = newTitle + ", сезон " + String.valueOf(movie.getSeason());
                    }

                    // Original title
                    originalTitle = newTitle;
                    for (String s : HTMLTools.extractTags(xml, "class=\"moviename-big\">", "</span>", "<span", "</span>")) {
                        if (!s.isEmpty()) {
                            originalTitle = s;
                            newTitle = newTitle + " / " + originalTitle;
                        }
                        break;
                    }
                } else {
                    newTitle = originalTitle;
                }
            }

            // Plot
            if (!NFOplot) {
                StringBuffer plot = new StringBuffer();
                for (String subPlot : HTMLTools.extractTags(xml, "<span class=\"_reachbanner_\"", "</span>", "", "<")) {
                    if (!subPlot.isEmpty()) {
                        if (plot.length() > 0) {
                            plot.append(" ");
                        }
                        plot.append(subPlot);
                    }
                }

                String newPlot = "";
                if (plot.length() == 0) {
                    newPlot = movie.getPlot();
                } else {
                    newPlot = plot.toString();
                }

                newPlot = StringTools.trimToLength(newPlot, preferredPlotLength, true, plotEnding);
                movie.setPlot(newPlot);
            }

            for (String item : HTMLTools.extractTags(xml, "<table class=\"info\">", "</table>", "<tr>", "</tr>")) {
                item = "<td>" + item + "</tr>";

                // Genres
                if (!NFOgenres) {
                    LinkedList<String> newGenres = new LinkedList<String>();
                    boolean GenresFound;
                    GenresFound = false;
                    for (String genre : HTMLTools.extractTags(item, ">жанр<", "</tr>", "<a href=\"/level/10", "</a>")) {
                        GenresFound = true;
                        genre = genre.substring(0, 1).toUpperCase() + genre.substring(1, genre.length());
                        if (genre.equalsIgnoreCase("мультфильм")) {
                            newGenres.addFirst(genre);
                        } else {
                            newGenres.add(genre);
                        }
                    }
                    if (GenresFound) {
                    // Limit genres count
                        int maxGenres = 9;
                        try {
                            maxGenres = PropertiesUtil.getIntProperty("genres.max", "9");
                        } catch (Exception ignore) {
                            //
                        }
                        while (newGenres.size() > maxGenres) {
                            newGenres.removeLast();
                        }

                        movie.setGenres(newGenres);
                    }
                }

                // Certification from MPAA
                if (!NFOcertification) {
                    for (String mpaaTag : HTMLTools.extractTags(item, ">рейтинг MPAA<", "</tr>", "<a href=\'/level/38", "</a>")) {
                        // Now need scan for 'alt' attribute of 'img'
                        String key = "alt='рейтинг ";
                        int pos = mpaaTag.indexOf(key);
                        if (pos != -1) {
                            int start = pos + key.length();
                            pos = mpaaTag.indexOf("'", start);
                            if (pos != -1) {
                                mpaaTag = mpaaTag.substring(start, pos);
                                movie.setCertification(mpaaTag);
                            }
                        }
                        break;
                    }
                }

                // Country
                if (!NFOcountry) {
                    for (String country : HTMLTools.extractTags(item, ">страна<", "</tr>", "<a href=\"/level/10", "</a>")) {
                        movie.setCountry(country);
                        break;
                    }
                }

                // Year
                if (!movie.isOverrideYear() && NFOyear.equals("")) {
                    for (String year : HTMLTools.extractTags(item, ">год<", "</tr>", "<a href=\"/level/10", "</a>")) {
                        movie.setYear(year);
                        break;
                    }
                } else if (!NFOyear.equals("")) {
                    movie.setYear(NFOyear);
                }

                // Run time
                if (movie.getRuntime().equals(Movie.UNKNOWN)) {
                    for (String runtime : HTMLTools.extractTags(item, ">время<", "</tr>", "<td", "</td>")) {
                        movie.setRuntime(runtime);
                        break;
                    }
                }

                // Tagline
                if (!NFOtagline) {
                    for (String tagline : HTMLTools.extractTags(item, ">слоган<", "</tr>", "<td ", "</td>")) {
                        if (tagline.length() > 0) {
                            movie.setTagline(tagline.replace("\u00AB", "\"").replace("\u00BB", "\""));
                            break;
                        }
                    }
                }

                // Release date
                if (!NFOrelease) {
                    String releaseDate = "";
                    for (String release : HTMLTools.extractTags(item, ">премьера (мир)<", "</tr>", "<a href=\"/level/80", "</a>")) {
                        releaseDate = release;
                        break;
                    }
                    if (releaseDate.equals("")) {
                        for (String release : HTMLTools.extractTags(item, ">премьера (РФ)<", "</tr>", "<a href=\"/level/8", "</a>")) {
                            releaseDate = release;
                            break;
                        }
                    }
                    if (!releaseDate.equals("")) {
                        movie.setReleaseDate(releaseDate);
                    }
                }
            }

            // Rating
            if (!NFOrating) {
                int kinopoiskRating = -1;
                for (String rating : HTMLTools.extractTags(xml, "<a href=\"/level/83/film/" + kinopoiskId + "/\"", "</a>", "", "<")) {
                    try {
                        kinopoiskRating = (int)(Float.parseFloat(rating) * 10);
                    } catch (Exception ignore) {
                        // Ignore
                    }
                    break;
                }

                int imdbRating = movie.getRating();
                if (imdbRating == -1) {
                    // Get IMDB rating from kinopoisk page
                    String rating = HTMLTools.extractTag(xml, ">IMDB:", 0, "<(");
                    if (!rating.equals(Movie.UNKNOWN)) {
                        try {
                            imdbRating = (int)(Float.parseFloat(rating) * 10);
                        } catch (Exception ignore) {
                            // Ignore
                        }
                    }
                }

                int r = kinopoiskRating;
                if (imdbRating != -1) {
                    if (preferredRating.equals("imdb") || kinopoiskRating == -1) {
                        r = imdbRating;
                    } else if (preferredRating.equals("average")) {
                        r = (kinopoiskRating + imdbRating) / 2;
                    } else if (preferredRating.equals("combine")) {
                        r += imdbRating * 1000;
                    }
                }
                movie.setRating(r);
            }

            // Top250
            // Clear previous rating : if KinoPoisk is selected as search engine - 
            // it means user wants KinoPoisk's rating, not global.
            if (!NFOtop250) {
                movie.setTop250(-1);
                String top250 = HTMLTools.extractTag(xml, "<a href=\"/level/20/#", 0, "\"");
                try {
                    movie.setTop250(Integer.parseInt(top250));
                } catch (Exception ignore) {
                    // Ignore
                }
            }

            // Poster
            String posterURL = movie.getPosterURL();
            if (StringTools.isNotValidString(posterURL) || (!NFOposter && poster)) {
                if (poster) {
                    String previousURL = posterURL;
                    posterURL = Movie.UNKNOWN;

                    // Load page with all poster
                    String wholeArts = webBrowser.request("http://www.kinopoisk.ru/level/17/film/" + kinopoiskId + "/");
                    if (StringTools.isValidString(wholeArts)) {
                        if (wholeArts.indexOf("<table class=\"fotos") != -1) {
                            String picture = HTMLTools.extractTag(wholeArts, "src=\"http://st.kinopoisk.ru/images/poster/sm_", 0, "\"");
                            if (StringTools.isValidString(picture)) {
                                posterURL = "http://st.kinopoisk.ru/images/poster/" + picture;
                            }
                        }
                    }

                    if (StringTools.isNotValidString(posterURL)) {
                        posterURL = previousURL;
                    }

                    if (StringTools.isValidString(posterURL)) {
                        movie.setPosterURL(posterURL);
                        movie.setPosterFilename(movie.getBaseName() + ".jpg");
                        logger.debug("KinoPoisk Plugin: Set poster URL to " + posterURL + " for " + movie.getBaseName());
                    }
                }
                if (StringTools.isNotValidString(movie.getPosterURL())) {
                    movie.setTitle(originalTitle);
                    // Removing Poster info from plugins. Use of PosterScanner routine instead.
                    // movie.setPosterURL(locatePosterURL(movie, ""));
                }
            }

            // Fanart
            String fanURL = movie.getFanartURL();
            if (StringTools.isNotValidString(fanURL) || (!NFOfanart && fanArt)) {
                try {
                    String previousURL = fanURL;
                    fanURL = Movie.UNKNOWN;

                    // Load page with all wallpaper
                    String wholeArts = webBrowser.request("http://www.kinopoisk.ru/level/12/film/" + kinopoiskId + "/");
                    if (StringTools.isValidString(wholeArts)) {
                        if (wholeArts.indexOf("<table class=\"fotos") != -1) {
                            String picture = HTMLTools.extractTag(wholeArts, "src=\"http://st.kinopoisk.ru/images/wallpaper/sm_", 0, ".jpg");
                            if (StringTools.isValidString(picture)) {
                                String size = HTMLTools.extractTag(wholeArts, "<u><a href=\"/picture/" + picture + "/w_size/", 0, "/");
                                wholeArts = webBrowser.request("http://www.kinopoisk.ru/picture/" + picture + "/w_size/" + size);
                                if (StringTools.isValidString(wholeArts)) {
                                    picture = HTMLTools.extractTag(wholeArts, "src=\"http://st.kinopoisk.ru/im/wallpaper/", 0, "\"");
                                    if (StringTools.isValidString(picture)) {
                                        fanURL = "http://st.kinopoisk.ru/im/wallpaper/" + picture;
                                    }
                                }
                            }
                        }
                    }

                    if (kadr && StringTools.isNotValidString(fanURL)) {
                        // Load page with all videoimage
                        wholeArts = webBrowser.request("http://www.kinopoisk.ru/level/13/film/" + kinopoiskId + "/");
                        if (StringTools.isValidString(wholeArts)) {
                            // Looking for photos table
                            int photosInd = wholeArts.indexOf("<table class=\"fotos");
                            if (photosInd != -1) {
                                String picture = HTMLTools.extractTag(wholeArts, "src=\"http://st.kinopoisk.ru/images/kadr/sm_", 0, "\"");
                                if (StringTools.isValidString(picture)) {
                                    fanURL = "http://www.kinopoisk.ru/images/kadr/" + picture;
                                }
                            }
                        }
                    }

                    if (StringTools.isNotValidString(fanURL)) {
                        fanURL = previousURL;
                    }

                    if (StringTools.isValidString(fanURL)) {
                        movie.setFanartURL(fanURL);
                        movie.setFanartFilename(movie.getBaseName() + fanartToken + "." + fanartExtension);
                        logger.debug("KinoPoisk Plugin: Set fanart URL to " + fanURL + " for " + movie.getBaseName());
                    }
                } catch (Exception ignore) {
                    // Ignore
                }
            }

            // Studio/Company
            if (!NFOcompany) {
                xml = webBrowser.request("http://www.kinopoisk.ru/level/91/film/" + kinopoiskId);
                if (StringTools.isValidString(xml)) {
                    int studioInx = xml.indexOf("/level/10/m_act[studio]/");
                    if (studioInx != -1) {
                        String studio = "";
                        for (String tmp : HTMLTools.extractTags(xml, "<a href=\"/level/10/m_act[studio]/", "</a>", "", "<")) {
                            studio = tmp;
                            break;
                        }
                        if (studio.length() > 0) {
                            movie.setCompany(studio);
                        }
                    }
                }
            }

            // Awards
            if (!NFOawards) {
                xml = webBrowser.request("http://www.kinopoisk.ru/level/94/film/" + kinopoiskId);
                if (StringTools.isValidString(xml)) {
                    int beginIndex = xml.indexOf("/level/94/award/");
                    if (beginIndex != -1) {
                        Collection<AwardEvent> awards = new ArrayList<AwardEvent>();
                        for (String item : HTMLTools.extractTags(xml, "<table cellspacing=0 cellpadding=0 border=0 width=100%>", "<br><br><br><br><br><br>", "<table cellspacing=0 cellpadding=0 border=0 width=100% style=\"border:1px solid #ccc; text-align: left\">", "</table>")) {
                            String name = Movie.UNKNOWN;
                            int year = -1;
                            int won = 0;
                            int nominated = 0;
                            for (String tmp : HTMLTools.extractTags(item, "<td height=40 class=\"news\" style=\"padding:10px\">", "</td>", "<a href=\"/level/94/award/", "</a>")) {
                                int coma = tmp.indexOf(",");
                                name = tmp.substring(0, coma);
                                year = Integer.parseInt(tmp.substring(coma + 2, coma + 6));
                                break;
                            }
                            for (String tmp : HTMLTools.extractTags(item, ">Победитель<", ":", "(", ")")) {
                                won = Integer.parseInt(tmp);
                                break;
                            }
                            for (String tmp : HTMLTools.extractTags(item, ">Номинации<", ":", "(", ")")) {
                                nominated = Integer.parseInt(tmp);
                                break;
                            }
                            if (StringTools.isValidString(name) && year > 1900 && year < 2020) {
                                Award award = new Award();
                                award.setName(name);
                                award.setYear(year);
                                award.setWon(won);
                                award.setNominated(nominated);

                                AwardEvent event = new AwardEvent();
                                event.setName(name);
                                event.addAward(award);
                                awards.add(event);
                            }
                        }
                        if (awards.size() > 0) {
                            movie.setAwards(awards);
                        }
                    }
                }
            }

            // Cast enhancement
            if (!NFOcast || !NFOdirectors || !NFOwriters) {
                xml = webBrowser.request("http://www.kinopoisk.ru/level/19/film/" + kinopoiskId);
                if (StringTools.isValidString(xml)) {
                    int peopleCount = 0;
                    if (!NFOdirectors) {
                        peopleCount = scanMoviePerson(movie, xml, "director", writerMax, peopleCount);
                    }
                    if (!NFOwriters) {
                        peopleCount = scanMoviePerson(movie, xml, "writer", writerMax, peopleCount);
                    }
                    if (!NFOcast) {
                        peopleCount = scanMoviePerson(movie, xml, "actor", actorMax, peopleCount);
                    }
                    Collection<Person> outcast = new ArrayList<Person>();
                    for (Person p : movie.getPeople()) {
                        if (StringTools.isNotValidString(p.getId(KINOPOISK_PLUGIN_ID))) {
                            outcast.add(p);
                        }
                    }
                    for (Person p : outcast) {
                        movie.removePerson(p);
                    }
                }
            }

            // Finally set title
            movie.setTitle(newTitle);
        } catch (Exception error) {
            logger.error("Failed retreiving movie data from Kinopoisk : " + kinopoiskId);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
        return true;
    }

    protected int scanMoviePerson(Movie movie, String xml, String mode, int personMax, int peopleCount) {
        if (peopleCount < peopleMax && personMax > 0 && xml.indexOf("<a name=\"" + mode + "\">") != -1) {
            if (mode.equals("actor")) {
                movie.clearCast();
            } else if (mode.equals("director")) {
                movie.clearDirectors();
            } else if (mode.equals("writer")) {
                movie.clearWriters();
            }

            int count = 0;
            for (String item : HTMLTools.extractTags(xml, "<a name=\"" + mode + "\">", "<a href=\"#top\" class=\"continue\">", "<div class=\"dub ", "<div class=\"clear\"></div></div>")) {
                String name = HTMLTools.extractTag(item, "<div class=\"name\"><a href=\"/level/4/people/", "</a>");
                int beginIndex = name.indexOf("/\">");
                String personID = name.substring(0, beginIndex);
                name = name.substring(beginIndex + 3);
                String origName = HTMLTools.extractTag(item, "<span class=\"gray\">", "</span>");
                String role = Movie.UNKNOWN;
                String dubler = Movie.UNKNOWN;
                if (mode.equals("actor")) {
                    role = HTMLTools.extractTag(item, "<div class=\"role\">", "</div>").replaceAll("^\\.+\\s", "").replaceAll("\\s.$", "");
                    if (item.indexOf("<div class=\"dubInfo\">") > -1) {
                        dubler = HTMLTools.extractTag(HTMLTools.extractTag(item, "<div class=\"dubInfo\">", "</span></div>"), "<div class=\"name\"><a href=\"/level/4/people/", "</a>");
                        dubler = dubler.substring(dubler.indexOf("/\">") + 3);
                    }
                }
                count++;
                peopleCount++;
                boolean found = false;
                for (Person p : movie.getPeople()) {
                    if (p.getName().equalsIgnoreCase(origName) && p.getJob().equalsIgnoreCase(mode)) {
                        p.setId(KINOPOISK_PLUGIN_ID, personID);
                        p.setTitle(name);
                        p.setCharacter(role);
                        p.setDoublage(dubler);
                        if (mode.equals("actor")) {
                            movie.addActor(StringTools.isValidString(name)?name:origName);
                        } else if (mode.equals("director")) {
                            movie.addDirector(StringTools.isValidString(name)?name:origName);
                        } else if (mode.equals("writer")) {
                            movie.addWriter(StringTools.isValidString(name)?name:origName);
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (mode.equals("actor")) {
                        movie.addActor(KINOPOISK_PLUGIN_ID + ":" + personID, origName + ":" + name, role, "http://www.kinopoisk.ru/level/4/people/" + personID + "/", dubler);
                    } else if (mode.equals("director")) {
                        movie.addDirector(KINOPOISK_PLUGIN_ID + ":" + personID, origName + ":" + name, "http://www.kinopoisk.ru/level/4/people/" + personID + "/");
                    } else if (mode.equals("writer")) {
                        movie.addWriter(KINOPOISK_PLUGIN_ID + ":" + personID, origName + ":" + name, "http://www.kinopoisk.ru/level/4/people/" + personID + "/");
                    }
                }
                if (count == personMax || peopleCount == peopleMax) {
                    break;
                }
            }
        }
        return peopleCount;
    }

    // Copied from ComingSoonPlugin.java
    protected void generateTrailer(Movie movie) {
        if (movie.isExtra() || movie.isTVShow()) {
            return;
        }
        if (!movie.getExtraFiles().isEmpty()) {
            logger.debug("KinoPoisk Plugin: Movie has trailers, skipping");
            return;
        }

        String trailerUrl = getTrailerUrl(movie);
        if (StringTools.isNotValidString(trailerUrl)) {
            logger.debug("KinoPoisk Plugin: no trailer found");
            if (trailerSetExchange) {
                movie.setTrailerExchange(true);
            }
            return;
        }

        logger.debug("KinoPoisk Plugin: found trailer at URL " + trailerUrl);

        MovieFile tmf = new MovieFile();
        tmf.setTitle("TRAILER-ru");

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
            String trailerBasename = FileTools.makeSafeFilename(basename + ".[TRAILER-ru]" + trailerExt);
            String trailerFileName = parentPath + File.separator + trailerBasename;

            int slash = mf.getFilename().lastIndexOf("/");
            String playPath = slash == -1 ? mf.getFilename() : new String(mf.getFilename().substring(0, slash));
            String trailerPlayFileName = playPath + "/" + HTMLTools.encodeUrl(trailerBasename);

            logger.debug("KinoPoisk Plugin: Found trailer: " + trailerUrl);
            logger.debug("KinoPoisk Plugin: Download path: " + trailerFileName);
            logger.debug("KinoPoisk Plugin:      Play URL: " + trailerPlayFileName);
            File trailerFile = new File(trailerFileName);

            // Check if the file already exists - after jukebox directory was deleted for example
            if (trailerFile.exists()) {
                logger.debug("KinoPoisk Plugin: Trailer file (" + trailerPlayFileName + ") already exist for " + movie.getBaseName());
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

        // Copied from ComingSoonPlugin.java

        String trailerUrl = Movie.UNKNOWN;
        String kinopoiskId = movie.getId(KINOPOISK_PLUGIN_ID);
        if (StringTools.isNotValidString(kinopoiskId)) {
            return Movie.UNKNOWN;
        }
        try {
            String searchUrl = "http://www.kinopoisk.ru/level/16/film/" + kinopoiskId;
            logger.debug("KinoPoisk Plugin: searching for trailer at URL " + searchUrl);
            String xml = webBrowser.request(searchUrl);

            int beginIndex = xml.indexOf("/level/16/film/" + kinopoiskId + "/t/");
            if (beginIndex < 0) {
                // No link to movie page found. We have been redirected to the general video page
                logger.debug("KinoPoisk Plugin: no video found for movie " + movie.getTitle());
                return Movie.UNKNOWN;
            }

            String xmlUrl = new String("http://www.kinopoisk.ru" + xml.substring(beginIndex, xml.indexOf("/\"", beginIndex)));
            if (StringTools.isNotValidString(xmlUrl)) {
                logger.debug("KinoPoisk Plugin: no downloadable trailer found for movie: " + movie.getTitle());
                return Movie.UNKNOWN;
            }

            String trailerXml = webBrowser.request(xmlUrl);
            int beginUrl = trailerXml.indexOf("<a href=\"/getlink.php");
            if (beginUrl >= 0) {
                while (trailerXml.indexOf("<a href=\"/getlink.php", beginUrl + 1) > 0) {
                    beginUrl = trailerXml.indexOf("<a href=\"/getlink.php", beginUrl + 1);
                }
                beginUrl = trailerXml.indexOf("http://", beginUrl);
                trailerUrl = new String(trailerXml.substring(beginUrl, trailerXml.indexOf("\"", beginUrl)));
            } else {
                logger.error("KinoPoisk Plugin: cannot find trailer URL in XML. Layout changed?");
            }
        } catch (Exception error) {
            logger.error("KinoPoisk Plugin: Failed retreiving trailer for movie: " + movie.getTitle());
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
            return Movie.UNKNOWN;
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
            logger.info("KinoPoisk Plugin: Download trailer for " + movie.getBaseName());
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
            connection.setRequestProperty("User-Agent", "QuickTime/7.6.9");
            InputStream inputStream = connection.getInputStream();

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                logger.error("KinoPoisk Plugin: Download Failed");
                return false;
            }

            FileTools.copy(inputStream, new FileOutputStream(trailerFile), stats);
            System.out.println("Downloading trailer for " + movie.getTitle() + ": " + stats.statusString()); // Output the final stat information (100%)

            return true;
        } catch (Exception error) {
            logger.error("KinoPoisk Plugin: Download Exception");
            return false;
        } finally {
            timer.cancel();         // Close the timer
            if(connection != null){
                connection.disconnect();
            }
            ThreadExecutor.leaveIO();
        }
    }

    @Override
    public boolean scan(Person person) {
        String kinopoiskId = person.getId(KINOPOISK_PLUGIN_ID);
        if (StringTools.isNotValidString(kinopoiskId)) {
            kinopoiskId = getKinopoiskPersonId(person.getName(), person.getJob());
            person.setId(KINOPOISK_PLUGIN_ID, kinopoiskId);
        }

        boolean retval = super.scan(person);
        if (StringTools.isValidString(kinopoiskId)) {
            retval = updateKinopoiskPersonInfo(person);
        }
        return retval;
    }

    private boolean updateKinopoiskPersonInfo(Person person) {
        String kinopoiskId = person.getId(KINOPOISK_PLUGIN_ID);
        boolean returnStatus = false;

        try {
            String xml = webBrowser.request("http://www.kinopoisk.ru/level/4/people/" + kinopoiskId + "/view_info/ok/");
            if (StringTools.isValidString(xml)) {
                if (StringTools.isNotValidString(person.getName())) {
                    person.setName(HTMLTools.extractTag(xml, "<span style=\"font-size:13px;color:#666\">", "</span>"));
                }
                person.setTitle(HTMLTools.extractTag(xml, "<h1 style=\"padding:0px;margin:0px\" class=\"moviename-big\">", "</h1>"));

                String bd = HTMLTools.extractTag(xml, "<td class=\"birth\">", "</td>");
                if (StringTools.isValidString(bd) && bd.indexOf("</a>") > -1) {
                    bd = HTMLTools.removeHtmlTags(bd);
                    bd = bd.substring(0, bd.indexOf("•")).replace(",", "").replaceAll("\\s$", "");
                    if (StringTools.isValidString(bd)) {
                        person.setBirthday(bd);
                    }
                }

                String bp = HTMLTools.extractTag(xml, ">место рождения</td><td>", "</td>");
                if (StringTools.isValidString(bp)) {
                    bp = HTMLTools.removeHtmlTags(bp);
                    if (StringTools.isValidString(bp)) {
                        person.setBirthPlace(bp);
                    }
                }

                String km = HTMLTools.extractTag(xml, ">всего фильмов</td><td>", "</td>");
                if (StringTools.isValidString(km)) {
                    person.setKnownMovies(Integer.parseInt(km));
                }

                String bio = "";
                for (String item : HTMLTools.extractTags(xml, "<ul class=\"trivia\">", "</ul>", "<li class=\"trivia\">", "</li>")) {
                    bio += HTMLTools.removeHtmlTags(item).replaceAll("\u0097", "-") + " ";
                }
                if (StringTools.isValidString(bio)) {
                    bio = StringTools.trimToLength(bio, biographyLength, true, plotEnding);
                    person.setBiography(bio);
                }

                if (xml.indexOf("http://st.kinopoisk.ru/images/actor/" + kinopoiskId + ".jpg") > -1 && StringTools.isNotValidString(person.getPhotoURL())) {
                    person.setPhotoURL("http://st.kinopoisk.ru/images/actor/" + kinopoiskId + ".jpg");
                }
            }

            xml = webBrowser.request("http://www.kinopoisk.ru/level/4/people/" + kinopoiskId + "/sort/rating/");
            if (StringTools.isValidString(xml)) {
                TreeMap<Float, Filmography> filmography = new TreeMap<Float, Filmography>();
                for (String block : HTMLTools.extractTags(xml, "<center><div style='position: relative' ></div></center>", "<tr><td><br /><br /><br /><br /><br /><br /></td></tr>", "<tr><td colspan=3 height=4><spacer type=block height=4></td></tr>", "</table><br />")) {
                    String job = HTMLTools.extractTag(block, "<div style=\"padding-left: 9px\" id=\"", "\">");
                    if (job.equals("producer")) {
                        job = "Producer";
                    } else if (job.equals("director")) {
                        job = "Director";
                    } else if (job.equals("writer")) {
                        job = "Writer";
                    } else if (job.equals("actor")) {
                        job = "Actor";
                    } else if (job.equals("editor")) {
                        job = "Editor";
                    } else if (job.equals("himself")) {
                        job = "Himself";
                    } else if (job.equals("design")) {
                        job = "Design";
                    } else if (job.equals("operator")) {
                        job = "Operator";
                    } else if (job.equals("composer")) {
                        job = "Music";
                    }
                    for (String item : HTMLTools.extractTags(block + "</table>", "<table cellspacing=0 cellpadding=3 border=0 width=100%>", "</table>" , "<tr>", "</tr>")) {
                        String id = HTMLTools.extractTag(item, " href=\"/level/1/film/", "/\">");
                        String URL = "http://www.kinopoisk.ru/level/1/film/" + id + "/";
                        String title = HTMLTools.extractTag(item, " href=\"/level/1/film/" + id + "/\">", "</a>").replaceAll("\u00A0", " ").replaceAll("&nbsp;", " ");
                        if (skipTV && (title.indexOf(" (сериал)") > -1 || title.indexOf(" (ТВ)") > -1)) {
                            continue;
                        } else if (skipV && title.indexOf(" (видео)") > -1) {
                            continue;
                        }
                        String year = Movie.UNKNOWN;
                        if (title.lastIndexOf("(") > -1) {
                            year = title.substring(title.lastIndexOf("(")).replace(")$", "");
                        }
                        String name = HTMLTools.extractTag(item, "<span style=\"color:#999\">", "</span>").replaceAll("\u00A0", " ").replaceAll("&nbsp;", " ");
                        String character = Movie.UNKNOWN;
                        if (name.indexOf(" ... ") > -1) {
                            String[] names = name.split(" \\.\\.\\. ");
                            name = names[0];
                            if (job.equals("Actor")) {
                                character = names[1];
                            }
                        }
                        if (name.endsWith(", The")) {
                            name = "The " + name.replace(", The", "");
                        }
                        if (StringTools.isValidString(year)) {
                            name += " " + year;
                        }
                        String rating = HTMLTools.extractTag(item, " class=\"continue\" >", "</a>");
                        if (StringTools.isNotValidString(rating)) {
                            rating = HTMLTools.extractTag(item, " class=\"continue\" style='color: #777;'>", "</a>");
                            if (StringTools.isNotValidString(rating)) {
                                rating = "0";
                            }
                        }

                        float key = 10 - (Float.valueOf(rating).floatValue() + Float.valueOf("0.00" + id).floatValue());

                        if (filmography.get(key) == null) {
                            Filmography film = new Filmography();
                            film.setId(KINOPOISK_PLUGIN_ID, id);
                            film.setName(name);
                            film.setTitle(title);
                            film.setJob(job);
                            film.setCharacter(character);
                            film.setDepartment();
                            film.setRating(rating);
                            film.setUrl(URL);
                            filmography.put(key, film);
                        }
                    }
                }
                if (filmography.size() > 0) {
                    person.clearFilmography();
                    Iterator<Float> iterFilm = filmography.keySet().iterator();
                    Object obj;
                    int count = 0;
                    while (iterFilm.hasNext() && count < filmographyMax) {
                        obj = iterFilm.next();
                        person.addFilm(filmography.get(obj));
                        count++;
                    }
                }
                returnStatus = true;
            }
        } catch (Exception error) {
            logger.error("Failed retreiving IMDb data for person : " + kinopoiskId);
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
        return returnStatus;
    }

    protected String getKinopoiskPersonId(String person, String mode) {
        String personId = Movie.UNKNOWN;
        try {
            String sb = person;
            sb = sb.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            sb = "&m_act[find]=" + URLEncoder.encode(sb, "UTF-8").replace(" ", "+");

            sb = "http://www.kinopoisk.ru/index.php?level=7&from=forma&result=adv&m_act[from]=forma&m_act[what]=actor" + sb + "&m_act[work]=" + mode;
            String xml = webBrowser.request(sb);

            // Checking for zero results
            int beginIndex = xml.indexOf("class=\"search_results\"");
            if (beginIndex > 0) {
                // Checking if we got the person page directly
                int beginInx = xml.indexOf("id_actor = ");
                if (beginInx == -1) {
                    // It's search results page, searching a link to the person page
                    beginIndex = xml.indexOf("/level/4/people/", beginIndex);
                    if (beginIndex != -1) {
                        StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 16), "/");
                        personId = st.nextToken();
                    }
                } else {
                    // It's the person page
                    StringTokenizer st = new StringTokenizer(xml.substring(beginInx + 11), ";");
                    personId = st.nextToken();
                }
            }
        } catch (Exception error) {
            logger.error("Error : " + error.getMessage());
        }
        return personId;
    }
}
