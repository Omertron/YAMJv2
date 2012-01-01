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
package com.moviejukebox.model.Comparator;

import java.util.Comparator;

import com.moviejukebox.model.Movie;

/**
 * @author altman.matthew
 */
public class LastModifiedComparator implements Comparator<Movie> {

    @Override
    public int compare(Movie movie1, Movie movie2) {
        int retVal = 0;

        if (movie1.getLastModifiedTimestamp() > movie2.getLastModifiedTimestamp()) {
            retVal = -1;
        } else if (movie1.getLastModifiedTimestamp() < movie2.getLastModifiedTimestamp()) {
            retVal = 1;
        }
        return retVal;
    }
}
