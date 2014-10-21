/*
 *      Copyright (c) 2004-2014 YAMJ Members
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
package com.moviejukebox.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.moviejukebox.model.Codec;
import com.moviejukebox.model.CodecType;
import com.moviejukebox.model.Movie;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaInfoScannerTest {

    private static final Logger LOG = LoggerFactory.getLogger(MediaInfoScannerTest.class);
    private static final MediaInfoScanner MI_TEST = new MediaInfoScanner();
    private static final String testDir = "src/test/java/TestFiles/MediaInfo/";
    Map<String, String> infosGeneral = new HashMap<String, String>();
    List<Map<String, String>> infosVideo = new ArrayList<Map<String, String>>();
    List<Map<String, String>> infosAudio = new ArrayList<Map<String, String>>();
    List<Map<String, String>> infosText = new ArrayList<Map<String, String>>();

    @Ignore
    public void testMediaInfoScan() {
        getMediaInfoTestFile("test.mkv", false);
        printTextInfos("Video", this.infosVideo);
        printTextInfos("Audio", this.infosAudio);
    }
    @Test
    public void testStaticFile() {
        getMediaInfoTestFile("mediainfo-1.txt", true);

        Movie movie = new Movie();
        MI_TEST.updateMovieInfo(movie, infosGeneral, infosVideo, infosAudio, infosText, infosGeneral);

        LOG.info("Runtime: " + movie.getRuntime());
        assertEquals("Wrong runtime", "1h 56m", movie.getRuntime());
    }

    @Test
    public void testAudioStreamFile() {
        getMediaInfoTestFile("mediainfo-2.txt", true);
        assertEquals(7, infosAudio.size());
        assertEquals(1, infosVideo.size());
    }

    @Test
    public void testAvi() {
        getMediaInfoTestFile("AVI_DTS_ES_6.1.AVI.txt", true);

        Codec codec;
        int counter = 1;
        for (Map<String, String> codecInfo : infosVideo) {
            codec = MI_TEST.getCodecInfo(CodecType.VIDEO, codecInfo);
            System.out.println(counter++ + " = " + codec.toString());
        }

        counter = 1;
        for (Map<String, String> codecInfo : infosAudio) {
            codec = MI_TEST.getCodecInfo(CodecType.AUDIO, codecInfo);
            System.out.println(counter++ + " = " + codec.toString());
        }

        getMediaInfoTestFile("AVI_DTS_MA_7.1.AVI.txt", true);

        counter = 1;
        for (Map<String, String> codecInfo : infosVideo) {
            codec = MI_TEST.getCodecInfo(CodecType.VIDEO, codecInfo);
            System.out.println(counter++ + " = " + codec.toString());
        }

        counter = 1;
        for (Map<String, String> codecInfo : infosAudio) {
            codec = MI_TEST.getCodecInfo(CodecType.AUDIO, codecInfo);
            System.out.println(counter++ + " = " + codec.toString());
        }
    }

    @Test
    public void testChannels() {
        getMediaInfoTestFile("mediainfo-channels.txt", true);

        Codec codec;
        int counter = 1;
        for (Map<String, String> codecInfo : infosAudio) {
            codec = MI_TEST.getCodecInfo(CodecType.AUDIO, codecInfo);
            LOG.debug("{} = {}", counter++, codec.toString());
            assertTrue("No channels found!", codec.getCodecChannels() > 0);
        }
    }

    @Test
    public void testMultipleAudioCodes() {
        getMediaInfoTestFile("DTS_AC3_DTS_AC3.txt", true);
        Movie movie = new Movie();
        Codec codec;
        int counter = 1;
        
        for (Map<String, String> codecInfo : infosAudio) {
            codec = MI_TEST.getCodecInfo(CodecType.AUDIO, codecInfo);
            movie.addCodec(codec);
            LOG.debug("{} = {}", counter++, codec.toString());
            assertTrue("No channels found!", codec.getCodecChannels() > 0);
        }

        for (Codec audio : movie.getCodecs()) {
            LOG.debug("AudioCodec: {}", audio.toString());
        }
    }

    /**
     * Output the infos
     *
     * printTextInfos("General", Arrays.asList(infosGeneral));
     *
     * printTextInfos("Video", infosVideo);
     *
     * printTextInfos("Audio", infosAudio);
     *
     * printTextInfos("Text", infosText);
     *
     * @param title
     * @param infos
     */
    public void printTextInfos(String title, List<Map<String, String>> infos) {
        LOG.info("***** {}", title);
        for (Map<String, String> info : infos) {
            for (Map.Entry<String, String> entry : info.entrySet()) {
                LOG.info("{} -> {}", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Load a test file
     *
     * @param filename
     */
    private void getMediaInfoTestFile(String filename, boolean isText) {
        File file = FileUtils.getFile(testDir, filename);
        LOG.info("File: {} Length: {} Exists: {}", file.getAbsolutePath(), file.length(), file.exists());

        MediaInfoStream stream = null;
        try {
            if (isText) {
                stream = new MediaInfoStream(new FileInputStream(file));
            } else {
                stream = MI_TEST.createStream(file.getAbsolutePath());
            }

            infosGeneral.clear();
            infosVideo.clear();
            infosAudio.clear();
            infosText.clear();

            MI_TEST.parseMediaInfo(stream, infosGeneral, infosVideo, infosAudio, infosText);
        } catch (FileNotFoundException error) {
            LOG.warn("File not found.", error);
            assertFalse("No exception expected : " + error.getMessage(), true);
        } catch (Exception error) {
            LOG.warn("IOException.", error);
            assertFalse("No exception expected : " + error.getMessage(), true);
        } finally {
            if (stream != null)  {
                stream.close();
            }
        }
    }
}
