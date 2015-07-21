package com.pikle6.orion.util;

import com.pikle6.orion.core.Chunk;
import com.pikle6.orion.core.ChunkReference;
import com.pikle6.orion.core.Document;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pikle6 on 7/21/2015.
 * set of function that help with chunk handling
 */
public class ChunkHelper {
    public boolean isChunkReferenceValid(ChunkReference reference){
        return !(reference.getChunkSize() == 0L || reference.getMd5Checksum().isEmpty());
    }

    public static void serializeChunk(SplitFire context, Document document, Chunk chunk){
        try(
                ObjectOutputStream objectStream = new ObjectOutputStream(
                        new FileOutputStream(
                                context.getChunkDirectory().toFile().getAbsolutePath() +
                                "\\" +
                                document.getChunkPrefix() +
                                chunk.getSequenceNumber() +
                                getFormattedChunkPartialName(document)
                        )
                );
        ) {
            objectStream.writeObject(chunk);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Chunk deserializeChunk(Path pathToChunk){
        try(
            ObjectInputStream objectStream = new ObjectInputStream(Files.newInputStream(pathToChunk));
        ) {
            return (Chunk) objectStream.readObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getFormattedChunkPartialName(Document document){
        return "-" + document.getName() + "-" + document.getDocumentId() + "." + document.getChunkExtension();
    }

    public static String getMD5Hash(byte[] data){
        MessageDigest md5Hash = null;
        StringBuilder hexString = new StringBuilder();
        try {
            md5Hash = MessageDigest.getInstance("MD5");
            byte[] hash = md5Hash.digest(data);
            for (byte aHash : hash) {
                if ((0xff & aHash) < 0x10) {
                    hexString.append("0").append(Integer.toHexString((0xFF & aHash)));
                } else {
                    hexString.append(Integer.toHexString(0xFF & aHash));
                }
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Path> getChunkPaths(SplitFire context, Document document){
        List<Path> chunkPaths = new ArrayList<>();
        String formattedName = getFormattedChunkPartialName(document);
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(context.getChunkDirectory())){
            for (Path path : directoryStream) {
                if(path.toString().endsWith(formattedName))
                {
                    chunkPaths.add(path);
                }
            }
            if(chunkPaths.size()==0)
            {
                throw new RuntimeException("No Chunks Found");
            }
            return chunkPaths;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
