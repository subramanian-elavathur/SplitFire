package com.pikle6.splitfire.util;

import com.pikle6.splitfire.core.Document;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Created by pikle6 on 7/21/2015.
 * Document related helper functions
 */
public class DocumentHelper {

    public static final long DEFAULT_CHUNK_SIZE = 1048576L;

    public static final String DEFAULT_CHUNK_PREFIX = "orion-";

    public static final String DEFAULT_CHUNK_EXTENSION = "chunk";

    public static final String DEFAULT_DOCUMENT_EXTENSION = ".document";

    public static void isPathValid(Path path){
        if(path.toFile().isFile())
        { }
        else
        {
            throw new RuntimeException("No such file. Please check the path specified");
        }
    }

    public static void serializeDocument(Engine context, Document document){
        try(
            ObjectOutputStream objectStream = new ObjectOutputStream(
                new FileOutputStream(
                        context.getChunkDirectory().toFile().getAbsolutePath() +
                        "\\" +
                        document.getDocumentId() +
                        DocumentHelper.DEFAULT_DOCUMENT_EXTENSION
                )
            )
        ) {
            objectStream.writeObject(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document deserializeDocument(Path pathToDocument){
        try(
            ObjectInputStream objectStream = new ObjectInputStream(Files.newInputStream(pathToDocument))
        ) {
            return (Document) objectStream.readObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void createFile(Engine context, Document document, SortedMap<Long, Path> chunkMap){
        try(
            OutputStream outputStream = new FileOutputStream(
                context.getOutputDirectory().toFile().getAbsolutePath() +
                "\\" +
                document.getName()
            )
         ) {
            for (Map.Entry<Long, Path> entry : chunkMap.entrySet())
            {
                outputStream.write(ChunkHelper.deserializeChunk(entry.getValue()).getData());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Path> getDocumentPaths(Engine context){
        List<Path> documentPaths = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(context.getChunkDirectory())){
            for (Path path : directoryStream) {
                if(path.toString().endsWith(DEFAULT_DOCUMENT_EXTENSION))
                {
                    documentPaths.add(path);
                }
            }
            if(documentPaths.size()==0)
            {
                throw new RuntimeException("No Documents Found");
            }
            return documentPaths;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
