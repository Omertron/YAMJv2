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

import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;

/**
 * User: JDGJr
 * Date: Feb 15, 2009
 */
public interface MovieListingPlugin {

  public static String typeMovie = "Movie";
  public static String typeTVShow = "TV Show";
  public static String typeTVShowNoSpace = "TVShow";
  public static String typeExtra = "Extra";
  public static String typeAll = "All";

  public static String UNKNOWN = Movie.UNKNOWN;

  public void generate(Jukebox jukebox, Library library);

} // interface MovieListingPlugin
