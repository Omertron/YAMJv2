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
package com.moviejukebox.tools;

import static com.moviejukebox.tools.PropertiesUtil.getProperty;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.moviejukebox.MovieJukebox;
import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.MediaLibraryPath;
import com.moviejukebox.model.Movie;

/**
 * Save a pre-defined list of attributes of the jukebox and properties 
 * for use in subsequent processing runs to determine if an attribute
 * has changed and force a rescan of the appropriate data 
 * @author stuart.boston
 *
 */
public class JukeboxProperties {
    private final static Logger logger = Logger.getLogger("moviejukebox");
    private final static Collection<PropertyInformation> propInfo = new ArrayList<PropertyInformation>();
    private final static String JUKEBOX = "jukebox";
    private final static String PROPERTIES = "properties";
    
    static {
        // Set up the properties to watch:                                          xml           thumbnail     fanart        videoimage    trailers
        //                                                                                 html          poster        banner        index
        propInfo.add(new PropertyInformation("userPropertiesName",                  false, false, false, false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("mjb.skin.dir",                        false, true,  true,  true,  false, false, false, true,  false));
        propInfo.add(new PropertyInformation("fanart.movie.download",               false, false, false, false, true,  false, false, false, false));
        propInfo.add(new PropertyInformation("fanart.tv.download",                  false, false, false, false, true,  false, false, false, false));
        
        propInfo.add(new PropertyInformation("mjb.includeEpisodePlots",             true,  false, false, false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("mjb.includeVideoImages",              true,  false, false, false, false, false, true,  false, false));
        propInfo.add(new PropertyInformation("mjb.includeWideBanners",              false, false, false, false, false, true,  false, false, false));
        propInfo.add(new PropertyInformation("filename.scanner.skip.episodeTitle",  true,  true,  false, false, false, false, false, false, false));
        
        propInfo.add(new PropertyInformation("mjb.nbThumbnailsPerPage",             false, true,  true,  false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.nbThumbnailsPerLine",             false, true,  true,  false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.nbTvThumbnailsPerPage",           false, true,  true,  false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.nbTvThumbnailsPerLine",           false, true,  true,  false, false, false, false, true,  false));

        propInfo.add(new PropertyInformation("mjb.categories.minCount",             false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Other",       false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Genres",      false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Title",       false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Rating",      false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Year",        false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Library",     false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Set",         false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Cast",        false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Director",    false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Writer",      false, false, false, false, false, false, false, true,  false));
        propInfo.add(new PropertyInformation("mjb.categories.minCount.Country",     false, false, false, false, false, false, false, true,  false));

        propInfo.add(new PropertyInformation("thumbnails.width",                    false, true,  true,  false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("thumbnails.height",                   false, true,  true,  false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("thumbnails.logoHD",                   false, true,  true,  false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("thumbnails.logoTV",                   false, true,  true,  false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("thumbnails.logoSet",                  false, true,  true,  false, false, false, false, false, false));
        propInfo.add(new PropertyInformation("thumbnails.language",                 false, true,  true,  false, false, false, false, false, false));
        
        propInfo.add(new PropertyInformation("posters.width",                       false, true,  false, true,  false, false, false, false, false));
        propInfo.add(new PropertyInformation("posters.height",                      false, true,  false, true,  false, false, false, false, false));
        propInfo.add(new PropertyInformation("posters.logoHD",                      false, true,  false, true,  false, false, false, false, false));
        propInfo.add(new PropertyInformation("posters.logoTV",                      false, true,  false, true,  false, false, false, false, false));
        propInfo.add(new PropertyInformation("posters.language",                    false, true,  false, true,  false, false, false, false, false));

        propInfo.add(new PropertyInformation("trailers.rescan.days",                false, false, false, false, false, false, false, false, true));
    }

    /**
     * Check to see if the file needs to be processed (if it exists) or just created
     * Note: This *MIGHT* cause issues with some programs that assume all XML files in the jukebox folder are
     * videos or indexes. However, they should just deal with this themselves :-)
     * 
     * @param jukebox
     * @param mediaLibraryPaths 
     */
    public static void readDetailsFile(Jukebox jukebox, Collection<MediaLibraryPath> mediaLibraryPaths) {
        boolean monitor = PropertiesUtil.getBooleanProperty("mjb.monitorJukeboxProperties", "false"); 
        
        // Read the mjbDetails file that stores the jukebox properties we want to watch
        File mjbDetails = new File(jukebox.getJukeboxRootLocationDetailsFile(), "jukebox_details.xml");
        FileTools.addJukeboxFile(mjbDetails.getName());
        try {
            // If we are monitoring the file and it exists, then read and check, otherwise create the file
            if (monitor && mjbDetails.exists()) {
                PropertyInformation pi = processFile(mjbDetails, mediaLibraryPaths);
                
                if (pi.isBannerOverwrite()) {
                    logger.debug("Setting 'forceBannerOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceBannersOverwrite", "true");
                }
                
                if (pi.isFanartOverwrite()) {
                    logger.debug("Setting 'forceFanartOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceFanartOverwrite", "true");
                }
                
                if (pi.isHtmlOverwrite()) {
                    logger.debug("Setting 'forceHtmlOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceHtmlOverwrite", "true");
                }
                
                if (pi.isPosterOverwrite()) {
                    logger.debug("Setting 'forcePosterOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forcePosterOverwrite", "true");
                }
                
                if (pi.isThumbnailOverwrite()) {
                    logger.debug("Setting 'forceThumbnailOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceThumbnailOverwrite", "true");
                }
                
                if (pi.isVideoimageOverwrite()) {
                    logger.debug("Setting 'forceVideoimageOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceVideoimageOverwrite", "true");
                }
                
                if (pi.isXmlOverwrite()) {
                    logger.debug("Setting 'forceXmlOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceXmlOverwrite", "true");
                }
                
                if (pi.isIndexOverwrite()) {
                    logger.debug("Setting 'forceIndexOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceIndexOverwrite", "true");
                }

                if (pi.isTrailersOverwrite()) {
                    logger.debug("Setting 'forceTrailersOverwrite = true' due to property file changes");
                    PropertiesUtil.setProperty("mjb.forceTrailersOverwrite", "true");
                }
            }
        } catch (Exception error) {
            final Writer eResult = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(eResult);
            error.printStackTrace(printWriter);
            logger.error("Failed creating " + mjbDetails.getName() + " file!");
            logger.error(eResult.toString());
        }
        return;
    }

    /**
     * Create the mjbDetails file and populate with the attributes
     * @param mjbDetails
     * @param jukebox
     */
    public static void writeFile(Jukebox jukebox, Collection<MediaLibraryPath> mediaLibraryPaths) {
        File mjbDetails = new File(jukebox.getJukeboxRootLocationDetailsFile(), "jukebox_details.xml");
        FileTools.addJukeboxFile(mjbDetails.getName());

        Document docMjbDetails;
        Element eRoot, eJukebox, eProperties;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-kk:mm:ss");
        
        try {
            logger.debug("Creating JukeboxProperties file: " + mjbDetails.getAbsolutePath());
            if (mjbDetails.exists() && !mjbDetails.delete()) {
                logger.error("JukeboxProperties: Failed to delete " + mjbDetails.getName() + ". Please make sure it's not read only");
                return;
            }
        } catch (Exception error) {
            logger.error("JukeboxProperties: Failed to create/delete " + mjbDetails.getName() + ". Please make sure it's not read only");
            return;
        }
        
        try {
            // Start with a blank document
            docMjbDetails = DOMHelper.createDocument();
            docMjbDetails.appendChild(docMjbDetails.createComment("This file was created on: " + dateFormat.format(System.currentTimeMillis())));
            
            //create the root element and add it to the document
            eRoot = docMjbDetails.createElement("root");
            docMjbDetails.appendChild(eRoot);
            
            //create child element, add an attribute, and add to root
            eJukebox = docMjbDetails.createElement(JUKEBOX);
            eRoot.appendChild(eJukebox);

            // Jukebox version
            String specificationVersion = MovieJukebox.mjbVersion;
            if (specificationVersion == null) {
                specificationVersion = Movie.UNKNOWN;
            }
            DOMHelper.appendChild(docMjbDetails, eJukebox, "JukeboxVersion", specificationVersion);

            // Jukebox revision
            // If YAMJ is self compiled then the revision information may not exist.
            String currentRevision = MovieJukebox.mjbRevision;
            // If YAMJ is self compiled then the revision information may not exist.
            if ((currentRevision == null) || (currentRevision.equalsIgnoreCase("${env.SVN_REVISION}"))) {
                currentRevision = Movie.UNKNOWN;
            }
            DOMHelper.appendChild(docMjbDetails, eJukebox, "JukeboxRevision", currentRevision);

            // Save the run date
            DOMHelper.appendChild(docMjbDetails, eJukebox, "RunTime", dateFormat.format(System.currentTimeMillis()));
            
            // Save the details directory name
            DOMHelper.appendChild(docMjbDetails, eJukebox, "DetailsDirName", jukebox.getDetailsDirName());
            
            // Save the jukebox location
            DOMHelper.appendChild(docMjbDetails, eJukebox, "JukeboxLocation", jukebox.getJukeboxRootLocation());
            
            // Save the root index filename
            DOMHelper.appendChild(docMjbDetails, eJukebox, "indexFile", getProperty("mjb.indexFile", "index.htm"));
            
            // Save the library paths. This isn't very accurate, any change to this file will cause the jukebox to be rebuilt
            DOMHelper.appendChild(docMjbDetails, eJukebox, "LibraryPath", mediaLibraryPaths.toString());

            eProperties = docMjbDetails.createElement(PROPERTIES);
            eRoot.appendChild(eProperties);
            
            Iterator<PropertyInformation> iterator = propInfo.iterator();
            while (iterator.hasNext()) {
                appendProperty(docMjbDetails, eProperties, iterator.next().getPropertyName());
            }
            
            DOMHelper.writeDocumentToFile(docMjbDetails, mjbDetails.getAbsolutePath());
        } catch (Exception error) {
            logger.error("JukeboxProperties: Error creating " + mjbDetails.getName() + " file");
            error.printStackTrace();
        }
    }
    
    /**
     * Read the attributes from the file and compare and set any force overwrites needed
     * @param mjbDetails
     * @param mediaLibraryPaths 
     * @return PropertyInformation Containing the merged overwrite values
     */
    public static PropertyInformation processFile(File mjbDetails, Collection<MediaLibraryPath> mediaLibraryPaths) {
        PropertyInformation piReturn = new PropertyInformation("RETURN", false, false, false, false, false, false, false, false, false);
        Document docMjbDetails;
        // Try to open and read the document file
        try {
            docMjbDetails = DOMHelper.getEventDocFromUrl(mjbDetails);
        } catch (Exception error) {
            logger.error("JukeboxProperties: Failed creating the file, no checks performed");
            logger.error(error.getMessage());
            error.getStackTrace();
            return piReturn;
        }
        
        NodeList nlElements;
        Node nDetails;
        
        nlElements = docMjbDetails.getElementsByTagName(JUKEBOX);
        nDetails = nlElements.item(0);
        
        if (nDetails.getNodeType() == Node.ELEMENT_NODE) {
            Element eJukebox = (Element) nDetails;
            // logger.fine("DetailsDirName : " + DOMHelper.getValueFromElement(eJukebox, "DetailsDirName"));
            // logger.fine("JukeboxLocation: " + DOMHelper.getValueFromElement(eJukebox, "JukeboxLocation"));
            
            // Check the library file
            String mlp = DOMHelper.getValueFromElement(eJukebox, "LibraryPath");
            if (!mediaLibraryPaths.toString().equalsIgnoreCase(mlp)) {
                // Overwrite the indexes only.
                piReturn.mergePropertyInformation(new PropertyInformation("LibraryPath", false, false, false, false, false, false, false, true, false));
            }
        }
        
        nlElements = docMjbDetails.getElementsByTagName(PROPERTIES);
        nDetails = nlElements.item(0);
        
        if (nDetails == null) {
            // Just return the property info file as is.
            return piReturn;
        }
        
        if (nDetails.getNodeType() == Node.ELEMENT_NODE) {
            Element eJukebox = (Element) nDetails;
            String propName, propValue, propCurrent;
            
            Iterator<PropertyInformation> iterator = propInfo.iterator();
            while (iterator.hasNext()) {
                PropertyInformation pi = iterator.next();
                propName = pi.getPropertyName();
                propValue = DOMHelper.getValueFromElement(eJukebox, propName);
                propCurrent = PropertiesUtil.getProperty(propName, "");
                
                if (!propValue.equalsIgnoreCase(propCurrent)) {
                    // Update the return value with the information from this property
                    piReturn.mergePropertyInformation(pi);
                }
            }
        }
        
        logger.debug("JukeboxProperties: Returning: " + piReturn.toString());
        return piReturn;
    }
    
    /**
     * Helper function to write out the property to the DOM document & Element
     * @param doc
     * @param element
     * @param propertyName
     */
    private static void appendProperty(Document doc, Element element, String propertyName) {
        String propValue = PropertiesUtil.getProperty(propertyName);
        
        // Only write valid values
        if (StringTools.isValidString(propValue)) {
            DOMHelper.appendChild(doc, element, propertyName, propValue);
        }
    }

    /**
     * Class to define the property name and the impact on each of the overwrite flags.
     * If the 
     * @author stuart.boston
     *
     */
    public static class PropertyInformation {
        private String  propertyName        = Movie.UNKNOWN;
        private boolean xmlOverwrite        = false;
        private boolean htmlOverwrite       = false;
        private boolean thumbnailOverwrite  = false;
        private boolean posterOverwrite     = false;
        private boolean fanartOverwrite     = false;
        private boolean bannerOverwrite     = false;
        private boolean videoimageOverwrite = false;
        private boolean indexOverwrite      = false;
        private boolean trailersOverwrite   = false;
        
        public PropertyInformation(String property,
                                    boolean xml,
                                    boolean html,
                                    boolean thumbnail, 
                                    boolean poster,
                                    boolean fanart,
                                    boolean banner,
                                    boolean videoimage,
                                    boolean index,
                                    boolean trailers) {
            this.propertyName        = property;
            this.xmlOverwrite        = xml;
            this.htmlOverwrite       = html;
            this.thumbnailOverwrite  = thumbnail;
            this.posterOverwrite     = poster;
            this.fanartOverwrite     = fanart;
            this.bannerOverwrite     = banner;
            this.videoimageOverwrite = videoimage;
            this.indexOverwrite      = index;
            this.trailersOverwrite   = trailers;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isXmlOverwrite() {
            return xmlOverwrite;
        }

        public boolean isHtmlOverwrite() {
            return htmlOverwrite;
        }

        public boolean isThumbnailOverwrite() {
            return thumbnailOverwrite;
        }

        public boolean isPosterOverwrite() {
            return posterOverwrite;
        }

        public boolean isFanartOverwrite() {
            return fanartOverwrite;
        }

        public boolean isBannerOverwrite() {
            return bannerOverwrite;
        }

        public boolean isTrailersOverwrite() {
            return trailersOverwrite;
        }

        public boolean isVideoimageOverwrite() {
            return videoimageOverwrite;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public void setXmlOverwrite(boolean xmlOverwrite) {
            this.xmlOverwrite = xmlOverwrite;
        }

        public void setHtmlOverwrite(boolean htmlOverwrite) {
            this.htmlOverwrite = htmlOverwrite;
        }

        public void setThumbnailOverwrite(boolean thumbnailOverwrite) {
            this.thumbnailOverwrite = thumbnailOverwrite;
        }

        public void setPosterOverwrite(boolean posterOverwrite) {
            this.posterOverwrite = posterOverwrite;
        }

        public void setFanartOverwrite(boolean fanartOverwrite) {
            this.fanartOverwrite = fanartOverwrite;
        }

        public void setBannerOverwrite(boolean bannerOverwrite) {
            this.bannerOverwrite = bannerOverwrite;
        }

        public void setTrailersOverwrite(boolean trailersOverwrite) {
            this.trailersOverwrite = trailersOverwrite;
        }

        public void setVideoimagesOverwrite(boolean videoimageOverwrite) {
            this.videoimageOverwrite = videoimageOverwrite;
        }
        
        /**
         * Merge two PropertyInformation objects. Sets the overwrite flags to true.
         * @param newPI
         */
        public void mergePropertyInformation(PropertyInformation newPI) {
            this.xmlOverwrite        = xmlOverwrite        || newPI.isXmlOverwrite();
            this.htmlOverwrite       = htmlOverwrite       || newPI.isHtmlOverwrite();
            this.thumbnailOverwrite  = thumbnailOverwrite  || newPI.isThumbnailOverwrite();
            this.posterOverwrite     = posterOverwrite     || newPI.isPosterOverwrite();
            this.fanartOverwrite     = fanartOverwrite     || newPI.isFanartOverwrite();
            this.bannerOverwrite     = bannerOverwrite     || newPI.isBannerOverwrite();
            this.videoimageOverwrite = videoimageOverwrite || newPI.isVideoimageOverwrite();
            this.indexOverwrite      = indexOverwrite      || newPI.isIndexOverwrite();
            this.trailersOverwrite   = trailersOverwrite   || newPI.isTrailersOverwrite();
        }
        
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("Name: ");
            sb.append(getPropertyName());
            sb.append(", xmlOverwrite: ");
            sb.append(isXmlOverwrite());
            sb.append(", htmlOverwrite: ");
            sb.append(isHtmlOverwrite());
            sb.append(", thumbnailOverwrite: ");
            sb.append(isThumbnailOverwrite());
            sb.append(", posterOverwrite: ");
            sb.append(isPosterOverwrite());
            sb.append(", fanartOverwrite: ");
            sb.append(isFanartOverwrite());
            sb.append(", bannerOverwrite: ");
            sb.append(isBannerOverwrite());
            sb.append(", videoimageOverwrite: ");
            sb.append(isVideoimageOverwrite());
            sb.append(", indexOverwrite: ");
            sb.append(isIndexOverwrite());
            sb.append(", trailersOverwrite: ");
            sb.append(isTrailersOverwrite());
            return sb.toString();
        }

        public boolean isIndexOverwrite() {
            return indexOverwrite;
        }

        public void setVideoimageOverwrite(boolean videoimageOverwrite) {
            this.videoimageOverwrite = videoimageOverwrite;
        }

        public void setIndexOverwrite(boolean indexOverwrite) {
            this.indexOverwrite = indexOverwrite;
        }
    }
    

}
