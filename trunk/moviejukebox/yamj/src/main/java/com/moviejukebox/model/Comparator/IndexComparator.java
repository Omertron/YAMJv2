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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.Library;
import com.moviejukebox.tools.FileTools;

/**
 * @author ilgizar
 */
public class IndexComparator implements Comparator<Map.Entry<String, List<Movie>>>{
    Library library = null;
    String categoryName = null;

    public IndexComparator(Library library, String categoryName) {
        this.library = library;
        this.categoryName = categoryName;
    }

    @Override
    public int compare(Map.Entry<String, List<Movie>> first, Map.Entry<String, List<Movie>> second) {
        if (library == null || categoryName == null) {
            return 0;
        }
        return library.getMovieCountForIndex(categoryName, FileTools.createCategoryKey(second.getKey())) - library.getMovieCountForIndex(categoryName, FileTools.createCategoryKey(first.getKey()));
    }
}