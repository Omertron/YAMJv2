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
package com.moviejukebox.plugin.poster;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import org.apache.log4j.Logger;

import com.moviejukebox.model.Movie;
import com.moviejukebox.model.IImage;
import com.moviejukebox.model.Image;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.WebBrowser;

public class CaratulasdecinePosterPlugin extends AbstractMoviePosterPlugin {
//    private static Logger logger = Logger.getLogger("moviejukebox");

    private WebBrowser webBrowser = new WebBrowser();

    public CaratulasdecinePosterPlugin() {
        super();
        
        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }
    }

    private String getMovieUrl(String xml) throws IOException {
        String response = Movie.UNKNOWN;

        String searchString = "caratula.php?pel=";
        int beginIndex = xml.indexOf(searchString);
        if (beginIndex > -1) {
            response = new String(xml.substring(beginIndex + searchString.length(), xml.indexOf("\"", beginIndex + searchString.length())));
        }
        return response;
    }

    @Override
    public String getIdFromMovieInfo(String title, String year) {
        String response = Movie.UNKNOWN;
        try {
            StringBuilder sb = new StringBuilder("http://www.google.es/custom?hl=es&domains=caratulasdecine.com&sa=Search&sitesearch=caratulasdecine.com&client=pub-8773978869337108&forid=1&q=");
            sb.append(URLEncoder.encode(title, "ISO-8859-1"));
            String xml = webBrowser.request(sb.toString());
            response = getMovieUrl(xml);

            if (StringTools.isNotValidString(response)) {
                // Did we've a link to the movie list
                String searchString = "http://www.caratulasdecine.com/listado.php";
                int beginIndex = xml.indexOf(searchString);
                if (beginIndex > -1) {
                    String url = new String(xml.substring(beginIndex, xml.indexOf("\"", beginIndex + searchString.length())));
                    // Need to find a better way to do this
                    url = url.replaceAll("&amp;", "&");
                    xml = webBrowser.request(url, Charset.forName("ISO-8859-1"));
                    String sectionStart = " <a class='pag' href='listado.php?";
                    String sectionEnd = "</p>";
                    String extractTag = HTMLTools.extractTag(xml, sectionStart, sectionEnd);// , startTag, endTag);
                    String[] extractTags = extractTag.split("<a class=\"A\"");
                    for (String string : extractTags) {
                        if (string.contains(title)) {
                            response = getMovieUrl(string);
                            break;
                        }
                    }
                }
            }
        } catch (Exception error) {
            logger.error("Failed retreiving CaratulasdecinePoster Id for movie: " + title);
            logger.error("Error : " + error.getMessage());
        }
        return response;
    }

    @Override
    public IImage getPosterUrl(String id) {
        String posterURL = Movie.UNKNOWN;
        if (!Movie.UNKNOWN.equals(id)) {
            try {
                StringBuilder sb = new StringBuilder("http://www.caratulasdecine.com/caratula.php?pel=");
                sb.append(id);

                String xml = webBrowser.request(sb.toString());
                String searchString = "<td><img src=\"";
                int beginIndex = xml.indexOf(searchString);
                if (beginIndex > -1) {
                    posterURL = "http://www.caratulasdecine.com/"
                                    + new String(xml.substring(beginIndex + searchString.length(), xml.indexOf("\"", beginIndex + searchString.length())));
                }

            } catch (Exception e) {
                logger.error("Failed retreiving CaratulasdecinePoster url for movie : " + id);
                logger.error("Error : " + e.getMessage());
            }
        }
        if (!Movie.UNKNOWN.equalsIgnoreCase(posterURL)) {
            return new Image(posterURL);
        }
        return Image.UNKNOWN;
    }

    @Override
    public IImage getPosterUrl(String title, String year) {
        return getPosterUrl(getIdFromMovieInfo(title, year));
    }

    @Override
    public String getName() {
        return "caratulasdecine";
    }
}
