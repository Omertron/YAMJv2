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

import com.moviejukebox.model.IImage;
import com.moviejukebox.model.Image;
import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.ImdbInfo;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;

public class ImdbPosterPlugin extends AbstractMoviePosterPlugin {
    private static Logger logger = Logger.getLogger(ImdbPosterPlugin.class);
    private WebBrowser webBrowser;
    private ImdbInfo imdbInfo;

    public ImdbPosterPlugin() {
        super();

        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }

        webBrowser = new WebBrowser();
        imdbInfo = new ImdbInfo();
    }

    @Override
    public String getIdFromMovieInfo(String title, String year) {
        String response = Movie.UNKNOWN;

        try {
            String imdbId = imdbInfo.getImdbId(title, year);
            if (StringTools.isValidString(imdbId)) {
                response = imdbId;
            }
        } catch (Exception error) {
            logger.error("PosterScanner: Imdb Error: " + error.getMessage());
            return Movie.UNKNOWN;
        }
        return response;
    }

    @Override
    public IImage getPosterUrl(String title, String year) {
        return getPosterUrl(getIdFromMovieInfo(title, year));
    }

    @Override
    public IImage getPosterUrl(String id) {
        String posterURL = Movie.UNKNOWN;
        String imdbXML;

        try {
            if (StringTools.isValidString(id)) {
                imdbXML = webBrowser.request(imdbInfo.getSiteDef().getSite() + "title/" + id + "/", imdbInfo.getSiteDef().getCharset());

                StringTokenizer st;

                // Use cast token to avoid internalization trouble
                int castIndex, beginIndex ;
                castIndex = imdbXML.indexOf("<h3>" + imdbInfo.getSiteDef().getCast() + "</h3>");

                if (castIndex > -1) {
                    // Use the old format
                    beginIndex = imdbXML.indexOf("src=\"http://ia.media-imdb.com/images");
                    st = new StringTokenizer(new String(imdbXML.substring(beginIndex + 5)), "\"");
                } else {
                    // Try the new format
                    castIndex = imdbXML.indexOf("<h2>" + imdbInfo.getSiteDef().getCast() + "</h2>");
                    beginIndex = imdbXML.indexOf("href='http://ia.media-imdb.com/images");
                    st = new StringTokenizer(new String(imdbXML.substring(beginIndex + 6)), "'");
                }

                // Search the XML from IMDB for a poster
                if ((beginIndex < castIndex) && (beginIndex != -1)) {
                    posterURL = st.nextToken();
                    int index = posterURL.indexOf("_SX");
                    if (index != -1) {
                        posterURL = new String(posterURL.substring(0, index)) + "_SX600_SY800_.jpg";
                    } else {
                        posterURL = Movie.UNKNOWN;
                    }
                    logger.debug("PosterScanner: Imdb found poster @: " + posterURL);
                }


            }
        } catch (Exception error) {
            logger.error("PosterScanner: Imdb Error: " + error.getMessage());
            return Image.UNKNOWN;
        }

        if (StringTools.isValidString(posterURL)) {
            return new Image(posterURL);
        }

        return Image.UNKNOWN;
    }

    @Override
    public String getName() {
        return "imdb";
    }

}
