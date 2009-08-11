/*
 *      Copyright (c) 2004-2009 YAMJ Members
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

package com.moviejukebox.scanner;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.moviejukebox.model.Library;
import com.moviejukebox.model.MediaLibraryPath;
import com.moviejukebox.model.Movie;
import com.moviejukebox.model.MovieFile;
import com.moviejukebox.model.MovieFileNameDTO;
import com.moviejukebox.scanner.BDRipScanner.BDFilePropertiesMovie;
import com.moviejukebox.tools.HTMLTools;
import com.moviejukebox.tools.PropertiesUtil;

/**
 * DirectoryScanner
 * 
 * @author jjulien
 * @author gaelead
 * @author jriihi
 */
public class MovieDirectoryScanner {

    protected int mediaLibraryRootPathIndex;
    private String mediaLibraryRoot;
    private final Set<String> supportedExtensions = new HashSet<String>();
    private String thumbnailsFormat;
    private String postersFormat;
    private String opensubtitles;
    private Boolean excludeFilesWithoutExternalSubtitles;
    private Boolean excludeMultiPartBluRay;
    private static Logger logger = Logger.getLogger("moviejukebox");

    // BD rip infos Scanner
    private BDRipScanner localBDRipScanner;

    public MovieDirectoryScanner() {
        for (String ext : PropertiesUtil.getProperty("mjb.extensions", "AVI DIVX MKV WMV M2TS TS RM QT ISO VOB MPG MOV").toUpperCase().split(" ")) {
            supportedExtensions.add(ext);
        }
        thumbnailsFormat = PropertiesUtil.getProperty("thumbnails.format", "png");
        postersFormat = PropertiesUtil.getProperty("posters.format", "png");
        excludeFilesWithoutExternalSubtitles = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.subtitles.ExcludeFilesWithoutExternal", "false"));
        excludeMultiPartBluRay = Boolean.parseBoolean(PropertiesUtil.getProperty("mjb.excludeMultiPartBluRay", "false"));
        opensubtitles = PropertiesUtil.getProperty("opensubtitles.language", ""); // We want to check this isn't set for the exclusion

        localBDRipScanner = new BDRipScanner();
    }

    /**
     * Scan the specified directory for movies files.
     * 
     * @param directory
     *            movie library rootfile
     * @return a new library
     */
    public Library scan(MediaLibraryPath srcPath, Library library) {

        File directory = new File(srcPath.getPath());

        if (directory.isFile()) {
            mediaLibraryRoot = directory.getParentFile().getAbsolutePath();
        } else {
            mediaLibraryRoot = directory.getAbsolutePath();
        }

        mediaLibraryRootPathIndex = mediaLibraryRoot.length();

        this.scanDirectory(srcPath, directory, library);
        return library;
    }

    protected void scanDirectory(MediaLibraryPath srcPath, File directory, Library collection) {
        if (directory.isFile()) {
            scanFile(srcPath, directory, collection);
        } else {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                List<File> fileList = Arrays.asList(files);
                Collections.sort(fileList);

                // Prescan files list. Ignore directory if file with predefined name is found.
                // TODO May be read the file and exclude files by mask (similar to .cvsignore)
                for (File file : files) {
                    if (file.getName().equalsIgnoreCase(".mjbignore"))
                        return;
                }

                for (File file : fileList) {
                    if (file.isDirectory() && file.getName().equalsIgnoreCase("VIDEO_TS")) {
                        scanFile(srcPath, file.getParentFile(), collection);
                    } else if (file.isDirectory() && file.getName().equalsIgnoreCase("BDMV")) {
                        scanFile(srcPath, file.getParentFile(), collection);
                    } else if (file.isDirectory()) {
                        /*
                         * Check to see if we filter the directory. This needs to be here rather 
                         * than on the condition above so the directory is skipped if it is excluded.
                         */
                        if (!isFiltered(srcPath, file)) {
                            scanDirectory(srcPath, file, collection);
                        }
                    } else if (!isFiltered(srcPath, file)) {
                        scanFile(srcPath, file, collection);
                    }
                }
            }
        }
    }

    /**
     * Checks the file or directory passed to determine if it should be excluded from the scan
     * 
     * @param srcPath
     * @param file
     * @return boolean flag, true if the file should be excluded, false otherwise
     */
    protected boolean isFiltered(MediaLibraryPath srcPath, File file) {
        boolean isDirectory = file.isDirectory();
        String filename = file.getName();

        // Skip these parts if the file is a directory
        if (!isDirectory) {
            int index = filename.lastIndexOf(".");
            if (index < 0) {
                return true;
            }

            String extension = file.getName().substring(index + 1).toUpperCase();
            if (!supportedExtensions.contains(extension)) {
                return true;
            }

            // Exclude files without external subtitles
            if (opensubtitles.equals("")) { // We are not downloading subtitles, so exclude those that don't have any.
                if (excludeFilesWithoutExternalSubtitles && !hasSubtitles(file)) {
                    logger.fine("File " + filename + " excluded. (no external subtitles)");
                    return true;
                }
            }
        }

        // Compute the relative filename
        String relativeFilename = file.getAbsolutePath().substring(mediaLibraryRootPathIndex);
        if (relativeFilename.startsWith(File.separator)) {
            relativeFilename = relativeFilename.substring(1);
        }

        String relativeFileNameLower = relativeFilename.toLowerCase();
        for (String excluded : srcPath.getExcludes()) {
            if (excluded.length() > 0) {
                excluded = excluded.replace("/", File.separator);
                excluded = excluded.replace("\\", File.separator);
                if (relativeFileNameLower.indexOf(excluded.toLowerCase()) >= 0) {
                    logger.fine((isDirectory ? "Directory " : "File ") + filename + " excluded.");
                    return true;
                }
            }
        }

        return false;
    }

    protected static boolean hasSubtitles(File fileToScan) {
        String path = fileToScan.getAbsolutePath();
        int index = path.lastIndexOf(".");
        String basename = path.substring(0, index + 1);

        if (index >= 0) {
            return (new File(basename + "srt").exists() || new File(basename + "SRT").exists() || new File(basename + "sub").exists()
                            || new File(basename + "SUB").exists() || new File(basename + "smi").exists() || new File(basename + "SMI").exists()
                            || new File(basename + "ssa").exists() || new File(basename + "SSA").exists());
        }

        String fn = path.toUpperCase();
        if (fn.indexOf("VOST") >= 0) {
            return true;
        }
        return false;
    }

    protected void scanFile(MediaLibraryPath srcPath, File file, Library library) {
        File contentFiles[];
        int bdDuration = 0;

        contentFiles = new File[1];
        contentFiles[0] = file;

        if (file.isDirectory()) {
            // Scan BD Playlist files
            BDFilePropertiesMovie bdPropertiesMovie = localBDRipScanner.executeGetBDInfo(file);

            if (bdPropertiesMovie != null) {

                // Exclude multi part BluRay that include more than one file
                if (excludeMultiPartBluRay && bdPropertiesMovie.fileList.length > 1) {
                    logger.fine("File " + file.getName() + " excluded. (multi part BluRay)");
                    return;
                }

                bdDuration = bdPropertiesMovie.duration;
                contentFiles = bdPropertiesMovie.fileList;
            }
        }

        for (int i = 0; i < contentFiles.length; i++) {
            // Hopefully this is a fix for issue #670 -- I can't duplicate it, since I don't have an BD rips
            if (contentFiles[i] == null)
                continue;

            // Compute the baseFilename: This is the filename with no the extension
            String baseFileName = file.getName();

            if (!file.isDirectory()) {
                baseFileName = baseFileName.substring(0, file.getName().lastIndexOf("."));
            }

            // Compute the relative filename
            String relativeFilename = contentFiles[i].getAbsolutePath().substring(mediaLibraryRootPathIndex);
            if (relativeFilename.startsWith(File.separator)) {
                relativeFilename = relativeFilename.substring(1);
            }

            MovieFile movieFile = new MovieFile();
            relativeFilename = relativeFilename.replace('\\', '/'); // make it unix!

            if (contentFiles[i].isDirectory()) {
                // For DVD images
                movieFile.setFilename(srcPath.getNmtRootPath() + HTMLTools.encodeUrlPath(relativeFilename) + "/VIDEO_TS");
            } else {
                movieFile.setFilename(srcPath.getNmtRootPath() + HTMLTools.encodeUrlPath(relativeFilename));
            }
            movieFile.setPart(i + 1);
            movieFile.setFile(contentFiles[i]);

            Movie m = new Movie();
            m.setScrapeLibrary(srcPath.isScrapeLibrary());
            m.addMovieFile(movieFile);
            m.setFile(contentFiles[i]);
            m.setContainerFile(file);
            m.setBaseName(baseFileName);
            m.setLibraryPath(srcPath.getPath());
            m.setPosterFilename(baseFileName + ".jpg");
            m.setThumbnailFilename(baseFileName + "_small." + thumbnailsFormat);
            m.setDetailPosterFilename(baseFileName + "_large." + postersFormat);
            m.setSubtitles(hasSubtitles(m.getFile()));
            m.setLibraryDescription(srcPath.getDescription());
            m.setPrebuf(srcPath.getPrebuf());

            MovieFileNameDTO dto = MovieFilenameScanner.scan(file);
            m.mergeFileNameDTO(dto);

            if (bdDuration == 0) {
                // Do not merge file information for BD
                movieFile.mergeFileNameDTO(dto);
            } else {
                // Set duration for BD disks using the data in the playlist + mark bd source and container
                m.setRuntime(MediaInfoScanner.formatDuration(bdDuration));
                m.setContainer("BluRay");
                m.setVideoSource("BluRay");
            }

            library.addMovie(m);
        }
    }
}
