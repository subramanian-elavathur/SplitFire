package com.pikle6.orion;

import com.pikle6.orion.util.SplitFire;

import java.nio.file.Paths;

/**
 * main testing class
 *
 */
public class App 
{
    static final String PATH_TO_RAW = "D:\\working\\orion\\samples\\raw\\";
    static final String PATH_TO_PROCESSED = "D:\\working\\orion\\samples\\processed\\";
    static final String PATH_TO_CHUNKS = "D:\\working\\orion\\samples\\chunks\\";
    static final String MUSIC_FILE = "music.mp3";
    public static void main( String[] args )
    {
        SplitFire splitFire = new SplitFire(PATH_TO_RAW, PATH_TO_CHUNKS, PATH_TO_PROCESSED);
        splitFire.split(Paths.get(PATH_TO_RAW + MUSIC_FILE), 0);

        splitFire.assemble();
    }
}
