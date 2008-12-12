package com.moviejukebox;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import com.moviejukebox.model.Library;
import com.moviejukebox.model.MediaLibraryPath;
import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.DatabasePluginController;
import com.moviejukebox.plugin.DefaultBackgroundPlugin;
import com.moviejukebox.plugin.DefaultPosterPlugin;
import com.moviejukebox.plugin.DefaultThumbnailPlugin;
import com.moviejukebox.plugin.MovieImagePlugin;
import com.moviejukebox.scanner.MediaInfoScanner;
import com.moviejukebox.scanner.MovieDirectoryScanner;
import com.moviejukebox.scanner.MovieNFOScanner;
import com.moviejukebox.scanner.PosterScanner;
import com.moviejukebox.scanner.FanartScanner;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.GraphicTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.writer.MovieJukeboxHTMLWriter;
import com.moviejukebox.writer.MovieJukeboxXMLWriter;
import java.security.PrivilegedAction;

public class MovieJukebox {

    private static Logger logger = Logger.getLogger("moviejukebox");
    Collection<MediaLibraryPath> movieLibraryPaths;
    private String movieLibraryRoot;
    private String jukeboxRoot;
    private String skinHome;
    private String detailsDirName;
    private boolean forceThumbnailOverwrite;
    private boolean forcePosterOverwrite;
    private boolean fanartDownload;

    public static void main(String[] args) throws XMLStreamException, SecurityException, IOException, ClassNotFoundException {
        // Send logger output to our FileHandler.

        Formatter mjbFormatter = new Formatter() {
            public synchronized String format(LogRecord record) {
                return record.getMessage() + (String)java.security.AccessController.doPrivileged(
                    new PrivilegedAction<Object>() {
                        public Object run() {
                            return System.getProperty("line.separator");
                        }
                    });
            }
        };

        FileHandler fh = new FileHandler("moviejukebox.log");
        fh.setFormatter(mjbFormatter);
        fh.setLevel(Level.ALL);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(mjbFormatter);
        ch.setLevel(Level.FINE);

        logger.setUseParentHandlers(false);
        logger.addHandler(fh);
        logger.addHandler(ch);
        logger.setLevel(Level.ALL);

        String movieLibraryRoot = null;
        String jukeboxRoot = null;
        boolean jukeboxClean = false;

        if (args.length == 0) {
            help();
            return;
        }

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = (String) args[i];
                if ("-o".equalsIgnoreCase(arg)) {
                    jukeboxRoot = args[++i];
                } else if ("-c".equalsIgnoreCase(arg)) {
                    jukeboxClean = true;
                } else if (arg.startsWith("-")) {
                    help();
                    return;
                } else {
                    movieLibraryRoot = args[i];

                    File f = new File(movieLibraryRoot);
                    if (f.exists() && f.isDirectory() && jukeboxRoot == null) {
                        jukeboxRoot = movieLibraryRoot;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Wrong arguments specified");
            help();
            return;
        }

        if (movieLibraryRoot == null) {
            help();
            return;
        }

        if (jukeboxRoot == null) {
            System.out.println("Wrong arguments specified: you must define the jukeboxRoot property (-o) !");
            help();
            return;
        }

        if (!new File(movieLibraryRoot).exists()) {
            System.err.println("Directory not found : " + movieLibraryRoot);
            return;
        }

        MovieJukebox ml = new MovieJukebox(movieLibraryRoot, jukeboxRoot);
        ml.generateLibrary(jukeboxClean);
    }

    private static void help() {
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("");
        System.out.println("Generates an HTML library for your movies library.");
        System.out.println("");
        System.out.println("MOVIELIB movieLibraryRoot [-o jukeboxRoot]");
        System.out.println("");
        System.out.println("    movieLibraryRoot    : MANDATORY");
        System.out.println("                          This parameter can be either: ");
        System.out.println("                          - An existing directory (local or network)");
        System.out.println("                            This is where your movie files are stored.");
        System.out.println("                            In this case -o is optional.");
        System.out.println("");
        System.out.println("                          - Or an XML configuration file specifying one or");
        System.out.println("                            many directories to be scanned for movies.");
        System.out.println("                            In this case -o option is MANDATORY.");
        System.out.println("                            Please check README.TXT for further information.");
        System.out.println("");
        System.out.println("    -o jukeboxRoot      : OPTIONAL (when not using XML libraries file)");
        System.out.println("                          output directory (local or network directory)");
        System.out.println("                          This is where the jukebox file will be written to");
        System.out.println("                          by default the is the same as the movieLibraryRoot");
        System.out.println("");
        System.out.println("    -c                  : OPTIONAL");
        System.out.println("                          Clean the jukebox directory after running.");
        System.out.println("                          This will delete any unused files from the jukebox");
        System.out.println("                          directory at the end of the run.");
    }

    private MovieJukebox(String source, String jukeboxRoot) {
        this.movieLibraryRoot = source;
        this.jukeboxRoot = jukeboxRoot;
        this.detailsDirName = PropertiesUtil.getProperty("mjb.detailsDirName", "Jukebox");
        this.forceThumbnailOverwrite = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.forceThumbnailsOverwrite", "false"));
        this.forcePosterOverwrite = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.forcePostersOverwrite", "false"));
        this.skinHome = PropertiesUtil.getProperty("mjb.skin.dir", "./skins/default");
        this.fanartDownload = Boolean.parseBoolean(PropertiesUtil.getProperty("moviedb.fanart.download", "false"));

        File f = new File(source);
        if (f.exists() && f.isFile() && source.toUpperCase().endsWith("XML")) {
            logger.finest("Parsing library file : " + source);
            movieLibraryPaths = parseMovieLibraryRootFile(f);
        } else if (f.exists() && f.isDirectory()) {
            logger.finest("Library path is : " + source);
            movieLibraryPaths = new ArrayList<MediaLibraryPath>();
            MediaLibraryPath mlp = new MediaLibraryPath();
            mlp.setPath(source);
            mlp.setNmtRootPath(PropertiesUtil.getProperty("mjb.nmtRootPath", "file:///opt/sybhttpd/localhost.drives/HARD_DISK/Video/"));
            mlp.setExcludes(new ArrayList<String>());
            movieLibraryPaths.add(mlp);
        }
    }

    private void generateLibrary(boolean jukeboxClean) throws FileNotFoundException, XMLStreamException, ClassNotFoundException {
        MovieJukeboxXMLWriter xmlWriter = new MovieJukeboxXMLWriter();
        MovieJukeboxHTMLWriter htmlWriter = new MovieJukeboxHTMLWriter();

        MovieImagePlugin thumbnailPlugin = this.getThumbnailPlugin(PropertiesUtil.getProperty("mjb.thumbnail.plugin", "com.moviejukebox.plugin.DefaultThumbnailPlugin"));
        MovieImagePlugin posterPlugin = this.getPosterPlugin(PropertiesUtil.getProperty("mjb.poster.plugin", "com.moviejukebox.plugin.DefaultPosterPlugin"));
        MovieImagePlugin backgroundPlugin = this.getBackgroundPlugin(PropertiesUtil.getProperty("mjb.background.plugin", "com.moviejukebox.plugin.DefaultBackgroundPlugin"));

        MovieDirectoryScanner mds = new MovieDirectoryScanner();
        MovieNFOScanner nfoScanner = new MovieNFOScanner();
        MediaInfoScanner miScanner = new MediaInfoScanner();
        PosterScanner posterScanner = new PosterScanner();
        FanartScanner fanartScanner = new FanartScanner();

        File mediaLibraryRoot = new File(movieLibraryRoot);
        String jukeboxDetailsRoot = jukeboxRoot + File.separator + detailsDirName;
        
        int nbFiles = 0;
        String cleanCurrent  = "";

        //////////////////////////////////////////////////////////////////
        /// PASS 0 : Preparing temporary environnement...
        //
        
        File tempJukeboxCleanFile = new File(jukeboxDetailsRoot);
        if ( jukeboxClean && tempJukeboxCleanFile.exists()) {
            // Clear out the jukebox generated files to force them to be re-created.
            
            File[] cleanList = tempJukeboxCleanFile.listFiles();
            
            for (nbFiles = 0; nbFiles < cleanList.length; nbFiles++) {
                // Scan each file in here
                if ( cleanList[nbFiles].isFile() ) {
                    cleanCurrent = cleanList[nbFiles].getName().toUpperCase();
                    cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf("."));
                    
                    if ( cleanCurrent.equals("CATEGORIES") ) {
                        cleanList[nbFiles].delete();
                    } else if ( (cleanList[nbFiles].getName().substring(cleanList[nbFiles].getName().lastIndexOf(".")).equals(".css")) ||
                                (cleanCurrent.indexOf("GENRES_") >= 0) || 
                                (cleanCurrent.indexOf("OTHER_")  >= 0) ||
                                (cleanCurrent.indexOf("RATING_") >= 0) ||
                                (cleanCurrent.indexOf("TITLE_")  >= 0) ) {
                        cleanList[nbFiles].delete();
                    }
                }
            }
        }
        
        logger.fine("Initializing...");
        String tempJukeboxRoot = "./temp";
        String tempJukeboxDetailsRoot = tempJukeboxRoot + File.separator + detailsDirName;

        File tempJukeboxDetailsRootFile = new File(tempJukeboxDetailsRoot);
        if (tempJukeboxDetailsRootFile.exists()) {
            //Clean up
            File[] isoList = tempJukeboxDetailsRootFile.listFiles();
            for (nbFiles = 0; nbFiles < isoList.length; nbFiles++) {
                isoList[nbFiles].delete();
            }
            tempJukeboxDetailsRootFile.delete();
        }
        tempJukeboxDetailsRootFile.mkdirs();


        //////////////////////////////////////////////////////////////////
        /// PASS 1 : Scan movie libraries for files...
        //
        logger.fine("Scanning movies directory " + mediaLibraryRoot);
        logger.fine("Jukebox output goes to " + jukeboxRoot);

        Library library = new Library();
        for (MediaLibraryPath mediaLibraryPath : movieLibraryPaths) {
            logger.finer("Scanning media library " + mediaLibraryPath.getPath());
            library = mds.scan(mediaLibraryPath, library);
        }

        logger.fine("Found " + library.size() + " movies in your media library");

        //////////////////////////////////////////////////////////////////
        /// PASS 2 : Scan movie libraries for files...
        //
        logger.fine("Searching for movies information...");

        for (Movie movie : library.values()) {
            // First get movie data (title, year, director, genre, etc...)
            logger.fine("Updating data for: " + movie.getTitle());
            updateMovieData(xmlWriter, nfoScanner, miScanner, jukeboxDetailsRoot, movie);

            // Look for local file poster (MUST BE DONE BEFORE updateMoviePoster)
            posterScanner.scan(jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);

            // Then get this movie's poster
            logger.finer("Updating poster for: " + movie.getTitle() + "...");
            updateMoviePoster(jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);

            // Get Fanart if requested
            if (fanartDownload) {
                // Look for local fanart (MUST BE DONE BEFORE updateMovieFanart)
                fanartScanner.scan(backgroundPlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);

                logger.finer("Updating fanart for: " + movie.getTitle() + "...");
                updateMovieFanart(backgroundPlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);
            }
        }


        //////////////////////////////////////////////////////////////////
        /// PASS 3 : Indexing the library
        //
        logger.fine("Indexing libraries...");
        library.buildIndex();

        for (Movie movie : library.values()) {
            // Update movie XML files with computed index information
            logger.finest("Writing index data to movie: " + movie.getBaseName());
            xmlWriter.writeMovieXML(jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);

            // Create a detail poster for each movie
            logger.finest("Creating detail poster for movie: " + movie.getBaseName());
            createPoster(posterPlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, skinHome, movie, forcePosterOverwrite);

            // Create a thumbnail for each movie
            logger.finest("Creating thumbnails for movie: " + movie.getBaseName());
            createThumbnail(thumbnailPlugin, jukeboxDetailsRoot, tempJukeboxDetailsRoot, skinHome, movie, forceThumbnailOverwrite);

            // write the movie details HTML		
            htmlWriter.generateMovieDetailsHTML(jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);

            // write the playlist for the movie if needed
            htmlWriter.generatePlaylist(jukeboxDetailsRoot, tempJukeboxDetailsRoot, movie);
        }

        logger.fine("Generating Indexes...");
        xmlWriter.writeIndexXML(tempJukeboxDetailsRoot, detailsDirName, library);
        xmlWriter.writeCategoryXML(tempJukeboxRoot, detailsDirName, library);
        htmlWriter.generateMoviesIndexHTML(tempJukeboxRoot, detailsDirName, library);
        htmlWriter.generateMoviesCategoryHTML(tempJukeboxRoot, detailsDirName, library);

        logger.fine("Copying new files to Jukebox directory...");
        FileTools.copyDir(tempJukeboxDetailsRoot, jukeboxDetailsRoot);
        FileTools.copyFile(new File(tempJukeboxRoot + File.separator + "index.htm"), new File(jukeboxRoot + File.separator + "index.htm"));

        logger.fine("Copying resources to Jukebox directory...");
        FileTools.copyDir(skinHome + File.separator + "html", jukeboxDetailsRoot);

        logger.fine("Clean up temporary files");
        File[] isoList = tempJukeboxDetailsRootFile.listFiles();
        for (nbFiles = 0; nbFiles < isoList.length; nbFiles++) {
            isoList[nbFiles].delete();
        }
        tempJukeboxDetailsRootFile.delete();
        File rootIndex = new File(tempJukeboxRoot + File.separator + "index.htm");
        rootIndex.delete();

        //////////////////////////////////////////////////////////////////
        /// PASS 4: Clean-up the jukebox directory 
        /// If the command line argument "-c" was passed
        //
        if ( jukeboxClean ) {
            logger.fine("Cleaning up the jukebox directory...");

//            File tempJukeboxCleanFile = new File(jukeboxDetailsRoot);
            File[] cleanList = tempJukeboxCleanFile.listFiles();
            int cleanDeletedTotal = 0;
            
            for (nbFiles = 0; nbFiles < cleanList.length; nbFiles++) {
                // Scan each file in here
                if ( cleanList[nbFiles].isFile() ) {
                    cleanCurrent = cleanList[nbFiles].getName().toUpperCase();
                    cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf("."));
                    
                    if ( cleanCurrent.equals("CATEGORIES") ) {
                        //logger.fine(cleanCurrent + " ignored");
                    } else if ( (cleanList[nbFiles].getName().substring(cleanList[nbFiles].getName().lastIndexOf(".")).equals(".css")) ||
                                (cleanCurrent.indexOf("GENRES_") >= 0) || 
                                (cleanCurrent.indexOf("OTHER_")  >= 0) ||
                                (cleanCurrent.indexOf("RATING_") >= 0) ||
                                (cleanCurrent.indexOf("TITLE_")  >= 0) ) {
                        //logger.fine(cleanCurrent + " ignored");
                    } else {
                        // Left with just the generated movie files in the directory now.
                        // We should now check to see if they are in the current movie list
                        // If they are not in this list, then we will delete them.
                        
                        if ( cleanCurrent.lastIndexOf(".PLAYLIST") > 0 ) {
                            cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf(".PLAYLIST"));
                        } else if ( cleanCurrent.lastIndexOf("_LARGE") > 0 ) {
                            cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf("_LARGE"));
                        } else if ( cleanCurrent.lastIndexOf("_SMALL") > 0 ) {
                            cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf("_SMALL"));
                        } else if ( cleanCurrent.lastIndexOf("FANART") > 0 ) {
                            cleanCurrent = cleanCurrent.substring(0, cleanCurrent.lastIndexOf("FANART")-1);
                        }
                        
                        if ( !searchLibrary(cleanCurrent, library) ) {
                            logger.finest("Deleted: " + cleanList[nbFiles].getName() + " from library");
                            cleanDeletedTotal++;
                            cleanList[nbFiles].delete();
                        }
                    }
                }
            }
            logger.fine(Integer.toString(nbFiles) + " files in the jukebox directory");
            logger.fine("Deleted " + Integer.toString(cleanDeletedTotal) + " files");
        } else {
            logger.fine("Jukebox cleaning skipped");
        }
        logger.fine("Process terminated.");
    }

/**
  * Search the movie library for the passed movie name
  * @param slMovieName - the name of the movie to match
  * @param library - the library to search
  * @return true if found, false if not.
  */
    private Boolean searchLibrary(String slMovieName, Library library) {
        slMovieName = slMovieName.toUpperCase();
        for (Movie movie : library.values()) {
            if ( movie.getBaseName().toUpperCase().equals(slMovieName) ) {
                return true;
            }
        }
        return false;
    }
    
/**
  * Generates a movie XML file which contains data in the <tt>Movie</tt> bean.
  * 
  * When an XML file exists for the specified movie file, it is loaded into the 
  * specified <tt>Movie</tt> object.
  * 
  * When no XML file exist, scanners are called in turn, in order to add information
  * to the specified <tt>movie</tt> object. Once scanned, the <tt>movie</tt> object
  * is persisted.
  */
    private void updateMovieData(MovieJukeboxXMLWriter xmlWriter,
            MovieNFOScanner nfoScanner, MediaInfoScanner miScanner,
            String jukeboxDetailsRoot, Movie movie) throws FileNotFoundException, XMLStreamException {

        boolean forceXMLOverwrite = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.forceXMLOverwrite", "false"));
        boolean checkNewer = Boolean.parseBoolean(PropertiesUtil.getProperty("filename.nfo.checknewer", "true"));

        // For each movie in the library, if an XML file for this
        // movie already exist, then no need to search for movie
        // information, just parse the XML data.
        File xmlFile = new File(jukeboxDetailsRoot + File.separator + movie.getBaseName() + ".xml");
        
        // See if we can find the NFO associated with this video file.
        File nfoFile = new File(nfoScanner.locateNFO(movie));
        
        if (xmlFile.exists() && nfoFile.exists() && checkNewer) {
            // check the dates to see if the nfo file is newer
            if (xmlFile.lastModified() < nfoFile.lastModified()) {
                logger.fine("Detected that NFO is newer. Rescanning file.");
                forceXMLOverwrite = true;
            }
        }

        if (xmlFile.exists() && !forceXMLOverwrite) {
            // parse the movie XML file
            logger.finer("movie XML file found for movie:" + movie.getBaseName());
            xmlWriter.parseMovieXML(xmlFile, movie);

            // update new episodes titles if new MovieFiles were added
            DatabasePluginController.scanTVShowTitles(movie);

            // Update thumbnails format if needed
            String thumbnailExtension = PropertiesUtil.getProperty("thumbnails.format", "png");
            movie.setThumbnailFilename(movie.getBaseName() + "_small." + thumbnailExtension);
            // Update poster format if needed
            String posterExtension = PropertiesUtil.getProperty("posters.format", "png");
            movie.setDetailPosterFilename(movie.getBaseName() + "_large." + posterExtension);

        } else {

            // No XML file for this movie. We've got to find movie
            // information where we can (filename, IMDb, NFO, etc...)
            // Add here extra scanners if needed.
            if ( forceXMLOverwrite ) {
                logger.finer("Rescanning Internet Data for file " + movie.getBaseName());
            } else {
                logger.finer("Movie XML file not found. Scanning Internet Data for file " + movie.getBaseName());
            }
            nfoScanner.scan(movie);
            DatabasePluginController.scan(movie);
            miScanner.scan(movie);
        }
    }

    /**
     * Update the movie poster for the specified movie.
     * 
     * When an existing thumbnail is found for the movie, it is not overwriten,
     * unless the mjb.forceThumbnailOverwrite is set to true in the property file.
     * 
     * When the specified movie does not contain a valid URL for the poster, a 
     * dummy image is used instead.
     * @param tempJukeboxDetailsRoot 
     */
    private void updateMoviePoster(String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        String posterFilename = jukeboxDetailsRoot + File.separator + movie.getPosterFilename();
        File posterFile = new File(posterFilename);
        String tmpDestFileName = tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename();
        File tmpDestFile = new File(tmpDestFileName);

        // Do not overwrite existing posters, unless there is a new poster URL in the nfo file.
        if ((!tmpDestFile.exists() && !posterFile.exists()) || (movie.isDirtyPoster()) ) {
            posterFile.getParentFile().mkdirs();

            if (movie.getPosterURL() == null || movie.getPosterURL().equalsIgnoreCase("Unknown")) {
                logger.finest("Dummy image used for " + movie.getBaseName());
                FileTools.copyFile(
                        new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                        new File(tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename()));
            } else {
                try {
                    // Issue 201 : we now download to local temp dir
                    logger.finest("Downloading poster for " + movie.getBaseName() + " to " + tmpDestFileName + " [calling plugin]");
                    downloadImage(tmpDestFile, movie.getPosterURL());
                } catch (Exception e) {
                    logger.finer("Failed downloading movie poster : " + movie.getPosterURL());
                    FileTools.copyFile(
                            new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                            new File(tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename()));
                }
            }
        }
    }

    /**
     * Update the Fanart for the specified movie.
     * 
     * When existing fanart is found for the movie, it is not overwriten,
     * unless the mjb.forceFanartOverwrite is set to true in the property file.
     * 
     * When the specified movie does not contain a valid URL for the poster, a 
     * dummy image is used instead.
     * @param tempJukeboxDetailsRoot 
     */
    private void updateMovieFanart(MovieImagePlugin backgroundPlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        String posterFilename = jukeboxDetailsRoot + File.separator + movie.getPosterFilename();
        File posterFile = new File(posterFilename);
        String tmpDestFileName = tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename();
        File tmpDestFile = new File(tmpDestFileName);

        // Do not overwrite existing posters, unless there is a new poster URL in the nfo file.
        if ((!tmpDestFile.exists() && !posterFile.exists()) || (movie.isDirtyPoster()) ) {
            posterFile.getParentFile().mkdirs();

            if (movie.getPosterURL() == null || movie.getPosterURL().equalsIgnoreCase("Unknown")) {
                logger.finest("Dummy image used for " + movie.getBaseName());
                FileTools.copyFile(
                        new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                        new File(tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename()));
            } else {
                try {
                    // Issue 201 : we now download to local temp dir
                    logger.finest("Downloading poster for " + movie.getBaseName() + " to " + tmpDestFileName + " [calling plugin]");
                    downloadImage(tmpDestFile, movie.getPosterURL());
                } catch (Exception e) {
                    logger.finer("Failed downloading movie poster : " + movie.getPosterURL());
                    FileTools.copyFile(
                            new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                            new File(tempJukeboxDetailsRoot + File.separator + movie.getPosterFilename()));
                }
            }
        }
    }

    // Not used anymore
    /*
    private void updateFanart(MovieImagePlugin backgroundPlugin, String jukeboxDetailsRoot, String tempJukeboxDetailsRoot, Movie movie) {
        if (movie.getFanartURL() != null && !movie.getFanartURL().equalsIgnoreCase(Movie.UNKNOWN)) {
            String fanartFilename = jukeboxDetailsRoot + File.separator + movie.getFanartFilename();
            File fanartFile = new File(fanartFilename);
            String tmpDestFileName = tempJukeboxDetailsRoot + File.separator + movie.getFanartFilename();
            File tmpDestFile = new File(tmpDestFileName);

            // Do not overwrite existing fanart
            if (!fanartFile.exists() && !tmpDestFile.exists()) {
                fanartFile.getParentFile().mkdirs();

                try {
                    logger.finest("Downloading fanart for " + movie.getBaseName() + " to " + tmpDestFileName + " [calling plugin]");

                    BufferedImage fanartImage = GraphicTools.loadJPEGImage(movie.getFanartURL());

                    if (fanartImage != null) {
                        fanartImage = backgroundPlugin.generate(movie, fanartImage);
                        GraphicTools.saveImageToDisk(fanartImage, tmpDestFileName);
                    } else {
                        movie.setFanartFilename(Movie.UNKNOWN);
                        movie.setFanartURL(Movie.UNKNOWN);
                    }
                } catch (Exception e) {
                    logger.finer("Failed downloading fanart : " + movie.getFanartURL());
                }
            }
        }
    }
    */
    
    @SuppressWarnings("unchecked")
    private Collection<MediaLibraryPath> parseMovieLibraryRootFile(File f) {
        Collection<MediaLibraryPath> mlp = new ArrayList<MediaLibraryPath>();

        if (!f.exists() || f.isDirectory()) {
            logger.severe("The moviejukebox library input file you specified is invalid: " + f.getName());
            return mlp;
        }

        try {
            XMLConfiguration c = new XMLConfiguration(f);

            List<HierarchicalConfiguration> fields = c.configurationsAt("library");
            for (Iterator<HierarchicalConfiguration> it = fields.iterator(); it.hasNext();) {
                HierarchicalConfiguration sub = it.next();
                // sub contains now all data about a single medialibrary node
                String path = sub.getString("path");
                String nmtpath = sub.getString("nmtpath");

                // Check that the nmtpath terminates with a "/" or "\"
                if ( !(nmtpath.endsWith("/") || nmtpath.endsWith("\\")) ) {
                    // This is the NMTPATH so add the unix path separator rather than File.separator
                    nmtpath = nmtpath + "/";
                }

                List<String> excludes = sub.getList("exclude[@name]");

                if (new File(path).exists()) {
                    MediaLibraryPath medlib = new MediaLibraryPath();
                    medlib.setPath(path);
                    medlib.setNmtRootPath(nmtpath);
                    medlib.setExcludes(excludes);
                    mlp.add(medlib);
                    logger.fine("Found media library: " + medlib);
                } else {
                    logger.fine("Skipped invalid media library: " + path);
                }
            }
        } catch (Exception e) {
            logger.severe("Failed parsing moviejukebox library input file: " + f.getName());
            e.printStackTrace();
        }
        return mlp;
    }

    public MovieImagePlugin getThumbnailPlugin(String className) {
        MovieImagePlugin thumbnailPlugin;

        try {
            Thread t = Thread.currentThread();
            ClassLoader cl = t.getContextClassLoader();
            Class<? extends DefaultThumbnailPlugin> pluginClass = cl.loadClass(className).asSubclass(DefaultThumbnailPlugin.class);
            thumbnailPlugin = pluginClass.newInstance();
        } catch (Exception e) {
            thumbnailPlugin = new DefaultThumbnailPlugin();
            logger.severe("Failed instanciating ThumbnailPlugin: " + className);
            logger.severe("Default thumbnail plugin will be used instead.");
            e.printStackTrace();
        }

        return thumbnailPlugin;
    }

    public MovieImagePlugin getPosterPlugin(String className) {
        MovieImagePlugin posterPlugin;

        try {
            Thread t = Thread.currentThread();
            ClassLoader cl = t.getContextClassLoader();
            Class<? extends MovieImagePlugin> pluginClass = cl.loadClass(className).asSubclass(MovieImagePlugin.class);
            posterPlugin = pluginClass.newInstance();
        } catch (Exception e) {
            posterPlugin = new DefaultPosterPlugin();
            logger.severe("Failed instanciating PosterPlugin: " + className);
            logger.severe("Default poster plugin will be used instead.");
            e.printStackTrace();
        }

        return posterPlugin;
    }

//* Not used for the time being
/*
    public MovieImagePlugin getFanartPlugin(String className) {
        MovieImagePlugin fanartPlugin;

        try {
            Thread t = Thread.currentThread();
            ClassLoader cl = t.getContextClassLoader();
            Class<? extends MovieImagePlugin> pluginClass = cl.loadClass(className).asSubclass(MovieImagePlugin.class);
            fanartPlugin = pluginClass.newInstance();
        } catch (Exception e) {
            fanartPlugin = new DefaultFanartPlugin();
            logger.severe("Failed instanciating FanartPlugin: " + className);
            logger.severe("Default fanart plugin will be used instead.");
            e.printStackTrace();
        }

        return fanartPlugin;
    }
*/
    public MovieImagePlugin getBackgroundPlugin(String className) {
        MovieImagePlugin backgroundPlugin;

        try {
            Thread t = Thread.currentThread();
            ClassLoader cl = t.getContextClassLoader();
            Class<? extends MovieImagePlugin> pluginClass = cl.loadClass(className).asSubclass(MovieImagePlugin.class);
            backgroundPlugin = pluginClass.newInstance();
        } catch (Exception e) {
            backgroundPlugin = new DefaultBackgroundPlugin();
            logger.severe("Failed instanciating BackgroundPlugin: " + className);
            logger.severe("Default background plugin will be used instead.");
            e.printStackTrace();
        }

        return backgroundPlugin;
    }

    /**
     * Download the image for the specified url into the specified file.
     * @throws IOException
     */
    public static void downloadImage(File imageFile, String imageURL) throws IOException {
        URL url = new URL(imageURL);
        URLConnection cnx = url.openConnection();

        // Let's pretend we're Firefox...
        cnx.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; U; Linux x86_64; en-GB; rv:1.8.1.5) Gecko/20070719 Iceweasel/2.0.0.5 (Debian-2.0.0.5-0etch1)");

        FileTools.copy(cnx.getInputStream(), new FileOutputStream(imageFile));
    }

    public static void createThumbnail(MovieImagePlugin thumbnailManager, String rootPath, String tempRootPath, String skinHome, Movie movie, boolean forceThumbnailOverwrite) {
        try {
            // Issue 201 : we now download to local temp dire
            String src    = tempRootPath + File.separator + movie.getPosterFilename();
            String oldsrc = rootPath     + File.separator + movie.getPosterFilename();
            String dst    = tempRootPath + File.separator + movie.getThumbnailFilename();
            String olddst = rootPath     + File.separator + movie.getThumbnailFilename();
            FileInputStream fis;
            
            if (!(new File(olddst).exists()) || forceThumbnailOverwrite || (new File(src).exists())) {
                // Issue 228: If the PNG files are deleted before running the jukebox this fails. Therefor check to see if they exist in the original directory
                if (new File(src).exists()) {
                    logger.finest("New file exists");
                    fis = new FileInputStream(src);
                } else {
                    logger.finest("Use old file");
                    fis = new FileInputStream(oldsrc);
                }

                BufferedImage bi = GraphicTools.loadJPEGImage(fis);
                if (bi == null) {
                    logger.info("Using dummy thumbnail image for " + movie.getTitle());
                    FileTools.copyFile(
                            new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                            new File(rootPath + File.separator + movie.getPosterFilename()));
                    fis = new FileInputStream(src);
                    bi = GraphicTools.loadJPEGImage(fis);
                }

                bi = thumbnailManager.generate(movie, bi);

                logger.finest("Generating thumbnail from " + src + " to " + dst);
                GraphicTools.saveImageToDisk(bi, dst);
            }
        } catch (Exception e) {
            logger.severe("Failed creating thumbnail for " + movie.getTitle());
            e.printStackTrace();
        }
    }

    public static void createPoster(MovieImagePlugin posterManager, String rootPath, String tempRootPath, String skinHome, Movie movie, boolean forcePosterOverwrite) {
        try {
            // Issue 201 : we now download to local temp dire
            String src    = tempRootPath + File.separator + movie.getPosterFilename();
            String oldsrc = rootPath     + File.separator + movie.getPosterFilename();
            String dst    = tempRootPath + File.separator + movie.getDetailPosterFilename();
            String olddst = rootPath     + File.separator + movie.getDetailPosterFilename();
            FileInputStream fis;

            if (!(new File(olddst).exists()) || forcePosterOverwrite || (new File(src).exists())) {
                // Issue 228: If the PNG files are deleted before running the jukebox this fails. Therefor check to see if they exist in the original directory
                if (new File(src).exists()) {
                    logger.finest("New file exists");
                    fis = new FileInputStream(src);
                } else {
                    logger.finest("Use old file");
                    fis = new FileInputStream(oldsrc);
                }

                BufferedImage bi = GraphicTools.loadJPEGImage(fis);
                if (bi == null) {
                    logger.info("Using dummy poster image for " + movie.getTitle());
                    FileTools.copyFile(
                            new File(skinHome + File.separator + "resources" + File.separator + "dummy.jpg"),
                            new File(rootPath + File.separator + movie.getPosterFilename()));
                    fis = new FileInputStream(src);
                    bi = GraphicTools.loadJPEGImage(fis);
                }
                logger.finest("Generating poster from " + src + " to " + dst);
                bi = posterManager.generate(movie, bi);

                GraphicTools.saveImageToDisk(bi, dst);
            }
        } catch (Exception e) {
            logger.severe("Failed creating poster for " + movie.getTitle());
            e.printStackTrace();
        }
    }
}
