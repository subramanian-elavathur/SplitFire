package com.pikle6.splitfire.util;

import com.pikle6.splitfire.core.Chunk;
import com.pikle6.splitfire.core.ChunkReference;
import com.pikle6.splitfire.core.Document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by pikle6 on 7/21/2015.
 * Engine for splitting and reassembly
 */
public class Engine {

    private Path inputDirectory;
    private Path chunkDirectory;
    private Path outputDirectory;

    public Engine(String inputDirectory, String chunkDirectory, String outputDirectory) {
        this.inputDirectory = Paths.get(inputDirectory);
        this.chunkDirectory = Paths.get(chunkDirectory);
        this.outputDirectory = Paths.get(outputDirectory);
        this.PathValidator(this.inputDirectory, this.chunkDirectory, this.outputDirectory);
    }

    private void PathValidator(Path ... paths){
        for(Path path : paths){
            if(!path.toFile().isDirectory()){
                throw new RuntimeException("Invalid input, chunk or output directory");
            }
            else{
                if(!path.toFile().exists()){
                    try {
                        Files.createDirectory(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public long split(Path file, int chunkCount){
        long documentId = (new Random()).nextLong();
        Document document = new Document(documentId, file, chunkCount);
        byte[] buffer = new byte[(int) document.getChunkSize()];
        int counter = 0;
        try(InputStream source = Files.newInputStream(file)) {
            int readCount;
            while ((readCount = source.read(buffer)) > 0) {
                Chunk chunk = new Chunk(counter++, documentId, readCount);
                chunk.setData(buffer);
                chunk.setMd5Checksum(ChunkHelper.getMD5Hash(buffer));
                document.addChunk(chunk);
                ChunkHelper.serializeChunk(this, document, chunk);
            }
            DocumentHelper.serializeDocument(this, document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return documentId;
    }

    public void assemble(){
        List<Path> documentPaths = DocumentHelper.getDocumentPaths(this);
        for(Path documentPath : documentPaths){
            Document document = DocumentHelper.deserializeDocument(documentPath);
            SortedMap<Long, ChunkReference> referenceMap = document.getChunkReferenceMap();
            List<Path> chunkPaths = ChunkHelper.getChunkPaths(this, document);
            SortedMap<Long, byte[]> chunkMap= new TreeMap<>();
            for(Path chunkPath : chunkPaths){
                Chunk chunk = ChunkHelper.deserializeChunk(chunkPath);
                if(chunk.getMd5Checksum().equals(referenceMap.get(chunk.getSequenceNumber()).getMd5Checksum())){
                    chunkMap.put(chunk.getSequenceNumber(), chunk.getData());
                }
                else{
                    throw new RuntimeException("Checksum validation Failed Try Again");
                }
            }
            DocumentHelper.createFile(this, document, chunkMap);
        }
    }

    public Path getInputDirectory() {
        return this.inputDirectory;
    }

    public Path getChunkDirectory() {
        return this.chunkDirectory;
    }

    public Path getOutputDirectory() {
        return this.outputDirectory;
    }

    @Override
    public String toString() {
        return "Engine{" +
                "inputDirectory=" + inputDirectory +
                ", chunkDirectory=" + chunkDirectory +
                ", outputDirectory=" + outputDirectory +
                '}';
    }
}
