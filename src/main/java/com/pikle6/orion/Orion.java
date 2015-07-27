package com.pikle6.orion;

import com.pikle6.orion.util.SplitFire;

import java.nio.file.Paths;

/**
 * main testing class
 *
 */
public class Orion
{
    static final String PATH_TO_RAW = "D:\\working\\orion\\samples\\raw\\";
    static final String PATH_TO_PROCESSED = "D:\\working\\orion\\samples\\processed\\";
    static final String PATH_TO_CHUNKS = "D:\\working\\orion\\samples\\chunks\\";
    static final String MUSIC_FILE = "music.mp3";
    static final String IMAGE_FILE = "picture.jpeg";
    static final String EXE_FILE = "md5.exe";
    public static void main( String[] args )
    {
        SplitFire splitFire = new SplitFire(PATH_TO_RAW, PATH_TO_CHUNKS, PATH_TO_PROCESSED);
        splitFire.split(Paths.get(PATH_TO_RAW + MUSIC_FILE), 10);
        splitFire.split(Paths.get(PATH_TO_RAW + IMAGE_FILE), 10);
        splitFire.split(Paths.get(PATH_TO_RAW + EXE_FILE), 10);

        splitFire.assemble();
    }
}
