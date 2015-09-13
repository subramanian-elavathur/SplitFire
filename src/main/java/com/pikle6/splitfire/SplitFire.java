package com.pikle6.splitfire;

import com.pikle6.splitfire.util.Engine;

import java.nio.file.Paths;

/**
 * main testing class
 *
 */
public class SplitFire
{
    static final String PATH_TO_RAW = "D:\\working\\SplitFire\\samples\\raw\\";
    static final String PATH_TO_PROCESSED = "D:\\working\\SplitFire\\samples\\processed\\";
    static final String PATH_TO_CHUNKS = "D:\\working\\SplitFire\\samples\\chunks\\";
    static final String MUSIC_FILE = "music.mp3";
    static final String IMAGE_FILE = "picture.jpeg";
    static final String EXE_FILE = "md5.exe";
    static final String SHOW_FILE = "show.mp4";
    public static void main( String[] args )
    {
        Engine engine = new Engine(PATH_TO_RAW, PATH_TO_CHUNKS, PATH_TO_PROCESSED);
        //engine.split(Paths.get(PATH_TO_RAW + SHOW_FILE), 0);
        /*engine.split(Paths.get(PATH_TO_RAW + IMAGE_FILE), 10);
        engine.split(Paths.get(PATH_TO_RAW + EXE_FILE), 10);*/

        engine.assemble();
    }
}
