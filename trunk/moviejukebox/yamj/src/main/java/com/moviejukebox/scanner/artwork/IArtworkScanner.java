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
package com.moviejukebox.scanner.artwork;

import com.moviejukebox.model.IImage;
import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Movie;

public interface IArtworkScanner {

    public String scan(Jukebox jukebox, Movie movie);

    public String scanLocalArtwork(Jukebox jukebox, Movie movie);

    public String scanOnlineArtwork(Movie movie);

    public boolean validateArtwork(IImage artworkImage);

    public boolean validateArtwork(IImage artworkImage, int artworkWidth, int artworkHeight, boolean checkAspect);

    public boolean saveArtworkToJukebox(Jukebox jukebox, Movie movie);

    /**
     * Updates the correct Filename based on the artwork type
     *
     * @param movie
     * @param artworkFilename
     */
    abstract void setArtworkFilename(Movie movie, String artworkFilename);

    /**
     * Returns the correct Filename based on the artwork type This should be
     * overridden at the artwork specific class level
     *
     * @param movie
     * @return
     */
    abstract String getArtworkFilename(Movie movie);

    /**
     * Updates the correct URL based on the artwork type
     *
     * @param movie
     * @param artworkUrl
     */
    abstract void setArtworkUrl(Movie movie, String artworkUrl);

    /**
     * Returns the correct URL based on the artwork type
     *
     * @param movie
     * @return
     */
    abstract String getArtworkUrl(Movie movie);

    /**
     * Sets the correct image plugin for the artwork type
     */
    abstract void setArtworkImagePlugin();

    /**
     * Return the value of the appropriate "dirty" setting for the artwork and
     * movie
     *
     * @param movie
     * @return
     */
    abstract boolean isDirtyArtwork(Movie movie);

    /**
     * Set the appropriate "dirty" setting for the artwork and movie
     *
     * @param movie
     * @param dirty
     */
    abstract void setDirtyArtwork(Movie movie, boolean dirty);

    /**
     * Determine if an online search should be performed for a particular movie
     * and artwork type. Properties should be checked as should scrape library
     * and ID = 0/-1
     *
     * @param movie
     * @return true if online scraping should be done
     */
    abstract boolean getOnlineArtwork(Movie movie);

    /**
     * Determine if the artwork type is required.
     *
     * @return true if the artwork is required
     */
    abstract boolean isRequired();

    /**
     * Determine if the artwork type is required for local searching
     *
     * @return
     */
    abstract boolean isRequiredLocal();

    /**
     * Determine if the artwork is required for Online TV search
     *
     * @return
     */
    abstract boolean isRequiredTV();

    /**
     * Determine if the artwork is required for Online Movie search
     *
     * @return
     */
    abstract boolean isRequiredMovie();

    /**
     * Create a operating system safe filename for the artwork
     *
     * @param movie
     * @return
     */
    abstract String makeSafeArtworkFilename(Movie movie);
}
