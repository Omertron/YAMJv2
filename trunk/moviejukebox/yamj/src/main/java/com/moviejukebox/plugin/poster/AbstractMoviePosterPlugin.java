/*
 *      Copyright (c) 2004-2013 YAMJ Members
 *      http://code.google.com/p/moviejukebox/people/list
 *
 *      This file is part of the Yet Another Movie Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: http://code.google.com/p/moviejukebox/
 *
 */
package com.moviejukebox.plugin.poster;

import com.moviejukebox.model.IImage;
import com.moviejukebox.model.IMovieBasicInformation;
import com.moviejukebox.model.Identifiable;
import com.moviejukebox.model.Image;
import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import org.apache.log4j.Logger;

public abstract class AbstractMoviePosterPlugin implements IMoviePosterPlugin {

    private static final Logger LOG = Logger.getLogger(AbstractMoviePosterPlugin.class);
    protected static final String SEARCH_PRIORITY_MOVIE = PropertiesUtil.getProperty("poster.scanner.SearchPriority.movie", "").toLowerCase();
    protected static final String SEARCH_PRIORITY_TV = PropertiesUtil.getProperty("poster.scanner.SearchPriority.tv", "").toLowerCase();

    public AbstractMoviePosterPlugin() {
    }

    @Override
    public boolean isNeeded() {
        return SEARCH_PRIORITY_MOVIE.contains(this.getName());
    }

    @Override
    public IImage getPosterUrl(Identifiable ident, IMovieBasicInformation movieInformation) {
        String id = getId(ident);
        if (StringTools.isNotValidString(id)) {
            id = getIdFromMovieInfo(movieInformation.getOriginalTitle(), movieInformation.getYear());
            // ID found
            if (StringTools.isValidString(id)) {
                LOG.debug(this.getName() + " posterPlugin: ID found setting it to '" + id + "'");
                ident.setId(getName(), id);
            } else if (!movieInformation.getOriginalTitle().equalsIgnoreCase(movieInformation.getTitle())) {
                // Didn't find the movie with the original title, try the normal title if it's different
                id = getIdFromMovieInfo(movieInformation.getTitle(), movieInformation.getYear());
                // ID found
                if (StringTools.isValidString(id)) {
                    LOG.debug(this.getName() + " posterPlugin: ID found setting it to '" + id + "'");
                    ident.setId(getName(), id);
                }
            }
        } else {
            LOG.debug(this.getName() + " posterPlugin: ID already in movie using it, skipping ID search: '" + id + "'");
        }

        if (!(StringTools.isNotValidString(id) || "-1".equals(id) || "0".equals(id))) {
            return getPosterUrl(id);
        }

        return Image.UNKNOWN;
    }

    private String getId(Identifiable ident) {
        String response = Movie.UNKNOWN;
        if (ident != null) {
            String id = ident.getId(this.getName());
            if (id != null) {
                response = id;
            }
        }
        return response;
    }
}
