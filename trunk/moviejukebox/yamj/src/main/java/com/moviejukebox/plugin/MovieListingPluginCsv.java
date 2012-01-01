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

import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.ExtraFile;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.CSVWriter;

import com.moviejukebox.tools.SystemTools;
import org.apache.log4j.Logger;
import java.util.Collection;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;

/**
 * User: JDGJr
 * Date: Feb 15, 2009
 */
public class MovieListingPluginCsv extends MovieListingPluginBase implements MovieListingPlugin {

    private static Logger logger = Logger.getLogger("moviejukebox");

    /**
     * @return CSV-formatted header row
     */
    protected String headerLine() {
        return prepOutput("Type")
                + prepOutput("Title")
                + prepOutput("TitleSort")
                + prepOutput("IMDB ID")
                + prepOutput("Director")
                + prepOutput("Company")
                + prepOutput("Country")
                + prepOutput("Language")
                + prepOutput("Runtime")
                + prepOutput("Release Date")
                + prepOutput("Year")
                + prepOutput("Certification")
                + prepOutput("Season #")
                + prepOutput("TheTVDB ID")
                + prepOutput("VideoSource")
                + prepOutput("Container")
                + prepOutput("File")
                + prepOutput("Audio Codec")
                + prepOutput("Audio Channels")
                + prepOutput("Resolution")
                + prepOutput("Video Codec")
                + prepOutput("Video Output")
                + prepOutput("FPS")
                + prepOutput("# Files")
                + prepOutput("# Extras")
                + prepOutput("# Genres")
                + prepOutput("# Cast")
                + prepOutput("SubTitles?")
                + prepOutput("Poster?")
                + prepOutput("Detail Poster Filename")
                + prepOutput("Rating #")
                + prepOutput("Top 250 #")
                + prepOutput("Library Description")
                + prepOutput("Library Path")
                + prepOutput("Allocine ID")
                + prepOutput("FilmDelta ID")
                + prepOutput("FilmUpIT ID")
                + prepOutput("FilmWeb ID")
                + prepOutput("Kinopoisk ID")
                + prepOutput("Animator ID")
                + prepOutput("Sratim ID")
                + prepOutput("Last Modified Date")
                + prepOutput("File Size", false)
                + prepOutput("Watched");
    } // headerLine();

    /**
     * @param sItemType
     * @param movie
     * @return output string properly formatted for CSV output
     */
    protected String toCSV(String sItemType, Movie movie) {
        Collection<ExtraFile> extras = movie.getExtraFiles();
        Collection<MovieFile> movieFiles = movie.getMovieFiles();
        Collection<String> genres = movie.getGenres();
        Collection<String> cast = movie.getCast();

        return prepOutput(sItemType)
                + prepOutput(movie.getTitle())
                + prepOutput(movie.getTitleSort())
                + prepOutput(movie.getId(ImdbPlugin.IMDB_PLUGIN_ID))
                + prepOutput(movie.getDirector())
                + prepOutput(movie.getCompany())
                + prepOutput(movie.getCountry())
                + prepOutput(movie.getLanguage())
                + prepOutput(movie.getRuntime())
                + prepOutput(movie.getReleaseDate())
                + prepOutput(movie.getYear())
                + prepOutput(movie.getCertification())
                + prepOutput(blankNegatives(movie.getSeason()))
                + prepOutput(movie.getId(TheTvDBPlugin.THETVDB_PLUGIN_ID))
                + prepOutput(movie.getVideoSource())
                + prepOutput(movie.getContainer())
                + prepOutput(movie.getContainerFile().getAbsolutePath())
                + prepOutput(movie.getAudioCodec())
                + prepOutput(movie.getAudioChannels())
                + prepOutput(movie.getResolution())
                + prepOutput(movie.getVideoCodec())
                + prepOutput(movie.getVideoOutput())
                + prepOutput(Float.toString(movie.getFps()))
                + prepOutput(String.valueOf(null == movieFiles ? "0" : movieFiles.size()))
                + prepOutput(String.valueOf(null == extras ? "0" : extras.size()))
                + prepOutput(String.valueOf(null == genres ? "0" : genres.size()))
                + prepOutput(String.valueOf(null == cast ? "0" : cast.size()))
                + prepOutput(movie.getSubtitles())
                + prepOutput((null != movie.getPosterURL() ? "True" : "False"))
                + prepOutput(String.valueOf(null != movie.getDetailPosterFilename() ? "True" : "False"))
                + prepOutput(String.valueOf(movie.getRating()))
                + prepOutput(String.valueOf(movie.getTop250()))
                + prepOutput(movie.getLibraryDescription())
                + prepOutput(movie.getLibraryPath())
                + prepOutput(movie.getId(AllocinePlugin.ALLOCINE_PLUGIN_ID))
                + prepOutput(movie.getId(FilmDeltaSEPlugin.FILMDELTA_PLUGIN_ID))
                + prepOutput(movie.getId(FilmUpITPlugin.FILMUPIT_PLUGIN_ID))
                + prepOutput(movie.getId(FilmwebPlugin.FILMWEB_PLUGIN_ID))
                + prepOutput(movie.getId(KinopoiskPlugin.KINOPOISK_PLUGIN_ID))
                + prepOutput(movie.getId(AnimatorPlugin.ANIMATOR_PLUGIN_ID))
                + prepOutput(movie.getId(SratimPlugin.SRATIM_PLUGIN_ID))
                + prepOutput(new Timestamp(movie.getLastModifiedTimestamp()).toString()) //, false)
                + prepOutput(movie.getFileSizeString())
                + prepOutput(String.valueOf(movie.isWatched()));
    } // toCSV()

    /**
     * @param i
     * @return empty string if input = -1
     */
    protected String blankNegatives(int i) {
        if (0 <= i) {
            return String.valueOf(i);
        } else {
            return "";
        }
    } // blankNegatives()

    /**
     * @param str
     * @return String cleaned up, ALWAYS comma appended
     */
    protected String prepOutput(String str) {
        return prepOutput(str, true);
    } // prepOutput()

    /**
     * @param str
     * @param bAddComma
     * @return String cleaned up, optional comma appended
     */
    protected String prepOutput(String str, boolean bAddComma) {
        if (null == str) {
            str = "";
        } else if (blankUNKNOWN && (UNKNOWN.equals(str) || UNDEFINED.equals(str))) {
            // clean 'UNKNOWN' values
            str = "";
        }

        // remove quotes from the string (before encapsulation)
        if (str.contains("\"")) {
            str = str.replace("\"", "");
        }

        // enclose strings with commas in quotes
        if (str.contains(",")) {
            str = "\"" + str + "\"";
        }

        // add trailing comma unless otherwise requested
        if (bAddComma) {
            str += ",";
        }
        return str;
    } // encloseInQuotes()

    /**
     * @param tempJukeboxRoot
     * @param jukeboxRoot
     * @param library
     */
    @Override
    public void generate(Jukebox jukebox, Library library) {
        initialize(jukebox);

        String filename = baseFilename + ".csv";
        File csvFile = new File(jukebox.getJukeboxTempLocation(), filename);

        ArrayList<String> alTypes = getSelectedTypes();
        try {
            CSVWriter writer = new CSVWriter(csvFile);
            logger.debug("  Writing CSV to: " + csvFile.getAbsolutePath());

            // write header line
            writer.line(headerLine());

            if (!groupByType) {
                for (Movie movie : library.values()) {
                    logger.debug(movie.toString());

                    String sType;
                    if (movie.isExtra()) {
                        sType = typeExtra;
                    } else if (movie.isTVShow()) {
                        sType = typeTVShow;
                    } else {
                        sType = typeMovie;
                    }

                    if (null != sType && alTypes.contains(sType)) {
                        writer.line(toCSV(sType, movie));
                    }
                } // for movie
            } else {
                for (String thisType : alTypes) {
                    for (Movie movie : library.values()) {
                        String sType;
                        if (movie.isExtra()) {
                            sType = typeExtra;
                        } else if (movie.isTVShow()) {
                            sType = typeTVShow;
                        } else {
                            sType = typeMovie;
                        }

                        if (null != sType && thisType.equalsIgnoreCase(sType)) {
                            writer.line(toCSV(sType, movie));
                        }
                    }
                }
            }

            writer.close();
        } catch (IOException error) {
            logger.error(SystemTools.getStackTrace(error));
        }

        // move to configured (default) location
        copyListingFile(csvFile, filename);

    } // generate()
} // class MovieListingPluginCsv

