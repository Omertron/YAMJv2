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
package com.moviejukebox.plugin.poster;

import com.moviejukebox.model.IMovieBasicInformation;
import com.moviejukebox.model.Identifiable;
import com.moviejukebox.model.IImage;

public interface IPosterPlugin {

    public String getName();

    /**
     * Check to see if the class is needed by reading the appropriate searchPriority property
     * @return true if the class is needed (in the searchPriority)
     */
    public boolean isNeeded();
    
    public IImage getPosterUrl(Identifiable ident, IMovieBasicInformation movieInformation);
}
