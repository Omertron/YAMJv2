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
package com.moviejukebox.writer;

import com.moviejukebox.model.DirtyFlag;
import com.moviejukebox.model.IndexInfo;
import com.moviejukebox.model.Jukebox;
import com.moviejukebox.model.Library;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.Person;
import com.moviejukebox.tools.DOMHelper;
import com.moviejukebox.tools.FileTools;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;
import com.moviejukebox.tools.SkinProperties;
import com.moviejukebox.tools.StringTools;
import com.moviejukebox.tools.SystemTools;
import com.moviejukebox.tools.ThreadExecutor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Generate HTML pages from XML movies and indexes
 *
 * @author Julien
 * @author artem.gratchev
 */
public class MovieJukeboxHTMLWriter {

    private static final Logger LOG = Logger.getLogger(MovieJukeboxHTMLWriter.class);
    private static final String LOG_MESSAGE = "HTMLWriter: ";
    private static final String EXT_XML = ".xml";
    private static final String EXT_HTML = ".html";
    private static final String EXT_XSL = ".xsl";
    private final boolean forceHTMLOverwrite = PropertiesUtil.getBooleanProperty("mjb.forceHTMLOverwrite", Boolean.FALSE);
    private String peopleFolder;
    private static final String SKIN_HOME = SkinProperties.getSkinHome();
    private static final TransformerFactory TRANSFORMER = TransformerFactory.newInstance();
    private static final String PLAYLIST_IGNORE_EXT = PropertiesUtil.getProperty("mjb.playlist.IgnoreExtensions", "iso,img");
    private static final File PLAYLIST_FILE = new File("playlist.xsl");
    private static final String INDEX_HTML_FILE = "../" + PropertiesUtil.getProperty("mjb.indexFile", "index.htm");
    private static final String MYIHOME_IP = PropertiesUtil.getProperty("mjb.myiHome.IP", "");
    private static final boolean GENERATE_MULTIPART_PLAYLIST = PropertiesUtil.getBooleanProperty("mjb.playlist.generateMultiPart", Boolean.TRUE);
    private static final int MAX_RETRY_COUNT = 3;   // The number of times to retry writing a HTML page

    public MovieJukeboxHTMLWriter() {

        // Issue 1947: Cast enhancement - option to save all related files to a specific folder
        peopleFolder = PropertiesUtil.getProperty("mjb.people.folder", "");
        if (StringTools.isNotValidString(peopleFolder)) {
            peopleFolder = "";
        } else if (!peopleFolder.endsWith(File.separator)) {
            peopleFolder += File.separator;
        }

        // Issue 310
        String transformerFactoryName = PropertiesUtil.getProperty("javax.xml.transform.TransformerFactory", null);
        if (transformerFactoryName != null) {
            System.setProperty("javax.xml.transform.TransformerFactory", transformerFactoryName);
        }
    }

    /**
     * Generate the HTML for the movie details
     *
     * @param jukebox
     * @param movie
     */
    public void generateMovieDetailsHTML(Jukebox jukebox, Movie movie) {
        try {
            String baseName = movie.getBaseName();
            String tempFilename = StringTools.appendToPath(jukebox.getJukeboxTempLocationDetails(), baseName);
            File tempXmlFile = new File(tempFilename + EXT_XML);
            File oldXmlFile = FileTools.fileCache.getFile(jukebox.getJukeboxRootLocationDetails() + File.separator + baseName + EXT_XML);

            FileTools.addJukeboxFile(baseName + EXT_XML);
            String indexList = PropertiesUtil.getProperty("mjb.view.detailList", "detail.xsl");
            for (final String indexStr : indexList.split(",")) {
                String suffix = "";
                if (!indexStr.equals("detail.xsl")) {
                    suffix = indexStr.replace("detail", "").replace(EXT_XSL, "");
                }

                File finalHtmlFile = FileTools.fileCache.getFile(jukebox.getJukeboxRootLocationDetails() + File.separator + baseName + suffix + EXT_HTML);
                File tempHtmlFile = new File(tempFilename + suffix + EXT_HTML);
                Source xmlSource;

                FileTools.addJukeboxFile(baseName + suffix + EXT_HTML);

                if (!finalHtmlFile.exists() || forceHTMLOverwrite || movie.isDirty(DirtyFlag.INFO) || movie.isDirty(DirtyFlag.WATCHED)) {

                    // Issue 216: If the HTML is deleted the generation fails because it looks in the temp directory and not
                    // the original source directory
                    if (tempXmlFile.exists()) {
                        // Use the temp file
                        xmlSource = new StreamSource(tempXmlFile);
                    } else {
                        // Use the file in the original directory
                        xmlSource = new StreamSource(oldXmlFile);
                    }
                    Result xmlResult = new StreamResult(tempHtmlFile);

                    if (xmlSource != null && xmlResult != null) {
                        File skinFile = new File(SKIN_HOME, indexStr);
                        Transformer transformer = getTransformer(skinFile, jukebox.getJukeboxRootLocationDetails());

                        doTransform(transformer, xmlSource, xmlResult, "Movie: " + movie.getBaseFilename());
                    } else {
                        LOG.error(LOG_MESSAGE + "Unable to transform XML for video " + movie.getBaseFilename() + " source: " + (xmlSource == null ? true : false)
                                + " result: " + (xmlResult == null ? true : false));
                    }
                }
            }
        } catch (Exception error) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML for movie " + movie.getBaseFilename());
            LOG.error(SystemTools.getStackTrace(error));
        }
    }

    /**
     * Generate HTML person details
     *
     * @param jukebox
     * @param person
     */
    public void generatePersonDetailsHTML(Jukebox jukebox, Person person) {
        try {
            String baseName = person.getFilename();
            String tempFilename = jukebox.getJukeboxTempLocationDetails() + File.separator + peopleFolder + baseName;
            File tempXmlFile = new File(tempFilename + EXT_XML);
            File oldXmlFile = FileTools.fileCache.getFile(jukebox.getJukeboxRootLocationDetails() + File.separator + peopleFolder + baseName + EXT_XML);
            FileTools.makeDirsForFile(tempXmlFile);

            FileTools.addJukeboxFile(baseName + EXT_XML);
            String indexList = PropertiesUtil.getProperty("mjb.view.personList", "people.xsl");
            for (final String indexStr : indexList.split(",")) {
                String suffix = "";
                if (!indexStr.equals("people.xsl")) {
                    suffix = indexStr.replace("people", "").replace(EXT_XSL, "");
                }

                File finalHtmlFile = FileTools.fileCache.getFile(jukebox.getJukeboxRootLocationDetails() + File.separator + peopleFolder + baseName + suffix + EXT_HTML);
                File tempHtmlFile = new File(tempFilename + suffix + EXT_HTML);
                Source xmlSource;

                FileTools.addJukeboxFile(baseName + suffix + EXT_HTML);

                if (!finalHtmlFile.exists() || forceHTMLOverwrite || person.isDirty()) {
                    if (tempXmlFile.exists()) {
                        // Use the temp file
                        xmlSource = new StreamSource(tempXmlFile);
                    } else {
                        // Use the file in the original directory
                        xmlSource = new StreamSource(oldXmlFile);
                    }
                    Result xmlResult = new StreamResult(tempHtmlFile);

                    if (xmlSource != null && xmlResult != null) {
                        File skinFile = new File(SKIN_HOME, indexStr);
                        if (skinFile.exists()) {
                            Transformer transformer = getTransformer(skinFile, jukebox.getJukeboxRootLocationDetails());
                            doTransform(transformer, xmlSource, xmlResult, "Person: " + person.getName());
                        } else {
                            LOG.warn(LOG_MESSAGE + "XSL file '" + skinFile.getAbsolutePath() + "' does not exist!");
                        }
                    } else {
                        LOG.error(LOG_MESSAGE + "Unable to transform XML for person " + person.getName() + " source: " + (xmlSource == null ? true : false)
                                + " result: " + (xmlResult == null ? true : false));
                    }
                }
            }
        } catch (Exception error) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML for person " + person.getName());
            LOG.error(SystemTools.getStackTrace(error));
        }
    }

    /**
     * Generates a playlist per part of the video. Used primarily with TV Series
     *
     * @param jukebox
     * @param movie
     * @return List of generated file names
     */
    public Collection<String> generatePlaylist(Jukebox jukebox, Movie movie) {
        Collection<String> fileNames = new ArrayList<String>();

        if (PLAYLIST_FILE == null) {
            return fileNames;
        }

        MovieFile[] movieFileArray = movie.getFiles().toArray(new MovieFile[movie.getFiles().size()]);

        try {
            String baseName = movie.getBaseName();
            String tempFilename = jukebox.getJukeboxTempLocationDetails() + File.separator + baseName;
            File tempXmlFile = new File(tempFilename + EXT_XML);
            File oldXmlFile = new File(jukebox.getJukeboxRootLocationDetails() + File.separator + baseName + EXT_XML);
            final String filenameSuffix = ".playlist.jsp";
            File finalPlaylistFile = new File(jukebox.getJukeboxRootLocationDetails() + File.separator + baseName + filenameSuffix);
            File tempPlaylistFile = new File(tempFilename + filenameSuffix);
            Source xmlSource;

            fileNames.add(baseName + filenameSuffix);

            // Issue 884: Remove ISO and IMG files from playlists
            int partCount = 0;
            for (MovieFile moviePart : movieFileArray) {
                String partExt = moviePart.getFilename().substring(moviePart.getFilename().lastIndexOf(".") + 1);
                if (PLAYLIST_IGNORE_EXT.indexOf(partExt) > -1) {
                    partCount++;
                }
            }
            if (partCount > 0) {
                // Note this will skip playlist generation for any movie that has an "mjb.playlist.ignoreextensions" entry.
                LOG.debug(LOG_MESSAGE + "Playlist for " + movie.getTitle() + " skipped - All parts are in mjb.playlist.IgnoreExtensions");
                return fileNames;
            } // Issue 884

            if (!finalPlaylistFile.exists() || forceHTMLOverwrite || movie.isDirty(DirtyFlag.INFO)) {
                FileTools.makeDirsForFile(tempPlaylistFile);

                Transformer transformer = getTransformer(PLAYLIST_FILE, jukebox.getJukeboxRootLocationDetails());

                if (tempXmlFile.exists()) {
                    // Use the temp file
                    xmlSource = new StreamSource(tempXmlFile);
                } else {
                    // Use the file in the original directory
                    xmlSource = new StreamSource(oldXmlFile);
                }
                Result xmlResult = new StreamResult(tempPlaylistFile);

                doTransform(transformer, xmlSource, xmlResult, "Movie: " + movie.getBaseName());

                removeBlankLines(tempPlaylistFile.getAbsolutePath());

                fileNames.add(baseName + filenameSuffix);
            }
        } catch (Exception error) {
            LOG.error(LOG_MESSAGE + "Failed generating playlist for movie " + movie);
            LOG.error(SystemTools.getStackTrace(error));
        }

        // if the multi part playlists are not required
        if (GENERATE_MULTIPART_PLAYLIST) {
            try {
                if (movie.getFiles().size() > 1) {
                    for (int i = 0; i < movieFileArray.length; i++) {
                        fileNames.add(generateSimplePlaylist(jukebox, movie, movieFileArray, i));
                    }
                }
            } catch (Exception error) {
                LOG.error(LOG_MESSAGE + "Failed generating playlist for movie " + movie);
                LOG.error(SystemTools.getStackTrace(error));
            }
        }

        return fileNames;
    }

    /**
     * Remove blank lines from the file The PCH does not like blank lines in the playlist.jsp files, so this routine will remove
     * them This routine is only called for the base playlist as this is transformed with the playlist.xsl file and therefore could
     * end up with blank lines in it
     *
     * @param filename
     */
    private void removeBlankLines(String filename) {
        BufferedReader reader = null;
        StringBuilder sb = new StringBuilder();
        FileWriter outFile = null;
        FileReader inFile = null;

        try {
            inFile = new FileReader(filename);
            reader = new BufferedReader(inFile);
            String br;

            while ((br = reader.readLine()) != null) {
                if (br.trim().length() > 0) {
                    sb.append(br).append("\n");
                }
            }
            reader.close();

            outFile = new FileWriter(filename);
            outFile.write(sb.toString());
            outFile.flush();

        } catch (Exception error) {
            LOG.debug(LOG_MESSAGE + "Failed deleting blank lines from " + filename);
            LOG.error(SystemTools.getStackTrace(error));
        } finally {
            try {
                if (inFile != null) {
                    inFile.close();
                }
            } catch (IOException e) {
                // Ignore
            }
            try {
                if (outFile != null) {
                    outFile.close();
                }
            } catch (IOException e) {
                // Ignore
            }

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Generate playlist with old simple method. The playlist is to be used for playing episodes starting from each episode
     * separately.
     *
     * @param rootPath
     * @param tempRootPath
     * @param movie
     * @param movieFiles
     * @param offset
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @return generated file name
     */
    private String generateSimplePlaylist(Jukebox jukebox, Movie movie, MovieFile[] movieFiles, int offset) throws FileNotFoundException,
            UnsupportedEncodingException {
        String fileSuffix = ".playlist" + movieFiles[offset % movieFiles.length].getFirstPart() + ".jsp";
        String baseName = movie.getBaseName();
        String tempFilename = jukebox.getJukeboxTempLocationDetails() + File.separator + baseName;
        File finalPlaylistFile = new File(jukebox.getJukeboxRootLocationDetails() + File.separator + baseName + fileSuffix);
        File tempPlaylistFile = new File(tempFilename + fileSuffix);

        if (!finalPlaylistFile.exists() || forceHTMLOverwrite || movie.isDirty(DirtyFlag.INFO)) {
            FileTools.makeDirsForFile(tempPlaylistFile);

            PrintWriter writer = new PrintWriter(tempPlaylistFile, "UTF-8");

            // Issue 237 - Add in the IP address of the MyiHome server so the playlist will work.
            // Issue 237 - It is perfectly valid for "mjb.myiHome.IP" to be blank, in fact this is
            // the normal method for stand alone YAMJ
            for (int i = 0; i < movieFiles.length; i++) {
                MovieFile part = movieFiles[(i + offset) % movieFiles.length];
                // write one line each in the format "name|0|0|IP/path" replacing an | that may exist in the title
                writer.println(movie.getTitle().replace('|', ' ') + " " + part.getFirstPart() + "|0|0|" + MYIHOME_IP + part.getFilename() + "|");
            }
            writer.flush();
            writer.close();
        }
        return baseName + fileSuffix;
    }

    /**
     * Generate the mjb.indexFile page from a template.
     * <p>
     * If the template is not found, then create a default index page
     *
     * @param jukebox
     * @param library
     */
    public void generateMainIndexHTML(Jukebox jukebox, Library library) {
        File jukeboxIndexFile = new File(SKIN_HOME, "jukebox-index.xsl");

        if (jukeboxIndexFile.exists()) {
            generateTransformedIndexHTML(jukebox, library);
        } else {
            generateDefaultIndexHTML(jukebox, library);
        }
    }

    /**
     * Use an xsl file to generate the jukebox index file
     *
     * @param jukebox
     * @param library
     */
    private void generateTransformedIndexHTML(Jukebox jukebox, Library library) {
        LOG.debug("Generating Index file from jukebox-index.xsl");

        String homePage = PropertiesUtil.getProperty("mjb.homePage", "");
        if (StringUtils.isBlank(homePage)) {
            String defCat = library.getDefaultCategory();
            if (defCat != null) {
                homePage = FileTools.createPrefix("Other", HTMLTools.encodeUrl(FileTools.makeSafeFilename(defCat))) + "1";
            } else {
                // figure out something better to do here
                LOG.info(LOG_MESSAGE + "No categories were found, so you should specify mjb.homePage in the config file.");
            }
        }

        try {
            // Create the index.xml file with some properties in it.
            File indexXmlFile = new File(jukebox.getJukeboxTempLocation(), "index.xml");
            FileTools.makeDirsForFile(indexXmlFile);

            Document docIndex = DOMHelper.createDocument();
            Element eRoot = docIndex.createElement("index");
            docIndex.appendChild(eRoot);

            DOMHelper.appendChild(docIndex, eRoot, "detailsDirName", jukebox.getDetailsDirName());
            DOMHelper.appendChild(docIndex, eRoot, "homePage", homePage);

            DOMHelper.writeDocumentToFile(docIndex, indexXmlFile);

            // Now generate the HTML from the XLST
            File htmlFile = new File(jukebox.getJukeboxTempLocation(), PropertiesUtil.getProperty("mjb.indexFile", "index.htm"));
            FileTools.addJukeboxFile(indexXmlFile.getName());
            FileTools.addJukeboxFile(htmlFile.getName());

            Transformer transformer = getTransformer(new File(SKIN_HOME, "jukebox-index.xsl"), jukebox.getJukeboxTempLocation());

            Source xmlSource = new StreamSource(indexXmlFile);
            Result xmlResult = new StreamResult(htmlFile);

            doTransform(transformer, xmlSource, xmlResult, "Jukebox index");
        } catch (ParserConfigurationException ex) {
            LOG.error(LOG_MESSAGE + "Failed generating jukebox index: " + ex.getMessage());
            LOG.error(SystemTools.getStackTrace(ex));
        } catch (RuntimeException ex) {
            LOG.error(LOG_MESSAGE + "Failed generating jukebox index: " + ex.getMessage());
            LOG.error(SystemTools.getStackTrace(ex));
        }
    }

    /**
     * Generate a simple jukebox index file from scratch
     *
     * @param jukebox
     * @param library
     */
    private void generateDefaultIndexHTML(Jukebox jukebox, Library library) {
        OutputStream fos = null;
        XMLStreamWriter writer = null;

        try {
            File htmlFile = new File(jukebox.getJukeboxTempLocation(), PropertiesUtil.getProperty("mjb.indexFile", "index.htm"));
            FileTools.makeDirsForFile(htmlFile);

            fos = FileTools.createFileOutputStream(htmlFile);
            XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
            writer = outputFactory.createXMLStreamWriter(fos, "UTF-8");

            String homePage = PropertiesUtil.getProperty("mjb.homePage", "");
            if (homePage.length() == 0) {
                String defCat = library.getDefaultCategory();
                if (defCat != null) {
                    homePage = FileTools.createPrefix("Other", HTMLTools.encodeUrl(FileTools.makeSafeFilename(defCat))) + "1";
                } else {
                    // figure out something better to do here
                    LOG.info(LOG_MESSAGE + "No categories were found, so you should specify mjb.homePage in the config file.");
                }
            }

            writer.writeStartDocument();
            writer.writeStartElement("html");
            writer.writeStartElement("head");

            writer.writeStartElement("meta");
            writer.writeAttribute("name", "YAMJ");
            writer.writeAttribute("content", "MovieJukebox");
            writer.writeEndElement();

            writer.writeStartElement("meta");
            writer.writeAttribute("HTTP-EQUIV", "Content-Type");
            writer.writeAttribute("content", "text/html; charset=UTF-8");
            writer.writeEndElement();

            writer.writeStartElement("meta");
            writer.writeAttribute("HTTP-EQUIV", "REFRESH");
            writer.writeAttribute("content", "0; url=" + jukebox.getDetailsDirName() + '/' + homePage + EXT_HTML);
            writer.writeEndElement();

            writer.writeEndElement();
            writer.writeEndElement();
        } catch (XMLStreamException ex) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML library index: " + ex.getMessage());
            LOG.error(SystemTools.getStackTrace(ex));
        } catch (FileNotFoundException ex) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML library index: " + ex.getMessage());
            LOG.error(SystemTools.getStackTrace(ex));
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (XMLStreamException ex) {
                    LOG.trace(LOG_MESSAGE + "Failed to close XMLStreamWriter");
                }
            }

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ex) {
                    LOG.trace(LOG_MESSAGE + "Failed to close FileOutputStream");
                }
            }
        }
    }

    public void generateMoviesCategoryHTML(Jukebox jukebox, Library library, String filename, String template, boolean isDirty) {
        try {
            // Issue 1886: Html indexes recreated every time
            String destFolder = jukebox.getJukeboxRootLocationDetails();
            File oldFile = FileTools.fileCache.getFile(destFolder + File.separator + filename + EXT_HTML);
            if (oldFile.exists() && !isDirty) {
                return;
            }

            LOG.info("  " + filename + "...");

            Source xmlSource;
            File detailsFolder = jukebox.getJukeboxTempLocationDetailsFile();
            File xmlFile = new File(detailsFolder, filename + EXT_XML);
            File oldXmlFile = FileTools.fileCache.getFile(jukebox.getJukeboxRootLocationDetails() + File.separator + filename + EXT_XML);
            File htmlFile = new File(detailsFolder, filename + EXT_HTML);

            FileTools.makeDirsForFile(htmlFile);

            if (xmlFile.exists()) {
                FileTools.addJukeboxFile(xmlFile.getName());
                xmlSource = new StreamSource(xmlFile);
            } else {
                xmlSource = new StreamSource(oldXmlFile);
            }

            FileTools.addJukeboxFile(htmlFile.getName());
            Result xmlResult = new StreamResult(htmlFile);

            Transformer transformer = getTransformer(new File(SKIN_HOME, template), jukebox.getJukeboxTempLocation());
            doTransform(transformer, xmlSource, xmlResult, "Playlist generation");

        } catch (Exception error) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML library category index.");
            LOG.error(SystemTools.getStackTrace(error));
        }
    }

    public void generateMoviesIndexHTML(final Jukebox jukebox, final Library library, ThreadExecutor<Void> tasks) throws Throwable {
        tasks.restart();
        for (final IndexInfo idx : library.getGeneratedIndexes()) {
            if (idx.canSkip) { // this is evaluated during XML indexing
                LOG.debug(LOG_MESSAGE + "Category '" + idx.categoryName + "' & '" + idx.key + "' no change detected, skipping HTML generation.");

                // Add the index files to the cache so they aren't deleted
                for (int page = 1; page <= idx.pages; page++) {
                    FileTools.addJukeboxFile(idx.baseName + page + EXT_XML);
                    FileTools.addJukeboxFile(idx.baseName + page + EXT_HTML);
                }
            } else {
                LOG.debug(LOG_MESSAGE + "Category '" + idx.categoryName + "' & '" + idx.key + "'");
                tasks.submit(new Callable<Void>() {
                    @Override
                    public Void call() {
                        for (int page = 1; page <= idx.pages; page++) {
                            writeSingleIndexPage(jukebox, idx, page);
                        }
                        return null;
                    }
                });
            }
        }

        tasks.waitFor();
    }

    private void writeSingleIndexPage(Jukebox jukebox, IndexInfo idx, int page) {
        try {
            File detailsDir = jukebox.getJukeboxTempLocationDetailsFile();
            FileTools.makeDirs(detailsDir);

            String filename = idx.baseName + page;

            File xmlFile = new File(detailsDir, filename + EXT_XML);

            FileTools.addJukeboxFile(xmlFile.getName());
            String indexList = PropertiesUtil.getProperty("mjb.view.indexList", "index.xsl");

            for (final String indexStr : indexList.split(",")) {
                String suffix = "";
                if (!indexStr.equals("index.xsl")) {
                    suffix = indexStr.replace("index", "").replace(EXT_XSL, "");
                }

                File htmlFile = new File(detailsDir, filename + suffix + EXT_HTML);
                FileTools.addJukeboxFile(htmlFile.getName());

                File transformCatKey = new File(SKIN_HOME, FileTools.makeSafeFilename(idx.categoryName + "_" + idx.key) + EXT_XSL);
                File transformCategory = new File(SKIN_HOME, FileTools.makeSafeFilename(idx.categoryName) + EXT_XSL);
                File transformBase = new File(SKIN_HOME, indexStr);

                Transformer transformer;

                if (transformCatKey.exists()) {
                    LOG.debug(LOG_MESSAGE + "Using CategoryKey transformation " + transformCatKey.getName() + " for " + xmlFile.getName());
                    transformer = getTransformer(transformCatKey, jukebox.getJukeboxTempLocationDetails());
                } else if (transformCategory.exists()) {
                    LOG.debug(LOG_MESSAGE + "Using Category transformation " + transformCategory.getName() + " for " + xmlFile.getName());
                    transformer = getTransformer(transformCategory, jukebox.getJukeboxTempLocationDetails());
                } else {
                    transformer = getTransformer(transformBase, jukebox.getJukeboxTempLocationDetails());
                }

                // Transformer transformer = getTransformer(new File(skinHome, "index.xsl"), rootPath);
                Source xmlSource = new StreamSource(xmlFile);
                Result xmlResult = new StreamResult(htmlFile);

                doTransform(transformer, xmlSource, xmlResult, "Category page");
            }
        } catch (Exception error) {
            LOG.error(LOG_MESSAGE + "Failed generating HTML library index for Category: " + idx.categoryName + ", Key: " + idx.key + ", Page: " + page);
            LOG.error(SystemTools.getStackTrace(error));
        }
    }

    /**
     * Creates and caches Transformer, one for every thread/xsl file.
     *
     * @param xslFile
     * @param styleSheetTargetRootPath
     * @return
     */
    public static Transformer getTransformer(File xslFile, String styleSheetTargetRootPath) {
        /*
         * Removed caching of transformer, as saxon keeps all parsed documents in memory, causing memory leaks.
         * Creating a new transformer every time doesn't consume too much time and has no impact on performance.
         * It lets YAMJ save lot of memory.
         * @author Vincent
         */
        Source xslSource = new StreamSource(xslFile);

        // Sometimes the StreamSource doesn't return an object and we get a null pointer exception, so check it and try loading it again
        for (int looper = 1; looper < 5; looper++) {
            xslSource = new StreamSource(xslFile);
            if (xslSource != null) {
                // looks ok, so quit the loop
                break;
            }
        }

        Transformer transformer = null;
        try {
            transformer = TRANSFORMER.newTransformer(xslSource);
            transformer.setParameter("homePage", INDEX_HTML_FILE);
            transformer.setParameter("rootPath", new File(styleSheetTargetRootPath).getAbsolutePath().replace('\\', '/'));
            for (Entry<Object, Object> e : PropertiesUtil.getEntrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    transformer.setParameter(e.getKey().toString(), e.getValue().toString());
                }
            }
        } catch (TransformerConfigurationException ex) {
            LOG.warn(SystemTools.getStackTrace(ex));
        }
        return transformer;
    }

    /**
     * Try to safely perform the transformation. Will retry up to maxRetryCount times before throwing the error
     *
     * @param transformer
     * @param xmlSource
     * @param xmlResult
     * @param message Message to print if there is an error
     * @throws Exception
     */
    private void doTransform(Transformer transformer, Source xmlSource, Result xmlResult, String message) throws RuntimeException {
        int retryCount = 0;

        do {
            try {
                transformer.transform(xmlSource, xmlResult);
                return;  // If the transform didn't throw an error, return
            } catch (TransformerException ex) {
                int retryTimes = MAX_RETRY_COUNT - ++retryCount;

                if (retryTimes == 0) {
                    // We've exceeded the maximum number of retries, so throw the exception and quit
                    throw new RuntimeException("Failed generating HTML, retries exceeded: " + ex.getMessage(), ex);
                } else {
                    LOG.debug(LOG_MESSAGE + "Failed generating HTML, will retry "
                            + retryTimes + " more time" + (retryTimes == 1 ? ". " : "s. ") + message);
                    try {
                        Thread.sleep(500);  // Sleep for 1/2 second to hopefully let the issue go away
                    } catch (InterruptedException ex1) {
                        // We don't care if we're interrupted
                    }
                }
            }   // Catch
        } while (retryCount <= MAX_RETRY_COUNT);
    }
}
