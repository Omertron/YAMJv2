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
package com.moviejukebox.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.pojava.datetime.DateTime;

import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.tools.FileTools;

public class WatchedScanner {
    /**
     * Calculate the watched state of a movie based on the files <filename>.watched & <filename>.unwatched
     * Always assumes that the file is unwatched if nothing is found.
     * @param movie
     */
    public static boolean checkWatched(Jukebox jukebox, Movie movie) {
        int fileWatchedCount = 0;
        boolean movieWatched = true;    // Assume it's watched.
        boolean fileWatched = false;
        boolean returnStatus = false;   // Assume no changes
        
        File foundFile = null;
        Collection<String> extensions = new ArrayList<String>();
        extensions.add("unwatched");
        extensions.add("watched");
        
        for (MovieFile mf : movie.getFiles()) {
            // Check that the file pointer is valid
            if (mf.getFile() == null) {
                continue;
            }
            
            fileWatched = false;
            String filename = "";
            // BluRay stores the file differently to DVD and single files, so we need to process the path a little
            if (movie.isBluray()) {
                filename = new File(FileTools.getParentFolder(mf.getFile())).getName();
            } else {
                filename = mf.getFile().getName();
            }
            
            foundFile = FileTools.findFilenameInCache(filename, extensions, jukebox, "Watched Scanner: ");
            
            if (foundFile != null) {
                fileWatchedCount++;
                if (foundFile.getName().toLowerCase().endsWith(".watched")) {
                    fileWatched = true;
                    mf.setWatchedDate(new DateTime().toMillis());
                } else {
                    // We've found a specific file <filename>.unwatched, so we clear the settings
                    fileWatched = false;
                    mf.setWatchedDate(0); // remove the date if it exists
                }
            }

            mf.setWatched(fileWatched); // Set the watched status
            // As soon as there is an unwatched file, the whole movie becomes unwatched
            movieWatched = movieWatched && fileWatched;
            
        }
        
        // Only change the watched status if we found at least 1 file
        if ((fileWatchedCount > 0) && (movie.isWatchedFile() != movieWatched)) {
            movie.setWatchedFile(movieWatched);
            movie.setDirty(true);
            
            // Issue 1949 - Force the artwork to be overwritten
            movie.setDirtyPoster(true);
            movie.setDirtyBanner(true);
            
            returnStatus = true;
        }
        
        // If there are no files found and the movie is watched(file), reset the status
        if ((fileWatchedCount == 0) && movie.isWatchedFile()) {
            movie.setWatchedFile(movieWatched);
            movie.setDirty(true);
            
            // Issue 1949 - Force the artwork to be overwritten
            movie.setDirtyPoster(true);
            movie.setDirtyBanner(true);

            returnStatus = true;
        }

        return returnStatus;
    }

}
