/*
 *      Copyright (c) 2004-2010 YAMJ Members
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

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.apache.xmlrpc.XmlRpcException;

import com.moviejukebox.model.IImage;
import com.moviejukebox.model.Image;
import com.moviejukebox.model.Movie;
import com.moviejukebox.plugin.MovieMeterPluginSession;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.WebBrowser;

public class MovieMeterPosterPlugin extends AbstractMoviePosterPlugin {
    private static Logger logger = Logger.getLogger("moviejukebox");

    private WebBrowser webBrowser;

    private String preferredSearchEngine;
    private MovieMeterPluginSession session;

    public MovieMeterPosterPlugin() {
        super();
        
        // Check to see if we are needed
        if (!isNeeded()) {
            return;
        }
        
        webBrowser = new WebBrowser();
        preferredSearchEngine = PropertiesUtil.getProperty("moviemeter.id.search", "moviemeter");
        try {
            session = new MovieMeterPluginSession();
        } catch (XmlRpcException error) {
            //final Writer eResult = new StringWriter();
            //final PrintWriter printWriter = new PrintWriter(eResult);
            //error.printStackTrace(printWriter);
            //logger.severe(eResult.toString());
            logger.finer("MovieMeterPosterPlugin: Failed to create session");
        }
    }

    private String getMovieMeterIdFromGoogle(String movieName, String year) {
        try {
            StringBuffer sb = new StringBuffer("http://www.google.nl/search?hl=nl&q=");
            sb.append(URLEncoder.encode(movieName, "UTF-8"));

            if (year != null && !year.equalsIgnoreCase(Movie.UNKNOWN)) {
                sb.append("+%28").append(year).append("%29");
            }

            sb.append("+site%3Amoviemeter.nl/film");

            String xml = webBrowser.request(sb.toString());
            int beginIndex = xml.indexOf("www.moviemeter.nl/film/");
            StringTokenizer st = new StringTokenizer(xml.substring(beginIndex + 23), "/\"");
            String moviemeterId = st.nextToken();

            if (isInteger(moviemeterId)) {
                return moviemeterId;
            } else {
                return Movie.UNKNOWN;
            }

        } catch (Exception error) {
            logger.severe("Failed retreiving moviemeter Id from Google for movie : " + movieName);
            logger.severe("Error : " + error.getMessage());
            return Movie.UNKNOWN;
        }
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public String getIdFromMovieInfo(String title, String year) {
        String response = Movie.UNKNOWN;
        try {
            logger.finest("Preferred search engine for moviemeter id: " + preferredSearchEngine);
            if ("google".equalsIgnoreCase(preferredSearchEngine)) {
                // Get moviemeter website from google
                logger.finest("Searching google.nl to get moviemeter.nl id");
                response = getMovieMeterIdFromGoogle(title, year);
                logger.finest("Returned id: " + response);
            } else if ("none".equalsIgnoreCase(preferredSearchEngine)) {
                response = Movie.UNKNOWN;
            } else {
                logger.finest("Searching moviemeter.nl for title: " + title);

                HashMap filmInfo = session.getMovieDetailsByTitleAndYear(title, year);
                response = filmInfo.get("filmId").toString();
            }

        } catch (Exception e) {
            logger.severe("Failed retreiving CaratulasdecinePoster Id for movie : " + title);
            logger.severe("Error : " + e.getMessage());
        }
        return response;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public IImage getPosterUrl(String id) {
        // <td><img src="
        String posterURL = Movie.UNKNOWN;
        if (!Movie.UNKNOWN.equals(id)) {
            try {
                HashMap filmInfo = session.getMovieDetailsById(Integer.valueOf(id));
                posterURL = filmInfo.get("thumbnail").toString().replaceAll("thumbs/", "");

            } catch (Exception e) {
                logger.severe("Failed retreiving CaratulasdecinePoster url for movie : " + id);
                logger.severe("Error : " + e.getMessage());
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
        return "movieposter";
    }
}
