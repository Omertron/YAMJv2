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

import static com.moviejukebox.tools.StringTools.isValidString;
import static com.moviejukebox.tools.StringTools.isNotValidString;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.moviejukebox.tools.FileTools;

/**
 *  This is the new bean for the Person
 *
 *  @author ilgizar
 *  Initial code copied from com.moviejukebox.themoviedb.model.Person
 *
 */
public class Person extends Filmography {

    private static final String UNKNOWN = Movie.UNKNOWN;

    private String  biography             = UNKNOWN;
    private int     version               = -1;
    private int     knownMovies           = -1;
    private String  birthPlace            = UNKNOWN;
    private String  photoURL              = UNKNOWN;
    private String  photoFilename         = UNKNOWN;
    private boolean isDirtyPhoto          = false;
    private int     popularity            = 1;
    private List<Filmography> filmography = new ArrayList<Filmography>();
    private List<String>      aka         = new ArrayList<String>();
    private String  lastModifiedAt;
    private List<Movie> movies            = new ArrayList<Movie>();

    public Person() {
    }

    public Person(Filmography person) {
        setIdMap(person.getIdMap());
        setName(person.getName());
        setDoublage(person.getDoublage());
        setTitle(person.getTitle());
        setFilename(new String(person.getFilename()));
        setJob(person.getJob());
        setCharacter(person.getCharacter());
        setDepartment(person.getDepartment());
        setRating(person.getRating());
        setUrl(person.getUrl());
        setScrapeLibrary(person.isScrapeLibrary());
        setOrder(person.getOrder());
        setCastId(person.getCastId());

        setDirty(person.isDirty());
    }

    public Person(Person person) {
        setIdMap(person.getIdMap());
        setName(person.getName());
        setDoublage(person.getDoublage());
        setTitle(person.getTitle());
        setFilename(new String(person.getFilename()));
        setJob(person.getJob());
        setCharacter(person.getCharacter());
        setDepartment(person.getDepartment());
        setRating(person.getRating());
        setUrl(person.getUrl());
        setScrapeLibrary(person.isScrapeLibrary());
        setOrder(person.getOrder());
        setCastId(person.getCastId());
        setYear(person.getYear());

        setBiography(person.getBiography());
        setVersion(person.getVersion());
        setKnownMovies(person.getKnownMovies());
        setBirthPlace(person.getBirthPlace());
        setPhotoURL(person.getPhotoURL());
        setPhotoFilename(person.getPhotoFilename());
        setDirtyPhoto(person.isDirtyPhoto());
        setPopularity(person.getPopularity());
        setFilmography(person.getFilmography());
        setAka(person.getAka());
        setLastModifiedAt(person.getLastModifiedAt());

        setDirty(person.isDirty());
    }

    public List<String> getAka() {
        return aka;
    }

    public String getBiography() {
        return biography;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public List<Filmography> getFilmography() {
        return filmography;
    }

    public int getKnownMovies() {
        return knownMovies;
    }

    public String getLastModifiedAt() {
        return lastModifiedAt;
    }

    public int getVersion() {
        return version;
    }

    public String getPhotoURL() {
        return photoURL;
    }

    public String getPhotoFilename() {
        return photoFilename;
    }

    public void addAka(String alsoKnownAs) {
        if (isValidString(alsoKnownAs) && !getName().equals(alsoKnownAs) && !getTitle().equals(alsoKnownAs) && !this.aka.contains(alsoKnownAs)) {
            this.aka.add(alsoKnownAs);
            setDirty();
        }
    }

    public void addFilm(Filmography film) {
        if (film != null) {
            this.filmography.add(film);
            setDirty();
        }
    }

    public void setPhotoURL(String URL) {
        if (isValidString(URL) && !photoURL.equalsIgnoreCase(URL)) {
            photoURL = URL;
            setDirty();
        }
    }

    public void setPhotoFilename(String filename) {
        if (isValidString(filename) && !this.photoFilename.equalsIgnoreCase(FileTools.makeSafeFilename(filename))) {
            this.photoFilename = FileTools.makeSafeFilename(filename);
            setDirty();
        }
    }

    public void setPhotoFilename() {
        if (isValidString(getTitle()) && isNotValidString(photoFilename)) {
            setPhotoFilename(getTitle() + ".jpg");
        }
    }

    public void clearPhotoFilename() {
        if (!this.photoFilename.equals(Movie.UNKNOWN)) {
            this.photoFilename = Movie.UNKNOWN;
            setDirty();
        }
    }

    public void setAka(List<String> aka) {
        if (aka != null && !this.aka.equals(aka)) {
            this.aka.clear();
            for (String akaName : aka) {
                addAka(akaName);
            }
            setDirty();
        }
    }

    public void setBiography(String biography) {
        if (isValidString(biography) && !this.biography.equalsIgnoreCase(biography)) {
            this.biography = biography;
            setDirty();
        }
    }

    public void setBirthPlace(String birthPlace) {
        if (isValidString(birthPlace) && !this.birthPlace.equalsIgnoreCase(birthPlace)) {
            this.birthPlace = birthPlace;
            setDirty();
        }
    }

    public void setFilmography(List<Filmography> filmography) {
        if (filmography != null && !this.filmography.equals(filmography)) {
            this.filmography = filmography;
            setDirty();
        }
    }

    public void clearFilmography() {
        filmography.clear();
        setDirty();
    }

    public void setKnownMovies(int knownMovies) {
        if (this.knownMovies != knownMovies) {
            this.knownMovies = knownMovies;
            setDirty();
        }
    }

    public void setLastModifiedAt(String lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public void setLastModifiedAt() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        setLastModifiedAt(sdf.format(Calendar.getInstance().getTime()));
    }

    public void setVersion(int version) {
        if (this.version != version) {
            this.version = version;
            setDirty();
        }
    }

    @Override
    public void setDirty(boolean isDirty) {
        super.setDirty(isDirty);
        setLastModifiedAt();
    }

    public boolean isDirtyPhoto() {
        return isDirtyPhoto;
    }

    public void setDirtyPhoto(boolean isDirty) {
        if (isDirtyPhoto != isDirty) {
            isDirtyPhoto = isDirty;
            setDirty();
        }
    }

    public void setDirtyPhoto() {
        setDirtyPhoto(true);
    }

    public void setPopularity(Integer value) {
        popularity = value;
    }

    public Integer getPopularity() {
        return popularity;
    }

    public void popularityUp() {
        popularity++;
    }

    public void popularityUp(Integer value) {
        popularity += value;
    }

    public void popularityUp(Movie movie) {
        if (movie != null && !movies.contains(movie)) {
            movies.add(movie);
        }
        popularity = movies.size();
    }

    public List<Movie> getMovies() {
        return movies;
    }
}
