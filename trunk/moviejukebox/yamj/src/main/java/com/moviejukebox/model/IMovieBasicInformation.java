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
package com.moviejukebox.model;

import com.moviejukebox.tools.BooleanYesNoAdapter;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

public interface IMovieBasicInformation {

    public abstract String getBaseName();

    public abstract String getLanguage();

    public abstract int getSeason();

    public abstract String getTitle();

    public abstract String getTitleSort();

    public abstract String getOriginalTitle();

    public abstract String getYear();

    @XmlAttribute(name = "isTV")
    public abstract boolean isTVShow();

    @XmlJavaTypeAdapter(BooleanYesNoAdapter.class)
    public abstract Boolean isTrailerExchange();

    @XmlAttribute(name = "isSet")
    public abstract boolean isSetMaster();

}