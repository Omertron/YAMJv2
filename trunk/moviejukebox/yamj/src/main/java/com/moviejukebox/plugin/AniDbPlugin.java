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

import static com.moviejukebox.tools.StringTools.cleanString;
import static com.moviejukebox.tools.StringTools.isValidString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.anidb.Anime;
import net.anidb.Episode;
import net.anidb.checksum.Ed2kChecksum;
import net.anidb.udp.AniDbException;
import net.anidb.udp.UdpConnection;
import net.anidb.udp.UdpConnectionException;
import net.anidb.udp.UdpConnectionFactory;
import net.anidb.udp.UdpReturnCodes;
import net.anidb.udp.mask.AnimeFileMask;
import net.anidb.udp.mask.AnimeMask;
import net.anidb.udp.mask.FileMask;

import org.apache.log4j.Logger;
import org.pojava.datetime.DateTime;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTable;
import com.j256.ormlite.table.TableUtils;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.Cache;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;

/**
 * AniDB Plugin
 * @author stuart.boston
 * @version 1
 * 
 */
public class AniDbPlugin implements MovieDatabasePlugin {
    // Issue 258: http://code.google.com/p/moviejukebox/issues/detail?id=258
    // API Documentation: http://grizzlyxp.bplaced.net/projects/javaanidbapi/index.html
    // AniDb Documentation: http://wiki.anidb.info/w/UDP_API_Definition
    
    // TODO: Keep the plugin logged in until the end of the run
    
    private static Logger logger = Logger.getLogger("moviejukebox");
    public static final String ANIDB_PLUGIN_ID = "anidb";
    private static final String ANIDB_CLIENT_NAME = "yamj";
    private static final int ANIDB_CLIENT_VERSION = 1;
    private static int anidbPort;
    private static final int ED2K_CHUNK_SIZE = 9728000;
    @SuppressWarnings("unused")
    private static final String WEBHOST = "anidb.net";
    private AnimeMask anidbMask;
    // 5 groups: 1=Scene Group, 2=Anime Name, 3=Episode, 4=Episode Title, 5=Remainder
    private static final String REGEX_TVSHOW = "(?i)(\\[.*?\\])?+(\\w.*?)(?:[\\. _-]|ep)(\\d{1,3})(\\w+)(.+)";
    // 4 groups: 1=Scene Group, 2=Anime Name, 3=CRC, 4=Remainder
    private static final String REGEX_MOVIE = "(\\[.*?\\])?+([\\w-]+)(\\[\\w{8}\\])?+(.*)";
    private static final String CRC_REGEX = "(.*)(\\[\\w{8}\\])(.*)";
    
    private static final String PICTURE_URL_BASE = "http://1.2.3.12/bmi/img7.anidb.net/pics/anime/";
    
    private static final String THETVDB_ANIDB_MAPPING_URL = "e:\\downloads\\anime-list.xml";//"http://sites.google.com/site/anidblist/anime-list.xml";
    
    @SuppressWarnings("unused")
    private int preferredPlotLength;
    private static final String LOG_MESSAGE = "AniDbPlugin: ";
    
    private static UdpConnection anidbConn = null;
    private static boolean anidbConnectionProtection = false;   // Set this to true to stop further calls
    private static String anidbUsername;
    private static String anidbPassword;

    private static boolean hash;
    private boolean getAdditionalInformationFromTheTvDB = false;
    private HashMap<Long, AnimeIdMapping> tvdbMappings;
    
    
    private static final int TABLE_VERSION = 1;
    private Dao<AnidbLocalFile, String> localFileDao;
    private Dao<AnidbFile, String> anidbFileDao;
    @SuppressWarnings("unused")
    private Dao<AnidbAnime, String> animeDao;
    @SuppressWarnings("unused")
    private Dao<AnidbEpisode, String> episodeDao;
    @SuppressWarnings("unused")
    private Dao<AnidbTableInfo, String> tableDao;
    
    @SuppressWarnings("unused")
    private TheTvDBPlugin tvdb;
    
    public AniDbPlugin() {
        preferredPlotLength = PropertiesUtil.getIntProperty("plugin.plot.maxlength", "500");
        
        try {
            anidbPort = PropertiesUtil.getIntProperty("anidb.port", "1025");
        } catch (Exception ignore) {
            anidbPort = 1025;
            logger.error(LOG_MESSAGE + "Error setting the port to '" + PropertiesUtil.getProperty("anidb.port") + "' using default");
        }
        
        anidbMask = new AnimeMask(true, true, true, false, false, true, false, true, false, true, true, true, true, true, true, true, true, false, true, true, true, true, false, false, false, false, false, true, true, false, false, false, false, false, true, false, true, true, false, false, false, true, false);
        
        anidbUsername = PropertiesUtil.getProperty("anidb.username", null);
        anidbPassword = PropertiesUtil.getProperty("anidb.password", null);
        //String str = PropertiesUtil.getProperty("anidb.useHashIdentification", null);
        hash = PropertiesUtil.getBooleanProperty("anidb.useHashIdentification", "false");
        
        if (anidbUsername == null || anidbPassword == null) {
            logger.error(LOG_MESSAGE + "You need to add your AniDb Username & password to the anidb.username & anidb.password properties");
            anidbConnectionProtection = true;
        }
        setupDatabase();
        tvdbMappings = new HashMap<Long, AniDbPlugin.AnimeIdMapping>();
        if (getAdditionalInformationFromTheTvDB) {
            loadAniDbTvDbMappings();
            tvdb = new TheTvDBPlugin();
        }
    }
    
    private void setupDatabase()
    {
        String datadir = null;
        /*
         * Not sure how well this will end up working on different versions
         * of Windows.
         * TODO: Implement an override for this in the properties file
         */
        if (System.getProperty("os.name").contains("Windows")) {
            datadir = System.getenv("APPDATA");
            if (datadir == null) {
                datadir = System.getProperty("user.home") + "AppData\\Roaming";
            }
            datadir = datadir.replaceAll("\\\\", "/");
        } else if (System.getProperty("os.name").contains("Mac")) { // http://developer.apple.com/library/mac/#documentation/MacOSX/Conceptual/BPFileSystem/Articles/WhereToPutFiles.html
            datadir = System.getProperty("user.home");
            datadir += "/Library/Application Support";
        } else { // Linux http://standards.freedesktop.org/basedir-spec/basedir-spec-0.6.html
            datadir = System.getenv("XDG_DATA_HOME") == null ? System.getProperty("user.home") + "/.local/share" : System.getenv("XDG_DATA_HOME");
        }
        logger.debug(LOG_MESSAGE + "Determined user data dir to be " + datadir);
        String yamjdir = datadir + "/Yamj";
        File file = new File(yamjdir);
        if(!file.exists()) {
            file.mkdirs();
        }
        logger.debug(LOG_MESSAGE + "Placing database in " + yamjdir);
        String dbUrl = "jdbc:sqlite:" + yamjdir + "/yamj_anidb.db";
        ConnectionSource connectionSource;
        try {
            connectionSource = new JdbcConnectionSource(dbUrl);
            updateTables(connectionSource);
            localFileDao = DaoManager.createDao(connectionSource, AnidbLocalFile.class);
            animeDao = DaoManager.createDao(connectionSource, AnidbAnime.class);
            episodeDao = DaoManager.createDao(connectionSource, AnidbEpisode.class);
            anidbFileDao = DaoManager.createDao(connectionSource, AnidbFile.class);
        } catch (SQLException e) {
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            e.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
    }

    /*
     * Method to check if the database tables are old and need updating, and performs
     * the update if it's needed.
     */
    private static synchronized void updateTables(ConnectionSource connectionSource) {
        try {
            Dao<AnidbTableInfo, String> tableDao = DaoManager.createDao(connectionSource, AnidbTableInfo.class);
            boolean dbUpdate = true;
            AnidbTableInfo info = null;
            int version = -1;
            if (tableDao.isTableExists()) {
                info = tableDao.queryForId(Integer.toString(AniDbPlugin.TABLE_VERSION));
                if (info != null) {
                    dbUpdate = false;
                } else {
                    info = tableDao.queryForAll().get(0);
                    if (info.version == AniDbPlugin.TABLE_VERSION) {
                        dbUpdate = false;
                    } else {
                        version = info.version;
                    }
                }
            }
            if (dbUpdate) {
                switch (version) {
                case -1: // DB Doesn't exist, create from scratch
                    TableUtils.createTable(connectionSource, AnidbLocalFile.class);
                    TableUtils.createTable(connectionSource, AnidbEpisode.class);
                    TableUtils.createTable(connectionSource, AnidbAnime.class);
                    TableUtils.createTable(connectionSource, AnidbTableInfo.class);
                    TableUtils.createTable(connectionSource, AnidbFile.class);
                    info = new AnidbTableInfo();
                    info.version = AniDbPlugin.TABLE_VERSION;
                    tableDao.create(info);
                default:
                    break;
                }
            }
        } catch (SQLException e) {
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            e.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
    }
    
    @Override
    public boolean scan(Movie movie) {
        logger.info(LOG_MESSAGE + "Scanning as a Movie");
        return anidbScan(movie);
    }

    @Override
    public void scanTVShowTitles(Movie movie) {
        logger.info(LOG_MESSAGE + "Scanning as a TV Show");
        anidbScan(movie);
        return;
    }
    
    /**
     * Generic scan routine
     * @param movie
     */
    private boolean anidbScan(Movie movie) {
        // This is required to prevent the client from being banned through overuse
        if (!anidbConnectionProtection) {
            anidbOpen();
        } else {
            logger.info(LOG_MESSAGE + "There was an error with the connection, no more connections will be attempted!");
            return false;
        }
        
        if (anidbConn == null) {
            return false;
        }

        // Now process the movie
        logger.info(LOG_MESSAGE + "Logged in and searching for " + movie.getBaseFilename());
        if (hash) {
            if (!anidbHashScan(movie)) {
                return false;
            }
        } else {
            Matcher titleMatch = Pattern.compile(REGEX_TVSHOW).matcher(movie.getBaseFilename());
            String episode = Movie.UNKNOWN;
            String remainder = Movie.UNKNOWN;
            String crc = Movie.UNKNOWN;
            
            if (titleMatch.find()) {
                // If this matches then this is a TV Show
                logger.info(LOG_MESSAGE + "Matched as a TV Show");
                movie.setMovieType(Movie.TYPE_TVSHOW);
                movie.setSeason(1);
                movie.setTitle(cleanString(titleMatch.group(2)));
                movie.setOriginalTitle(movie.getTitle());
                episode = titleMatch.group(3);
                remainder = titleMatch.group(5);
    
                if (isValidString(remainder)) {
                    Matcher crcMatch = Pattern.compile(CRC_REGEX).matcher(remainder);
                    if (crcMatch.find()) {
                        crc = crcMatch.group(2);
                        remainder = remainder.replace(crc, "");
                    }
                }
            } else {
                logger.info(LOG_MESSAGE + "Assuming a movie");
                titleMatch = Pattern.compile(REGEX_MOVIE).matcher(movie.getBaseFilename());
                if (titleMatch.find()) {
                    // 4 groups: 1=Scene Group, 2=Anime Name, 3=CRC, 4=Remainder
                    movie.setTitle(cleanString(titleMatch.group(2)));
                    movie.setOriginalTitle(movie.getTitle());
                    crc = titleMatch.group(3);
                    remainder = titleMatch.group(4);
                }
                movie.setMovieType(Movie.TYPE_MOVIE);
            }
            
            logger.info("Title  : " + movie.getTitle());    //XXX: DEBUG
            logger.info("Episode: " + (isValidString(episode)? episode : Movie.UNKNOWN));   //XXX: DEBUG
            logger.info("CRC    : " + (isValidString(crc)? crc : Movie.UNKNOWN));   //XXX: DEBUG
            logger.info("Remain : " + (isValidString(remainder)? remainder : Movie.UNKNOWN));   //XXX: DEBUG
        }
        
        Anime anime = null;
        String id = movie.getId(ANIDB_PLUGIN_ID);
        long animeId = 0;
        if (isValidString(id)) {
            animeId = Long.parseLong(id);
        }
        try {
            if (animeId > 0) {
                anime = getAnimeByAid(animeId);
            } else {
                anime = getAnimeByName(movie.getTitle());
                if (anime != null) {
                    animeId = anime.getAnimeId();
                    // Update the movie's Id
                    movie.setId(ANIDB_PLUGIN_ID, "" + animeId);
                }
            }
        } catch (UdpConnectionException error) {
            processUdpError(error);
        } catch (AniDbException error) {
            // We should use the return code here, but it doesn't seem to work
            if (error.getReturnCode() == UdpReturnCodes.NO_SUCH_ANIME || "NO SUCH ANIME".equals(error.getReturnString())) {
                anime = null;
            } else {
                logger.info(LOG_MESSAGE + "Unknown AniDb Exception erorr");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                error.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
        }
        
        if (anime != null) {
            // XXX: DEBUG
            logger.info("getAnimeId         : " + anime.getAnimeId());
            logger.info("getEnglishName     : " + anime.getEnglishName());
            logger.info("getPicname         : " + anime.getPicname());
            logger.info("getType            : " + anime.getType());
            logger.info("getYear            : " + anime.getYear());
            logger.info("getAirDate         : " + anime.getAirDate());
            logger.info("Date               : " + new DateTime(anime.getAirDate()).toString("dd-MM-yyyy"));
            logger.info("getAwardList       : " + anime.getAwardList());
            logger.info("getCategoryList    : " + anime.getCategoryList());
            logger.info("getCharacterIdList : " + anime.getCharacterIdList());
            logger.info("getEndDate         : " + anime.getEndDate());
            logger.info("getEpisodes        : " + anime.getEpisodes());
            logger.info("getProducerNameList: " + anime.getProducerNameList());
            logger.info("getRating          : " + anime.getRating());
            // XXX: DEBUG END
            
            movie.setId(ANIDB_PLUGIN_ID, "" + anime.getAnimeId());
            
            if (tvdbMappings.get(anime.getAnimeId()) != null) {
                movie.setId(TheTvDBPlugin.THETVDB_PLUGIN_ID, Long.toString(tvdbMappings.get(anime.getAnimeId()).tvDbId));
                movie.setSeason(1);
            }
            if (getAdditionalInformationFromTheTvDB) {
                movie.setTitle(anime.getRomajiName());
                tvdb.scan(movie);
            }
            
            if (isValidString(anime.getPicname())) {
                movie.setPosterURL(PICTURE_URL_BASE + anime.getPicname());
            }
            
            if (isValidString(anime.getEnglishName())) {
                movie.setOriginalTitle(anime.getEnglishName());
            } else if (isValidString(anime.getRomajiName())){
                movie.setOriginalTitle(anime.getRomajiName());
            } else {
                logger.error(LOG_MESSAGE + "Encountered an anime without a valid title. Anime ID: " + anime.getAnimeId());  
            }
            
            if (isValidString(anime.getYear())) {
                movie.setYear(new String(anime.getYear().substring(0, 4)));
            }
            
            if (!anime.getCategoryList().isEmpty()) {
                movie.setGenres(anime.getCategoryList());
            }
            
            if (!(anime.getAirDate() == null) && anime.getAirDate() > 0) {
                DateTime rDate = new DateTime(anime.getAirDate());
                movie.setReleaseDate(rDate.toString("yyyy-MM-dd"));
            }

            if (!(anime.getRating() == null) && anime.getRating() > 0) {
                movie.setRating((int) (anime.getRating() / 10));
            }
            
            String plot = HTMLTools.stripTags(getAnimeDescription(animeId));
            // This plot may contain the information on the director and this needs to be stripped from the plot
            logger.info("Plot: " + plot); // XXX: DEBUG
            if (!getAdditionalInformationFromTheTvDB) {
                movie.setPlot(plot);
                movie.setOutline(plot);
            }
            
            for (MovieFile mf : movie.getFiles()) {
                logger.info("File : " + mf.getFilename()); // XXX: DEBUG
                logger.info("First: " + mf.getFirstPart()); // XXX: DEBUG
                logger.info("Last : " + mf.getLastPart()); // XXX: DEBUG
            }
            
        } else {
            logger.info(LOG_MESSAGE + "Anime not found: " + movie.getTitle());
        }
        
        logger.info(LOG_MESSAGE + "Finished " + movie.getBaseFilename());
        return true;
    }
    
    // TODO: Create a separate class to handle all these database queries
    private boolean anidbHashScan(Movie movie) {
        AnidbLocalFile localFile = null;
        try {
            QueryBuilder<AnidbLocalFile, String> qb = localFileDao.queryBuilder();
            qb.where().eq(AnidbLocalFile.SIZE_COLUMN_NAME, movie.getFile().length()).and().eq(AnidbLocalFile.FILENAME_COLUMN_NAME, movie.getFile().getAbsolutePath());
            PreparedQuery<AnidbLocalFile> pq = qb.prepare();
            List<AnidbLocalFile> res = localFileDao.query(pq);
            if (res.size() > 0) {
                localFile = res.get(0);
            }
        } catch (SQLException e) {
            logger.error(LOG_MESSAGE + "An error occured while looking up " + movie.getFile().getAbsolutePath() + " in the cache database");
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            e.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }

        String hash = null;
        AnidbFile file = null;
        if (localFile == null) {
            hash = getEd2kChecksum(movie.getFile());
            if (hash.equals("")) {
                return false;
            }
        } 
        
        try {
            QueryBuilder<AnidbFile, String> qb = anidbFileDao.queryBuilder();
            if (localFile != null) {
                qb.where().eq(AnidbFile.ED2K_COLUMN_NAME, localFile.ed2kHash).and().eq(AnidbFile.SIZE_COLUMN_NAME, localFile.size);
            } else {
                qb.where().eq(AnidbFile.ED2K_COLUMN_NAME, hash).and().eq(AnidbFile.SIZE_COLUMN_NAME, movie.getFile().length());
            }
            PreparedQuery<AnidbFile> pq = qb.prepare();
            List<AnidbFile> res = anidbFileDao.query(pq);
            if (res.size() > 0) {
                file = res.get(0);
            }
        } catch (SQLException e) {
            logger.error(LOG_MESSAGE + "Encountered an SQL error");
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            e.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
        
        if (file == null) {
            localFile = PojoConverter.create(movie.getFile(), hash, localFileDao, logger);
            net.anidb.File file_ = null;
            try {
                file_ = getAnimeEpisodeByHash(movie.getFile().length(), hash);
            } catch (UdpConnectionException error) {
                logger.info(LOG_MESSAGE + "UDP Connection Error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                error.printStackTrace(printWriter);
                logger.error(eResult.toString());
                return false;
            } catch (AniDbException error) {
                logger.info(LOG_MESSAGE + "AniDb Exception Error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                error.printStackTrace(printWriter);
                logger.error(eResult.toString());
                return false;
            }
            if (file_ != null) {
                    file = PojoConverter.create(file_, anidbFileDao, logger);
            }
        } else if (file != null && localFile == null) {
            AnidbLocalFile lf = new AnidbLocalFile();
            lf.ed2kHash = hash;
            lf.size = movie.getFile().length();
            lf.originalFilename = movie.getFile().getAbsolutePath();
            lf.lastSeen = new Date();
            try {
                localFileDao.create(localFile);
            } catch (SQLException e) {
                logger.error(LOG_MESSAGE + "Encountered an SQL error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                e.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
        }
        
        movie.setId(ANIDB_PLUGIN_ID, Long.toString(file.aid));
        
        // Move the below to main scan method instead
        /*if (file.getEpisode().getAnime().getType().equals("Movie")) { // Assume anything not a movie is a TV show
            movie.setMovieType(Movie.TYPE_MOVIE);
        } else {
            movie.setMovieType(Movie.TYPE_TVSHOW);
            for (MovieFile mf : movie.getMovieFiles()) {
                if (mf.getFirstPart() != Integer.parseInt(file.getEpisode().getEpisodeNumber())) {
                    mf.setNewFile(true);
                    mf.setTitle(file.getEpisode().getEnglishTitle());
                    mf.setFirstPart(Integer.parseInt(file.getEpisode().getEpisodeNumber()));
                    mf.setLastPart(Integer.parseInt(file.getEpisode().getEpisodeNumber()));
                }
            }
        }*/
        return true;
    }

    private String getEd2kChecksum(File file) {
        try {
            FileInputStream fi = new FileInputStream(file);
            Ed2kChecksum ed2kChecksum = new Ed2kChecksum();
            byte[] buffer = new byte[ED2K_CHUNK_SIZE];
            int k = -1;
            while ((k = fi.read(buffer, 0, buffer.length)) > 0) {
                ed2kChecksum.update(buffer, 0, k);
            }
            return ed2kChecksum.getHexDigest();
        } catch (FileNotFoundException e) {
            // This shouldn't happen
            logger.error(LOG_MESSAGE + "Unable to find the file " + file.getAbsolutePath());
        } catch (IOException error) {
            logger.error(LOG_MESSAGE + "Encountered an IO-error while reading file " + file.getAbsolutePath());
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
        return "";
    }
    
    /**
     * Search for the Anime description
     * @param animeId
     * @return
     */
    public String getAnimeDescription(long animeId) {

        String animePlot = Movie.UNKNOWN;
        if ((animePlot = (String)Cache.getFromCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "Plot", Long.toString(animeId)))) == null) {
            try {
                animePlot = anidbConn.getAnimeDescription(animeId);
                Cache.addToCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "Plot", Long.toString(animeId)), animePlot);
                logger.debug(LOG_MESSAGE + "Added anime plot to cache for anime id: " + animeId);
            } catch (UdpConnectionException error) {
                processUdpError(error);
            } catch (AniDbException error) {
                processAnidbError(error);
            }
        } else {
            logger.debug(LOG_MESSAGE + "Found anime plot in id cache for anime id: " + animeId);
        }
        return animePlot;
    }
    
    public Anime getAnimeByAid(long animeId) throws UdpConnectionException, AniDbException {
        Anime anime = null;
        if ((anime = (Anime)Cache.getFromCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "AnimeId", Long.toString(animeId)))) != null) {
            logger.debug(LOG_MESSAGE + "Found anime in id cache with id: " + anime.getAnimeId());
            return anime;
        }
        anime = anidbConn.getAnime(animeId, anidbMask);
        Cache.addToCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "AnimeId", Long.toString(animeId)), anime);
        logger.debug(LOG_MESSAGE + "Added anime to cache for anime id: " + anime.getAnimeId());
        return anime;
    }
    
    public Anime getAnimeByName(String animeName) throws UdpConnectionException, AniDbException {
        Anime anime = null;
        if ((anime = (Anime)Cache.getFromCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "AnimeName", animeName))) != null) {
            logger.debug(LOG_MESSAGE + "Found anime in cache for name: " + animeName);
            return anime;
        }
        anime = anidbConn.getAnime(animeName, anidbMask);
        Cache.addToCache(Cache.generateCacheKey(ANIDB_PLUGIN_ID, "AnimeName", animeName), anime);
        logger.debug(LOG_MESSAGE + "Added anime to cache with name: " + anime.getAnimeId());
        return anime;
    }
    
    public net.anidb.File getAnimeEpisodeByHash(long size, String hash) throws UdpConnectionException, AniDbException {
        List<net.anidb.File> results = anidbConn.getFiles(size, hash, FileMask.ALL, AnimeFileMask.ALL);
        return results.get(0); // Unsure how we'd get more than one result here.
    }
    
    /**
     * Get the episode details by Episode ID
     * @param episodeId
     * @return
     * @throws UdpConnectionException
     * @throws AniDbException
     */
    public Episode get(long episodeId) throws UdpConnectionException, AniDbException {
        return anidbConn.getEpisode(episodeId);
    }
    
    /**
     * Get the episode details by AnimeID and Episode Number
     * @param animeId
     * @param episodeNumber
     * @return
     * @throws UdpConnectionException
     * @throws AniDbException
     */
    public Episode getEpisode(long animeId, long episodeNumber) throws UdpConnectionException, AniDbException {
        return anidbConn.getEpisode(animeId, episodeNumber);
    }
    
    /**
     * Get the episode details by Anime Name and Episode Number
     * @param animeName
     * @param episodeNumber
     * @return
     * @throws UdpConnectionException
     * @throws AniDbException
     */
    public Episode getEpisode(String animeName, long episodeNumber) throws UdpConnectionException, AniDbException {
        return anidbConn.getEpisode(animeName, episodeNumber);
    }
    
    public net.anidb.Character getCharacter(long characterId) throws UdpConnectionException, AniDbException {
        return anidbConn.getCharacter(characterId);
    }
    
    /**
     * Output a nice message for the UDP exception
     * @param error
     */
    private void processUdpError(UdpConnectionException error) {
        logger.info(LOG_MESSAGE + "Error: " + error.getMessage());
    }
    
    /**
     * Output a nice message for the AniDb Exception
     * @param error
     */
    private void processAnidbError(AniDbException error) {
        // We should use the return code here, but it doesn't seem to work
        logger.info(LOG_MESSAGE + "Error: " + error.getReturnString());

        int rc = error.getReturnCode();
        String rs = error.getReturnString();
        // Refactor to switch when the getReturnCode() works
        if (rc == UdpReturnCodes.NO_SUCH_ANIME || "NO SUCH ANIME".equals(rs)) {
            logger.info(LOG_MESSAGE + "Anime not found");
        } else if (rc == UdpReturnCodes.NO_SUCH_ANIME_DESCRIPTION || "NO SUCH ANIME DESCRIPTION".equals(rs)) {
            logger.info(LOG_MESSAGE + "Anime description not found");
        } else {
            logger.info(LOG_MESSAGE + "Unknown error occured: " + rc + " - "+ rs);
        }
    }

    @Override
    public void scanNFO(String nfo, Movie movie) {
        logger.debug(LOG_MESSAGE + "Scanning NFO for AniDb Id");
        int beginIndex = nfo.indexOf("aid=");
        if (beginIndex != -1) {
            StringTokenizer st = new StringTokenizer(new String(nfo.substring(beginIndex + 4)), "/ \n,:!&Ã©\"'(--Ã¨_Ã§Ã )=$");
            movie.setId(ANIDB_PLUGIN_ID, st.nextToken());
            logger.debug(LOG_MESSAGE + "AniDb Id found in nfo = " + movie.getId(ANIDB_PLUGIN_ID));
        } else {
            logger.debug(LOG_MESSAGE + "No AniDb Id found in nfo!");
        }
    }
    
    private void loadAniDbTvDbMappings() {
        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
           
            AnidbHandler handler = new AnidbHandler();
                
            saxParser.parse(THETVDB_ANIDB_MAPPING_URL, handler);
            for (AnimeIdMapping m : handler.mappings) {
                // We work on the assumption that multiple anidb ids can map to
                // the same tvdb id, but not the other way around.
                if (tvdbMappings.containsKey(m.aniDbId)) {
                    logger.error(LOG_MESSAGE + "Duplicate anidb ids found while setting up tvdb mappings: " + m.aniDbId + " mapping to " + tvdbMappings.get(m.aniDbId).tvDbId + " and " + m.tvDbId);
                }
                tvdbMappings.put(m.aniDbId, m);
            }
        } catch(SAXParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    
    /**
     * Open the connection to the website
     * @return a connection object, or null if there was a failure.
     */
    public static synchronized void anidbOpen() {
        if (anidbConn != null) {
            // No need to open again
            return;
        }
        
        UdpConnectionFactory factory;
        
        factory = UdpConnectionFactory.getInstance();
        try {
            anidbConn = factory.connect(anidbPort);
            anidbConn.authenticate(anidbUsername, anidbPassword, ANIDB_CLIENT_NAME, ANIDB_CLIENT_VERSION);
            anidbConnectionProtection = false;
        } catch (IllegalArgumentException error) {
            logger.error(LOG_MESSAGE + "Error logging in, please check you username & password");
            logger.error(LOG_MESSAGE + error.getMessage());
            anidbConn = null;
            anidbConnectionProtection = true;
        } catch (UdpConnectionException error) {
            logger.error(LOG_MESSAGE + "Error with UDP Connection, please try again later");
            logger.error(LOG_MESSAGE + error.getMessage());
            anidbConn = null;
            anidbConnectionProtection = true;
        } catch (AniDbException error) {
            logger.error(LOG_MESSAGE + "Error with AniDb: " + error.getMessage());
            anidbConn = null;
            anidbConnectionProtection = true;
        } catch (Exception error) {
            anidbConn = null;
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error(eResult.toString());
        }
        return;
    }

    /**
     * Close the connection to the website
     * @param conn
     */
    public static void anidbClose() {
        anidbLogout(anidbConn);
        // Now close the connection
        try {
            if (anidbConn != null) {
                anidbConn.close();
                logger.info(LOG_MESSAGE + "Logged out and leaving now.");
            }
        } catch (Exception error) {
            error.printStackTrace();    // XXX Debug
        }
    }

    /**
     * Try and log the user out
     * @param conn
     */
    private static void anidbLogout(UdpConnection conn) {
        if (conn == null) {
            return;
        }
        
        // If the user isn't logged in an exception is thrown which we can ignore
        try {
            conn.logout();
        } catch (Exception ignore) {
            // We don't care about this exception
        }
    }
    
    /**
     * Hold information about the AniDb video file
     * @author stuart.boston
     *
     */
    @SuppressWarnings("unused")
    private class AniDbVideo {
        String  originalFilename;   // The unedited filename
        String  sceneGroup;         // The scene group (probably unused)
        String  title;              // The derived title of the video
        int     episodeNumber;      // An episode number (optional)
        String  episodeName;        // The derived episode name
        String  crc;                // CRC number (optional)
        String  otherTags;          // Any other information from the filename, e.g. resolution
        int     anidbID;            // The AniDb ID (from NFO)
    }
    
    // TODO: Here be dragons! Everything below should probably be refactored in time
    
    
    
    /**
     * Hold information about mapping of AnimeId of AniDB and
     * TheTVDB ids for a given show.
     * @author Xaanin
     * 
     */
    
    @SuppressWarnings("unused")
    private class AnimeIdMapping {
        long aniDbId;
        long tvDbId;
        int defaultSeason;
        String name;
        HashMap<String, String> mappings; // Map Anidb season/episode to tvdb season/episode 
        
        public AnimeIdMapping() {
            mappings = new HashMap<String, String>();
        }
        
        public void addMapping(int anidbSeason, int anidbEpisode, int tvdbSeason, int tvdbEpisode) {
            mappings.put("" + anidbSeason + "|" + anidbEpisode, "" + tvdbSeason + "|" + tvdbEpisode);
        }
    }
    /**
     * Parses the xml document containing mappings from anidb id
     * to thetvdb id.
     * @author Xaanin
     *
     */
    private class AnidbHandler extends DefaultHandler{ 
        public List<AnimeIdMapping> mappings = new ArrayList<AnimeIdMapping>();
        AnimeIdMapping current;
        
        boolean name = false;
        boolean mapping = false;
        String lastMapping = "";
        int anidbMappingSeason;
        int tvdbMappingSeason;
        
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (qName.equalsIgnoreCase("anime")) {
                current = new AnimeIdMapping();
                String s = attributes.getValue(attributes.getIndex("tvdbid"));
                if (!s.equalsIgnoreCase("unknown")) {
                    current.aniDbId = Long.parseLong(attributes.getValue(attributes.getIndex("anidbid")));
                    mappings.add(current);
                    current.tvDbId = Long.parseLong(attributes.getValue(attributes.getIndex("tvdbid")));
                    current.defaultSeason = Integer.parseInt(attributes.getValue(attributes.getIndex("defaulttvdbseason")));                    
                }
            } else if (qName.equalsIgnoreCase("name")) {
                name = true;
            } else if (qName.equalsIgnoreCase("mapping")) {
                mapping = true;
            }
        }
        
        public void endElement(String uri, String localName, String qName) {
            logger.info(LOG_MESSAGE + "End of element: " + qName);
            if (qName.equalsIgnoreCase("mapping")) {
                mapping = false;
                String[] split = lastMapping.split(";");
                for(String s : split) {
                    if (s.length() > 0) {
                        String[] res =  s.split("-");
                        logger.info(LOG_MESSAGE + lastMapping + " >> " + s);
                        String[] tvdbres = res[1].split("\\+");   // For certain series such as Bokusatsu Tenshi Dokuro-chan where one 
                                                                // anidb episode maps to two episodes at the tvdb.
                                                                // For now we only use the first one.
                        current.addMapping(anidbMappingSeason, Integer.parseInt(res[0]), tvdbMappingSeason, Integer.parseInt(tvdbres[0]));
                    }
                }
                lastMapping = "";
            } else if (qName.equalsIgnoreCase("name")) {
                name = false;
            }
        }
        
        public void characters(char ch[], int start, int length) {
            if (name) {
                current.name += new String(ch, start, length);
            } else if (mapping) {
                lastMapping += new String(ch, start, length); 
            }
        }
    }
    
    /*
     * Create our database objects from anidb api objects
     */
    private static class PojoConverter {
        
        @SuppressWarnings("unused")
        public static AnidbEpisode create(Episode ep, Dao<AnidbEpisode, String> dao, Logger logger) {
            AnidbEpisode res = new AnidbEpisode();
            res.aid = ep.getAnime().getAnimeId();
            res.aired = new Date(ep.getAired());
            res.eid = ep.getEpisodeId();
            res.englishName = ep.getEnglishTitle();
            res.epno = ep.getEpisodeNumber();
            res.kanjiName = ep.getKanjiTitle();
            res.length = ep.getLength();
            res.rating = ep.getRating();
            res.retrieved = new Date();
            res.romajiName = ep.getRomajiTitle();
            res.votes = ep.getVotes();
            
            try {
                dao.create(res);
            } catch (SQLException e) {
                logger.error(LOG_MESSAGE + "Encountered an SQL error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                e.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
            return res;
        }
        
        public static AnidbLocalFile create(java.io.File file, String ed2kHash, Dao<AnidbLocalFile, String> dao, Logger logger) {
            AnidbLocalFile res = new AnidbLocalFile();
            res.ed2kHash = ed2kHash;
            res.lastSeen = new Date();
            res.originalFilename = file.getAbsolutePath();
            res.size = file.length();
            try {
                dao.create(res);
            } catch (SQLException e) {
                logger.error(LOG_MESSAGE + "Encountered an SQL error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                e.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
            return res;
        }
        
        public static AnidbFile create(net.anidb.File file, Dao<AnidbFile, String> dao, Logger logger) {
            AnidbFile res = new AnidbFile();
            res.aid = file.getEpisode().getAnime().getAnimeId();
            //ret.audioCodecs
            res.CRC32 = file.getCrc32();
            res.ed2k = file.getEd2k();
            res.eid = file.getEpisode().getEpisodeId();
            res.fid = file.getFileId();
            res.gid = file.getGroup().getGroupId();
            res.MD5 = file.getMd5();
            res.retrieved = new Date();
            res.SHA1 = file.getSha1();
            res.size = file.getSize();
            res.videoBitrate = file.getVideoBirate();
            res.videoCodec = file.getVideoCodec();
            res.videoResolution = file.getVideoResolution();
            try {
                dao.create(res);
            } catch (SQLException e) {
                logger.error(LOG_MESSAGE + "Encountered an SQL error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                e.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
            return res;
        }
        
        @SuppressWarnings("unused")
        public static AnidbAnime create(Anime anime, Dao<AnidbAnime, String> dao, Logger logger) {
            AnidbAnime ret = new AnidbAnime();
            ret.aid = anime.getAnimeId();
            ret.englishName = anime.getEnglishName();
            ret.kanjiName = anime.getKanjiName();
            ret.retrieved = new Date();
            ret.romajiName = anime.getRomajiName();
            ret.type = anime.getType();
            ret.year = anime.getYear();
            try {
                dao.create(ret);
            } catch (SQLException e) {
                logger.error(LOG_MESSAGE + "Encountered an SQL error");
                final Writer eResult = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(eResult);
                e.printStackTrace(printWriter);
                logger.error(eResult.toString());
            }
            return ret;
        }
    }
}

/**
 * Holds information about a scanned file on the local system
 */
@DatabaseTable(tableName = "local_files")
class AnidbLocalFile {
    public static final String ID_COLUMN_NAME = "id";
    public static final String FILENAME_COLUMN_NAME = "filename";
    public static final String ED2K_COLUMN_NAME = "ed2khash";
    public static final String SIZE_COLUMN_NAME = "size";
    public static final String LAST_SEEN_COLUMN_NAME = "lastseen";
    
    public AnidbLocalFile() {}
    @DatabaseField(generatedId = true, columnName = ID_COLUMN_NAME)
    int id;
    // We need to store filenames as bytes in order to allow special characters such as '
    @DatabaseField(columnName = FILENAME_COLUMN_NAME, dataType = DataType.STRING_BYTES) 
    String originalFilename;
    @DatabaseField(index = true, columnName = ED2K_COLUMN_NAME)
    String ed2kHash;
    @DatabaseField(index = true, columnName = SIZE_COLUMN_NAME)
    long size;
    @DatabaseField(index = true, columnName = LAST_SEEN_COLUMN_NAME)
    Date lastSeen; // Date this file was last seen, to allow periodic cleanup of stale records
}

/**
 * Hold information about a file from anidb
 * @author Xaanin
 */
@DatabaseTable(tableName = "anidb_file")
class AnidbFile {
    public static final String ED2K_COLUMN_NAME = "ed2k";
    public static final String SIZE_COLUMN_NAME = "size";
    public AnidbFile() {}
    @DatabaseField(id = true)
    long fid;
    @DatabaseField()
    long aid;
    @DatabaseField()
    long eid;
    @DatabaseField()
    long gid;
    @DatabaseField()
    long size;
    @DatabaseField()
    String MD5;
    @DatabaseField()
    String ed2k;
    @DatabaseField()
    String SHA1;
    @DatabaseField()
    String CRC32;
    @DatabaseField()
    String audioCodecs;
    @DatabaseField()
    long videoBitrate;
    @DatabaseField()
    String videoCodec;
    @DatabaseField()
    String videoResolution;
    @DatabaseField(index = true)
    Date retrieved; // We should be able to periodically recheck the anidb information
}
/**
 * Hold information about an episode from anidb
 * @author Xaanin
 */
@DatabaseTable(tableName = "anidb_episode")
class AnidbEpisode {
    public AnidbEpisode() {}
    @DatabaseField(id = true)
    long eid;
    @DatabaseField(index = true)
    long aid;
    @DatabaseField()
    long length;
    @DatabaseField()
    long rating;
    @DatabaseField()
    long votes;
    @DatabaseField()
    String epno; // This can contain letters for things like trailers, specials and such.
    @DatabaseField()
    String englishName;
    @DatabaseField()
    String romajiName;
    @DatabaseField()
    String kanjiName;
    @DatabaseField()
    Date aired;
    @DatabaseField(index = true)
    Date retrieved; // We should be able to periodically recheck the anidb information
}

/**
 * @author Xaanin
 */
@DatabaseTable(tableName = "anidb_anime")
class AnidbAnime {
    @DatabaseField(id = true)
    long aid;
    @DatabaseField()
    String year;
    @DatabaseField()
    String type;
    
    @DatabaseField(index = true)
    String romajiName;
    
    @DatabaseField(index = true)
    String englishName;
    
    @DatabaseField()
    String kanjiName;

    @DatabaseField()
    String description;
    
    @DatabaseField(index = true)
    Date retrieved; // We should be able to periodically recheck the anidb information
    public AnidbAnime() {}
}

/**
 * Class to allow many to many relationships for categories
 * @author Xaanin
 */
@DatabaseTable(tableName = "anidb_anime_categories")
class AnidbAnimeToCategories {
    @DatabaseField(generatedId = true)
    int id;
    
    @DatabaseField(foreign = true)
    AnidbAnime anime;
    
    @DatabaseField(foreign = true)
    AnidbCategory category;
}

@DatabaseTable(tableName = "anidb_category")
class AnidbCategory {
    @DatabaseField(generatedId = true)
    int id;
    
    @DatabaseField()
    String categoryName;
    
    @DatabaseField()
    int CategoryWeight;
}

@DatabaseTable(tableName = "anidb_tableinfo")
class AnidbTableInfo {
    @DatabaseField(id = true)
    int version;
    
    public AnidbTableInfo() {
        
    }
}