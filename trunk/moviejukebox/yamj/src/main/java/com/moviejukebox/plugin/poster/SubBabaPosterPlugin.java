/*
 *      Copyright (c) 2004-2015 YAMJ Members
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
import com.moviejukebox.model.Image;
import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;
import com.omertron.subbabaapi.SubBabaApi;
import com.omertron.subbabaapi.enumerations.SearchType;
import com.omertron.subbabaapi.model.SubBabaContent;
import com.omertron.subbabaapi.model.SubBabaMovie;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubBabaPosterPlugin extends AbstractMoviePosterPlugin implements ITvShowPosterPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(SubBabaPosterPlugin.class);
    private static final String API_KEY = PropertiesUtil.getProperty("API_KEY_SubBaba");
    private SubBabaApi subBaba;

    public SubBabaPosterPlugin() {
        super();

        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }

        subBaba = new SubBabaApi(API_KEY);

        // We need to set the proxy parameters if set.
        subBaba.setProxy(WebBrowser.getMjbProxyHost(), WebBrowser.getMjbProxyPort(), WebBrowser.getMjbProxyUsername(), WebBrowser.getMjbProxyPassword());

        // Set the timeout values
        subBaba.setTimeout(WebBrowser.getMjbTimeoutConnect(), WebBrowser.getMjbTimeoutRead());
    }

    /**
     * Get the Sub-Baba ID for the movie
     *
     * @param title
     * @param year
     * @return
     */
    @Override
    public String getIdFromMovieInfo(String title, String year) {
        if (StringTools.isNotValidString(title)) {
            return Movie.UNKNOWN;
        }

        if (StringUtils.isBlank(year)) {
            LOG.debug("Searching for title '{}'", title);
        } else {
            LOG.debug("Searching for title '{}' with year '{}'", title, year);
        }
        // Use the ALL search type because we don't care what type there is
        List<SubBabaMovie> sbMovies = subBaba.searchByEnglishName(title, SearchType.ALL);

        if (sbMovies != null && !sbMovies.isEmpty()) {
            LOG.debug("Found {} movies", sbMovies.size());
            for (SubBabaMovie sbm : sbMovies) {
                for (SubBabaContent m : sbm.getContent()) {
                    LOG.debug("SubBaba ID: {}", m.getId());
                    return String.valueOf(m.getId());
                }
            }
        }
        return Movie.UNKNOWN;
    }

    /**
     * Use the ID to find the poster
     *
     * @param id This should be the IMDB ID. Sub-Baba does not use it's own ID.
     * @return
     */
    @Override
    public IImage getPosterUrl(String id) {
        LOG.debug("Searching for poster URL with ID: {}", id);
        if (StringTools.isNotValidString(id)) {
            // Invalid ID
            return Image.UNKNOWN;
        }

        IImage posterImage = getFirstContent(id, SearchType.POSTERS);
        if (posterImage.equals(Image.UNKNOWN)) {
            // No posters found, try DVD Covers
            posterImage = getFirstContent(id, SearchType.DVD_COVERS);
            if (!posterImage.equals(Image.UNKNOWN)) {
                // Crop the image
                LOG.debug("Found DVD Cover for ID {}, cropping image to poster size.", id);
                posterImage.setSubimage("0, 0, 47, 100");
            }
        }

        if (!posterImage.equals(Image.UNKNOWN)) {
            LOG.debug("Found poster URL: {}", posterImage.getUrl());
        }

        return posterImage;
    }

    /**
     * Find the first piece of content that matches the search type.
     *
     * @param imdbId
     * @param searchType
     * @return
     */
    private IImage getFirstContent(String imdbId, SearchType searchType) {
        SubBabaMovie sbm = subBaba.searchByImdbId(imdbId, searchType);
        if (sbm == null) {
            // Nothing returned for the movie
            LOG.debug("No information found for movie ID {}", imdbId);
            return Image.UNKNOWN;
        }

        List<SubBabaContent> content = sbm.getContent();

        if (content == null || content.isEmpty()) {
            // Nothing returned for the movie
            LOG.debug("No {} found for movie ID {}", searchType.toString(), imdbId);
            return Image.UNKNOWN;
        }

        // return the first poster's download URL
        return new Image(content.get(0).getDownload());
    }

    @Override
    public IImage getPosterUrl(String title, String year) {
        return getPosterUrl(getIdFromMovieInfo(title, year));
    }

    @Override
    public String getIdFromMovieInfo(String title, String year, int tvSeason) {
        return getIdFromMovieInfo(title, year);
    }

    @Override
    public IImage getPosterUrl(String title, String year, int tvSeason) {
        return getPosterUrl(title, year);
    }

    @Override
    public IImage getPosterUrl(String id, int season) {
        return getPosterUrl(id);
    }

    @Override
    public String getName() {
        return "subbaba";
    }
}
