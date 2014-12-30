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
package com.moviejukebox.mjbsqldb.dto;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ArtworkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // SQL Definitions
    public static final String TABLE_NAME   = "ARTWORK";
    public static final String TABLE_KEY    = "ID";
    public static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME + " (" +
                "ID integer primary key, " +
                "FILENAME text, " +
                "URL text, " +
                "TYPE text, " +
                "RELATED_ID integer, " +
                "FOREIGN_KEY text" +
                ")";
    public static final String INSERT_TABLE = "insert into " + TABLE_NAME +
                " (ID, FILENAME, URL, TYPE, RELATED_ID, FOREIGN_KEY) values (?, ?, ?, ?, ?, ?)";
    public static final String DROP_TABLE   = "drop table if exists " + TABLE_NAME;

    public static final String TYPE_POSTER = "POSTER";
    public static final String TYPE_PERSON = "PERSON";
    public static final String TYPE_BANNER = "BANNER";
    public static final String TYPE_FANART = "FANART";
    public static final String TYPE_VIDEOIMAGE = "VIDEOIMAGE";

    private int     id;
    private String  filename;
    private String  url;
    private String  type;
    private int     relatedId; // This will be the id of the movie/person that the image corresponds to
    private String  foreignKey;

    public ArtworkDTO() {
        super();
        this.id = 0;    // Set to the default of 0 (zero)
    }

    public ArtworkDTO(int id, String filename, String url, String type, int relatedId, String foreignKey) {
        super();
        this.id = id;
        this.filename = filename;
        this.url = url;
        this.type = type;
        this.relatedId = relatedId;
        this.foreignKey = foreignKey;
    }

    public void populateDTO(ResultSet rs) throws SQLException {
        setId(rs.getInt("ID"));
        setFilename(rs.getString("FILENAME"));
        setUrl(rs.getString("URL"));
        setType(rs.getString("TYPE"));
        setRelatedId(rs.getInt("RELATED_ID"));
        setForeignKey(rs.getString("FOREIGN_KEY"));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getForeignKey() {
        return foreignKey;
    }

    public void setForeignKey(String foreignKey) {
        this.foreignKey = foreignKey;
    }

    @Override
    public String toString() {
        return "ArtworkDTO [id=" + id + ", filename=" + filename + ", url=" + url + ", type=" + type + ", videoId=" + relatedId + ", foreignKey=" + foreignKey
                        + "]";
    }

    public int getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(int relatedId) {
        this.relatedId = relatedId;
    }

}
