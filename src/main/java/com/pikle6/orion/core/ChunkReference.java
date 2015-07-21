package com.pikle6.orion.core;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Created by pikle6 on 7/21/2015.
 * chunkReference stored inside each Document
 */

public class ChunkReference implements Serializable {
    /*private long startingIndex;*/ // TODO : implement this
    private String md5Checksum;
    private long chunkSize;

    public ChunkReference(String md5Checksum, long chunkSize) {
        this.md5Checksum = md5Checksum;
        this.chunkSize = chunkSize;
    }

    @JsonProperty
    public String getMd5Checksum() {
        return this.md5Checksum;
    }

    @JsonProperty
    public void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    @JsonProperty
    public long getChunkSize() {
        return this.chunkSize;
    }

    @JsonProperty
    public void setChunkSize(long chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public String toString() {
        return "ChunkReference{" +
                ", md5Checksum='" + md5Checksum + '\'' +
                ", chunkSize=" + chunkSize +
                '}';
    }
}
