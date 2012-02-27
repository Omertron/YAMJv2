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

package com.moviejukebox.allocine;

import com.moviejukebox.allocine.jaxb.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

/**
 *  This is the Movie Search bean for the api.allocine.fr search
 *
 *  @author Yves.Blusseau
 */

public class MovieInfos extends Movie {

    private static final Pattern AGE_REGEXP             = Pattern.compile("\\s(\\d{1,2})\\san");
    private Set<String>          actors;
    private Set<String>          writers;
    private Set<String>          directors;
    private Set<String>          posterURLS;

    // Constants
    private static final int     ACTOR_ACTIVITY_CODE    = 8001;
    private static final int     DIRECTOR_ACTIVITY_CODE = 8002;
    private static final int     WRITER_ACTIVITY_CODE   = 8004;
    private static final int     SCRIPT_ACTIVITY_CODE   = 8043;

    private static final int     POSTER_MEDIA_CODE      = 31001;

    public MovieInfos() {
        setCode(-1); // Mark the object as invalid
    }

    public boolean isValid() {
        return getCode() > -1 ? true : false;
    }

    public boolean isNotValid() {
        return getCode() > -1 ? false : true;
    }

    public final String getSynopsis() {
        String synopsis = "";
        HtmlSynopsisType htmlSynopsis = getHtmlSynopsis();
        if (htmlSynopsis != null) {
            for (Object obj : getHtmlSynopsis().getContent()) {
                String str = "";
                if (obj instanceof String) {
                    str = (String) obj;
                } else if (obj instanceof Element) {
                    Element element = (Element) obj;
                    str = element.getTextContent();
                }
                synopsis = synopsis.concat(str);
            }
        }
        // Normalize the string (remove LF and collapse WhiteSpaces)
        synopsis = synopsis.replaceAll("\\r+", "\n").replaceAll("\\n+", " ").replaceAll("\\s+", " ").trim();
        return synopsis;
    }

    public final int getRating() {
        float note   = 0;
        int   sum    = 0;
        int   result = -1;

        if (getStatistics() != null ) {
            for ( RatingType rating : getStatistics().getRatingStats() ) {
                int count = rating.getValue();
                note += rating.getNote() * count;
                sum  += count;
            }
            if (sum > 0) {
                result = (int) ((note / sum) / 5.0 * 100);
            }
        }
        return result;
    }

    public final String getCertification() {
        String certification = "All"; // Default value
        if (!StringUtils.isBlank(getMovieCertificate())) {
            Matcher match    = AGE_REGEXP.matcher(getMovieCertificate());
            if (match.find()) {
                certification=match.group(1);
            }
        }
        return certification;
    }

    protected final void parseCasting() {
        if (actors == null) {
            actors = new LinkedHashSet<String>();
        }
        if (writers == null) {
            writers = new LinkedHashSet<String>();
        }
        if (directors == null) {
            directors = new LinkedHashSet<String>();
        }
        LinkedHashSet<String> scripts = new LinkedHashSet<String>();

        for (CastMember member : getCasting()) {
            if (member.getActivity().getCode() == ACTOR_ACTIVITY_CODE) {
                actors.add(member.getPerson().getName());
            } else if (member.getActivity().getCode() == DIRECTOR_ACTIVITY_CODE) {
                directors.add(member.getPerson().getName());
            } else if (member.getActivity().getCode() == WRITER_ACTIVITY_CODE) {
                writers.add(member.getPerson().getName());
            } else if (member.getActivity().getCode() == SCRIPT_ACTIVITY_CODE) {
                scripts.add(member.getPerson().getName());
            }
        }
        // Add scripts to writers
        writers.addAll(scripts);
    }

    public final Set<String> getActors() {
        if (actors == null) {
            parseCasting();
        }
        return actors;
    }

    public final Set<String> getDirectors() {
        if (directors == null) {
            parseCasting();
        }
        return directors;
    }

    public final Set<String> getWriters() {
        if (writers == null) {
            parseCasting();
        }
        return writers;
    }

    protected final void parseMediaList() {
        if (posterURLS == null) {
            posterURLS = new LinkedHashSet<String>();
        }

        for (Media media : getMediaList()) {
            if (media.getType().getCode() == POSTER_MEDIA_CODE) {
                posterURLS.add(media.getThumbnail().getHref());
            }
        }
    }

    public final Set<String> getPosterUrls() {
        if (posterURLS == null) {
            parseMediaList();
        }
        return posterURLS;
    }
}
