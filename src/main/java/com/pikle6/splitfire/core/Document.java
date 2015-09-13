package com.pikle6.splitfire.core;

import com.pikle6.splitfire.util.DocumentHelper;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by pikle6 on 7/21/2015.
 * Orions's representation of a file
 */

public class Document implements Serializable {
    private long documentId;
    private String name;
    private String extension;
    private String md5Checksum;
    private String filePath;
    private long fileSize;
    /* private BasicFileAttributes attributes; */ // TODO : serialization issue
    private long chunkCount;
    private boolean chunkCountUpdate;
    private long chunkSize;
    private String chunkPrefix;
    private String chunkExtension;
    /*private boolean chunkSizeVariant = false;*/ // TODO : add this functionality
    private SortedMap<Long, ChunkReference> chunkReferenceMap;

    public Document(long documentId, Path filePath) {
        DocumentHelper.isPathValid(filePath);
        this.documentId = documentId;
        this.filePath = filePath.toFile().getAbsolutePath();
        this.chunkPrefix = DocumentHelper.DEFAULT_CHUNK_PREFIX;
        this.chunkExtension = DocumentHelper.DEFAULT_CHUNK_EXTENSION;
        try {
            this.fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.chunkCount = 0;
        this.chunkSize = DocumentHelper.DEFAULT_CHUNK_SIZE;
        this.chunkCountUpdate = true;
        /*try {
            this.attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
        this.chunkReferenceMap = new TreeMap<>();
        this.setNameAndExtension(filePath);
    }

    public Document(long documentId, Path filePath, long chunkCount) {
        DocumentHelper.isPathValid(filePath);
        this.documentId = documentId;
        this.filePath = filePath.toFile().getAbsolutePath();
        this.chunkPrefix = DocumentHelper.DEFAULT_CHUNK_PREFIX;
        this.chunkExtension = DocumentHelper.DEFAULT_CHUNK_EXTENSION;
        try {
            this.fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(chunkCount == 0)
        {
            this.chunkCount = 0;
            this.chunkSize = DocumentHelper.DEFAULT_CHUNK_SIZE;
            this.chunkCountUpdate = true;
        }
        else
        {
            this.chunkCount = chunkCount;
            this.chunkSize = (this.fileSize/this.chunkCount);
            this.chunkCountUpdate = false;
        }
        /*try {
            this.attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
        this.chunkReferenceMap = new TreeMap<>();
        this.setNameAndExtension(filePath);
    }

    public Document(long documentId, Path filePath, long chunkCount, String chunkPrefix) {
        DocumentHelper.isPathValid(filePath);
        this.documentId = documentId;
        this.filePath = filePath.toFile().getAbsolutePath();
        this.chunkPrefix = chunkPrefix;
        this.chunkExtension = DocumentHelper.DEFAULT_CHUNK_EXTENSION;
        try {
            this.fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(chunkCount == 0)
        {
            this.chunkCount = 0;
            this.chunkSize = DocumentHelper.DEFAULT_CHUNK_SIZE;
            this.chunkCountUpdate = true;
        }
        else
        {
            this.chunkCount = chunkCount;
            this.chunkSize = (this.fileSize/this.chunkCount);
            this.chunkCountUpdate = false;
        }
        /*try {
            this.attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
        this.chunkReferenceMap = new TreeMap<>();
        this.setNameAndExtension(filePath);
    }

    public Document(long documentId, Path filePath, long chunkCount, String chunkPrefix, String chunkExtension) {
        DocumentHelper.isPathValid(filePath);
        this.documentId = documentId;
        this.filePath = filePath.toFile().getAbsolutePath();
        this.chunkPrefix = chunkPrefix;
        this.chunkExtension = chunkExtension;
        try {
            this.fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(chunkCount == 0)
        {
            this.chunkCount = 0;
            this.chunkSize = DocumentHelper.DEFAULT_CHUNK_SIZE;
            this.chunkCountUpdate = true;
        }
        else
        {
            this.chunkCount = chunkCount;
            this.chunkSize = (this.fileSize/this.chunkCount);
            this.chunkCountUpdate = false;
        }
        /*try {
            this.attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
        this.chunkReferenceMap = new TreeMap<>();
        this.setNameAndExtension(filePath);
    }

    private void setNameAndExtension(Path filePath){
        this.name = filePath.getFileName().toString();
        if(this.name.contains("."))
        {
            this.extension = this.name.substring(this.name.indexOf(".")+1);
        }
        else
        {
            this.extension = "No Extension";
        }
    }

    public void addChunk(Chunk chunk){
        ChunkReference chunkReference = new ChunkReference(chunk.getMd5Checksum(), chunk.getSize());
        if(this.chunkCountUpdate){
            this.chunkCount++;
        }
        this.chunkReferenceMap.put(chunk.getSequenceNumber(), chunkReference);
    }

    @Override
    public String toString() {
        return "Document{" +
                "documentId=" + documentId +
                ", name='" + name + '\'' +
                ", extension='" + extension + '\'' +
                ", md5Checksum='" + md5Checksum + '\'' +
                ", filePath=" + filePath +
                ", fileSize=" + fileSize +
                ", chunkCount=" + chunkCount +
                ", chunkCountUpdate=" + chunkCountUpdate +
                ", chunkSize=" + chunkSize +
                ", chunkReferenceMap=" + chunkReferenceMap +
                '}';
    }

    public void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public long getDocumentId() {
        return documentId;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public String getMd5Checksum() {
        return md5Checksum;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getChunkCount() {
        return chunkCount;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public SortedMap<Long, ChunkReference> getChunkReferenceMap() {
        return chunkReferenceMap;
    }

    public String getChunkPrefix() {
        return chunkPrefix;
    }

    public String getChunkExtension() {
        return chunkExtension;
    }
}
