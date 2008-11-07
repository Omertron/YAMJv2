package com.moviejukebox.scanner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.TrailerFile;
import com.moviejukebox.plugin.ImdbPlugin;
import com.moviejukebox.plugin.MovieDatabasePlugin;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import java.io.StringReader;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.XMLEvent;

/**
 * NFO file parser.
 * 
 * Search a NFO file for IMDb URL.
 * 
 * @author jjulien
 */
public class MovieNFOScanner {

    private static Logger logger = Logger.getLogger("moviejukebox");
    static final int BUFF_SIZE = 100000;
    static final byte[] buffer = new byte[BUFF_SIZE];

    /**
     * Search the IMDBb id of the specified movie in the NFO file if it exists.
     * 
     * @param movie
     * @param movieDB
     */
    public void scan(Movie movie, MovieDatabasePlugin movieDB) {
        String fn = movie.getFile().getAbsolutePath();
//		String localMovieName = movie.getTitle();
        String localMovieDir = fn.substring(0, fn.lastIndexOf(File.separator));	// the full directory that the video file is in
        String localDirectoryName = localMovieDir.substring(localMovieDir.lastIndexOf(File.separator) + 1);	// just the sub-directory the video file is in
        String checkedFN = "";
        String NFOdirectory = PropertiesUtil.getProperty("filename.nfo.directory", "");

        // If "fn" is a file then strip the extension from the file.
        if (movie.getFile().isFile()) {
            fn = fn.substring(0, fn.lastIndexOf("."));
        } else {
            // *** First step is to check for VIDEO_TS
            // The movie is a directory, which indicates that this is a VIDEO_TS file
            // So, we should search for the file moviename.nfo in the sub-directory
            checkedFN = checkNFO(fn + fn.substring(fn.lastIndexOf(File.separator)));
        }

        if (checkedFN.equals("")) {
            // Not a VIDEO_TS directory so search for the variations on the filename.nfo
            // *** Second step is to check for a directory wide NFO file.
            // This file should be named the same as the directory that it is in
            // E.G. C:\TV\Chuck\Season 1\Season 1.nfo
            checkedFN = checkNFO(localMovieDir + File.separator + localDirectoryName);

            if (checkedFN.equals("")) {
                // *** Third step is to check for the filename.nfo dile
                // This file should be named exactly the same as the video file with an extension of "nfo" or "NFO"
                // E.G. C:\Movies\Bladerunner.720p.avi => Bladerunner.720p.nfo
                checkedFN = checkNFO(fn);
            }

            if (checkedFN.equals("") && !NFOdirectory.equals("")) {
                // *** Last step if we still haven't found the nfo file is to
                // search the NFO directory as specified in the moviejukebox,properties file
                String sLibraryPath = movie.getLibraryPath();
                if ((sLibraryPath.lastIndexOf("\\") == sLibraryPath.length()) || (sLibraryPath.lastIndexOf("/") == sLibraryPath.length())) {
                    checkedFN = checkNFO(movie.getLibraryPath() + NFOdirectory + File.separator + movie.getBaseName());
                } else {
                    checkedFN = checkNFO(movie.getLibraryPath() + File.separator + NFOdirectory + File.separator + movie.getBaseName());
                }
            }
        }

        File nfoFile = new File(checkedFN);

        if (nfoFile.exists()) {
            logger.finest("Scanning NFO file for Infos : " + nfoFile.getName());
            InputStream in = null;
            ByteArrayOutputStream out = null;
            try {
                in = new FileInputStream(nfoFile);
                out = new ByteArrayOutputStream();
                while (true) {
                    synchronized (buffer) {
                        int amountRead = in.read(buffer);
                        if (amountRead == -1) {
                            break;
                        }
                        out.write(buffer, 0, amountRead);
                    }
                }

                String nfo = out.toString();

                if (!parseXMLNFO(nfo, movie)) {
                    movieDB.scanNFO(nfo, movie);

                    logger.finest("Scanning NFO for Poster URL");
                    int urlStartIndex = 0;
                    while (urlStartIndex >= 0 && urlStartIndex < nfo.length()) {
    //					logger.finest("Looking for URL start found in nfo urlStartIndex  = " + urlStartIndex);
                        int currentUrlStartIndex = nfo.indexOf("http://", urlStartIndex);
                        if (currentUrlStartIndex >= 0) {
    //						logger.finest("URL start found in nfo at pos=" + currentUrlStartIndex);
                            int currentUrlEndIndex = nfo.indexOf("jpg", currentUrlStartIndex);
                            if (currentUrlEndIndex < 0) {
                                currentUrlEndIndex = nfo.indexOf("JPG", currentUrlStartIndex);
                            }
                            if (currentUrlEndIndex >= 0) {
                                int nextUrlStartIndex = nfo.indexOf("http://", currentUrlStartIndex);
                                // look for shortest http://
                                while ((nextUrlStartIndex != -1) && (nextUrlStartIndex < currentUrlEndIndex + 3)) {
                                    currentUrlStartIndex = nextUrlStartIndex;
                                    nextUrlStartIndex = nfo.indexOf("http://", currentUrlStartIndex + 1);
                                }
                                logger.finer("Poster URL found in nfo = " + nfo.substring(currentUrlStartIndex, currentUrlEndIndex + 3));
                                movie.setPosterURL(nfo.substring(currentUrlStartIndex, currentUrlEndIndex + 3));
                                urlStartIndex = -1;
                            } else {
                                urlStartIndex = currentUrlStartIndex + 3;
                            }
                        } else {
                            urlStartIndex = -1;
                        }
                    }
                }

            } catch (IOException e) {
                logger.severe("Failed reading " + nfoFile.getName());
                e.printStackTrace();
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    logger.severe("Failed closing: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Check to see if the passed filename exists with nfo extensions
     * 
     * @param checkNFOfilename (NO EXTENSION)
     * @return blank string if not found, filename if found
     */
    private String checkNFO(String checkNFOfilename) {
        File nfoFile = new File(checkNFOfilename + ".nfo");
        if (nfoFile.exists()) {
            return (checkNFOfilename + ".nfo");
        } else {
            nfoFile = new File(checkNFOfilename + ".NFO");
            if (nfoFile.exists()) {
                return (checkNFOfilename + ".NFO");
            } else {
                return ("");
            }
        }
    }
    
    private boolean parseXMLNFO(String nfo, Movie movie) {
        boolean retval = true;
        if (nfo.indexOf("<movie>") > -1) {
            parseMovieNFO(nfo, movie);
        //} else if (nfo.indexOf("<tvshow>") > -1) {
        //    parseTVNFO(nfo, movie);
        //} else if (nfo.indexOf("<episodedetails>") > -1) {
        //    parseEpisodeNFO(nfo, movie);
        } else {
            retval = false;
        }
        return retval;
    }
    
    /**
     * Used to parse out the XBMC nfo xml data for movies
     * 
     * @param xmlFile
     * @param movie
     */
    private void parseMovieNFO(String nfo, Movie movie) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader r = factory.createXMLEventReader(new StringReader(nfo));

            while (r.hasNext()) {
                XMLEvent e = r.nextEvent();
                String tag = e.toString();
                if (tag.equalsIgnoreCase("<title>")) {
                    movie.setTitle(parseCData(r));
                } else if (tag.equalsIgnoreCase("<originaltitle>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<rating>")) {
                    movie.setRating(Math.round(Float.parseFloat(parseCData(r)) * 10f));
                } else if (tag.equalsIgnoreCase("<year>")) {
                    movie.setYear(parseCData(r));
                } else if (tag.equalsIgnoreCase("<top250>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<votes>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<outline>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<plot>")) {
                    movie.setPlot(parseCData(r));
                } else if (tag.equalsIgnoreCase("<tagline>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<runtime>")) {
                    movie.setRuntime(parseCData(r));
                } else if (tag.equalsIgnoreCase("<thumb>")) {
                    movie.setPosterURL(parseCData(r));
                } else if (tag.equalsIgnoreCase("<mpaa>")) {
                    movie.setCertification(parseCData(r));
                } else if (tag.equalsIgnoreCase("<playcount>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<watched>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<id>")) {
                    movie.setId(ImdbPlugin.IMDB_PLUGIN_ID, parseCData(r));
                } else if (tag.equalsIgnoreCase("<filenameandpath>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<trailer>")) {
                    String trailer = parseCData(r).trim();
                    if (!trailer.isEmpty()) {
                        TrailerFile tf = new TrailerFile();
                        tf.setNewFile(false);
                        tf.setFilename(trailer);
                        movie.addTrailerFile(tf);
                    }
                } else if (tag.equalsIgnoreCase("<genre>")) {
                    movie.addGenre(parseCData(r));
                } else if (tag.equalsIgnoreCase("<credits>")) {
                    // ignored
                } else if (tag.equalsIgnoreCase("<director>")) {
                    movie.setDirector(parseCData(r));
                } else if (tag.equalsIgnoreCase("<actor>")) {
                    String event = r.nextEvent().toString();
                    while (!event.equalsIgnoreCase("</actor>")) {
                        if (event.equalsIgnoreCase("<name>")) {
                            movie.addActor(parseCData(r));
                        } else if (event.equalsIgnoreCase("<role>")) {
                            // ignored
                        }
                        event = r.nextEvent().toString();
                    }
                }
                
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed parsing NFO file for movie: " + movie.getTitle() + ". Please fix or remove it.");
        }
    }  
    
    private String parseCData(XMLEventReader r) throws XMLStreamException {
        StringBuffer sb = new StringBuffer();
        XMLEvent e;
        while ((e = r.nextEvent()) instanceof Characters) {
            sb.append(e.toString());
        }
        return HTMLTools.decodeHtml(sb.toString());
    }
    
}
