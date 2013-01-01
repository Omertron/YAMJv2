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
import com.moviejukebox.model.Image;
import com.moviejukebox.model.Movie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;
import java.net.URLEncoder;
import java.text.Normalizer;
import org.apache.log4j.Logger;

public class MovieCoversPosterPlugin extends AbstractMoviePosterPlugin {
    private static final Logger logger = Logger.getLogger(MovieCoversPosterPlugin.class);
    private WebBrowser webBrowser;

    public MovieCoversPosterPlugin() {
        super();

        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }

        webBrowser = new WebBrowser();
    }

    @Override
    public String getIdFromMovieInfo(String title, String year) {
        String returnString = Movie.UNKNOWN;

        try {
            StringBuilder sb = new StringBuilder("http://www.moviecovers.com/multicrit.html?titre=");
            sb.append(URLEncoder.encode(title.replace("\u0153", "oe"), "iso-8859-1"));

            if (StringTools.isValidString(year)) {
                sb.append("&anneemin=");
                sb.append(URLEncoder.encode(Integer.toString(Integer.parseInt(year) - 1), "iso-8859-1"));
                sb.append("&anneemax=");
                sb.append(URLEncoder.encode(Integer.toString(Integer.parseInt(year) + 1), "iso-8859-1"));
            }
            sb.append("&slow=0&tri=Titre&listes=1");
            logger.debug("MovieCoversPosterPlugin: Searching for: " + sb.toString());

            String content = webBrowser.request(sb.toString());

            if (content != null) {
                String formattedTitle = Normalizer.normalize(title.replace("\u0153", "oe").toUpperCase(), Normalizer.Form.NFD).replaceAll("[\u0300-\u036F]", "");
                if (formattedTitle.endsWith(" (TV)")) {
                    formattedTitle = new String(formattedTitle.substring(0, formattedTitle.length() - 5));
                }
                String formattedTitleNormalized = formattedTitle;
                for (String prefix : Movie.getSortIgnorePrefixes()) {
                    if (formattedTitle.startsWith(prefix.toUpperCase())) {
                        formattedTitleNormalized = new String(formattedTitle.substring(prefix.length())) + " (" + prefix.toUpperCase().replace(" ","") + ")";
                        break;
                    }
                }
                // logger.debug("MovieCoversPosterPlugin: Looking for a poster for: " + formattedTitleNormalized);
                // Checking for "no result" message...
                if (!content.contains("/forum/index.html?forum=MovieCovers&vue=demande")) {
                    // There is some results
                    for (String filmURL : HTMLTools.extractTags(content, "<TD bgcolor=\"#339900\"", "<FORM action=\"/multicrit.html\"", "<LI><A href=\"/film/titre", "</A>", false)) {
                        if ( (filmURL.endsWith(formattedTitleNormalized)) || (filmURL.endsWith(formattedTitle)) ) {
                            returnString = HTMLTools.extractTag(filmURL, "_", ".html\">");
                            // logger.debug("MovieCoversPosterPlugin: Seems to find something: " + returnString + " - " + filmURL);
                            break;
                        }
                    }
                }
                // Search the forum if no answer
                if (returnString.equalsIgnoreCase(Movie.UNKNOWN)) {
                    sb = new StringBuilder("http://www.moviecovers.com/forum/search-mysql.html?forum=MovieCovers&query=");
                    sb.append(URLEncoder.encode(formattedTitle, "iso-8859-1"));
                    // logger.debug("MovieCoversPosterPlugin: We have to explore the forums: " + sb);
                    content = webBrowser.request(sb.toString());
                    if (content != null) {
                        // Loop through the search results
                        for (String filmURL : HTMLTools.extractTags(content, "<TABLE border=\"0\" cellpadding=\"0\" cellspacing=\"0\">", "<FORM action=\"search-mysql.html\">", "<TD><A href=\"fil.html?query=", "</A></TD>", false)) {
                            // logger.debug("MovieCoversPosterPlugin: examining: " + filmURL);
                            if ( (filmURL.endsWith(formattedTitleNormalized)) || (filmURL.endsWith(formattedTitle)) ) {
                                content = webBrowser.request("http://www.moviecovers.com/forum/fil.html?query=" + new String(filmURL.substring(0,filmURL.length()-formattedTitle.length()-2)));
                                if (content != null) {
                                    int sizePoster;
                                    int oldSizePoster = 0;
                                    // A quick trick to find a " fr " reference in the comments
                                    int indexFR = content.toUpperCase().indexOf(" FR ");
                                    if (indexFR != -1) {
                                        content = "</STRONG></B></FONT>" + new String(content.substring(indexFR));
                                    }
                                    // Search the biggest picture
                                    for (String poster : HTMLTools.extractTags(content, "</STRONG></B></FONT>", ">MovieCovers Team<", "<LI><A TARGET=\"affiche\" ", "Ko)", false)) {
                                        sizePoster = Integer.parseInt(HTMLTools.extractTag(poster, ".jpg\">Image .JPG</A> ("));
                                        if (sizePoster > oldSizePoster) {
                                            oldSizePoster = sizePoster;
                                            returnString = HTMLTools.extractTag(poster, "HREF=\"/getjpg.html/", ".jpg\">Image .JPG</A>");
                                            if (indexFR != -1) {
                                                break;
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception error) {
            logger.error("MovieCoversPosterPlugin: Failed retreiving Moviecovers poster URL: " + title);
            logger.error("MovieCoversPosterPlugin: Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
        logger.debug("MovieCoversPosterPlugin: retreiving Moviecovers poster URL: " + returnString);
        return returnString;
    }

    @Override
    public IImage getPosterUrl(String title, String year) {
       return getPosterUrl(getIdFromMovieInfo(title, year));
    }

    @Override
    public IImage getPosterUrl(String id) {
        String posterURL = Movie.UNKNOWN;
        try {
            if (id != null && !Movie.UNKNOWN.equalsIgnoreCase(id)) {
                logger.debug("MovieCoversPosterPlugin : Movie found on moviecovers.com" + id);
                posterURL = "http://www.moviecovers.com/getjpg.html/" + id.replace("+", "%20");
            } else {
                logger.debug("MovieCoversPosterPlugin: Unable to find posters for " + id);
            }
        } catch (Exception error) {
            logger.debug("MovieCoversPosterPlugin: MovieCovers.com API Error: " + error.getMessage());
        }

        if (!Movie.UNKNOWN.equalsIgnoreCase(posterURL)) {
            return new Image(posterURL);
        }
        return Image.UNKNOWN;
    }

    @Override
    public String getName() {
        return "moviecovers";
    }

}
