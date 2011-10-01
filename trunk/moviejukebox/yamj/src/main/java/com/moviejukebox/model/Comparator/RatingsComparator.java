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
package com.moviejukebox.model.Comparator;

import com.moviejukebox.model.Movie;

/**
 * @author ilgizar 
 * based on RatingComparator.java
 */
public class RatingsComparator extends RatingComparator {

    @Override
    public int compare(Movie movie1, Movie movie2) {
        return compare(movie1, movie2, false);
    }
}
