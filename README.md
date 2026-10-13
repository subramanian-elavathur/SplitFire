# SplitFire

[![CI](https://github.com/subramanian-elavathur/SplitFire/actions/workflows/ci.yml/badge.svg)](https://github.com/subramanian-elavathur/SplitFire/actions/workflows/ci.yml)

Split files into checksummed chunks and put them back together, with every byte verified.

SplitFire cuts a file into numbered chunk files plus a small manifest that records the SHA-256 checksum of every chunk
and of the whole file. Move, upload, or store the chunks however you like; SplitFire only reassembles them once every
checksum matches.

- **Split by size or count**: `--size 100M` or `--chunks 4`.
- **Verified**: each chunk and the reassembled file are checked against SHA-256 checksums.
- **Constant memory**: data is streamed, so files of any size work with a small heap.
- **Plain chunks**: chunks are raw slices of the file, so `cat movie.mp4.0*` rebuilds it even without SplitFire.
- **Safe**: never overwrites files unless you pass `--force`, and a failed reassembly never leaves a partial file
  behind.
- **No dependencies**: a single small jar you can use as a command-line tool or as a Java library.

## Requirements

Java 21 or newer. Maven doesn't need to be installed because the included wrapper downloads it.

## Build

```sh
./mvnw verify          # on Windows: mvnw.cmd verify
```

This compiles the code, runs the tests, and produces `target/splitfire-0.2.0.jar`.

## Usage

```sh
alias splitfire='java -jar /path/to/splitfire-0.2.0.jar'
```

```console
$ splitfire split movie.mp4 --size 100M --output chunks
Split movie.mp4 (286.1 MiB) into 3 chunks in /home/me/chunks
Manifest: /home/me/chunks/movie.mp4.splitfire

$ ls chunks
movie.mp4.001  movie.mp4.002  movie.mp4.003  movie.mp4.splitfire

$ splitfire verify chunks
OK movie.mp4 (286.1 MiB, 3 chunks)

$ splitfire assemble chunks --output restored
Assembled /home/me/restored/movie.mp4 (286.1 MiB, checksums verified)
```

| Command                                    | What it does                                                                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `split <file>`                             | Cuts the file into chunks and writes them, plus a `<file>.splitfire` manifest, to the output directory.                      |
| `assemble <manifest or directory>`         | Rebuilds the original file from a manifest and the chunks next to it. Given a directory, it rebuilds every manifest in it. |
| `verify <manifest or directory>`           | Checks that all chunks are present and intact without writing anything.                                                       |

| Option              | Applies to          | Meaning                                                                                 |
|---------------------|---------------------|-----------------------------------------------------------------------------------------|
| `-n, --chunks N`    | `split`             | Split into exactly N chunks of (nearly) equal size.                                     |
| `-s, --size SIZE`   | `split`             | Maximum chunk size, such as `4096`, `512K`, `10M`, or `2G` (powers of 1024). Default: `1M`. |
| `-o, --output DIR`  | `split`, `assemble` | Directory to write to, created if needed. Default: the current directory.              |
| `-f, --force`       | `split`, `assemble` | Overwrite existing files.                                                              |
| `-h, --help`        |                     | Show help.                                                                              |
| `-V, --version`     |                     | Show the version.                                                                       |

The exit status is `0` on success, `1` if an operation failed (such as a missing or corrupt chunk), and `2` for an
invalid command line.

## How chunks are stored

Splitting `movie.mp4` produces:

- `movie.mp4.001`, `movie.mp4.002`, ...: the raw bytes of the file, in order. Chunk numbers are zero-padded so they
  sort correctly, and they widen beyond three digits when there are more than 999 chunks.
- `movie.mp4.splitfire`: a UTF-8 [properties](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html#load(java.io.Reader))
  file describing the original file and its chunks:

```properties
# SplitFire manifest
splitfire.format=1
file.name=movie.mp4
file.size=300000000
file.sha256=d3a20246b1482feebe4fb87475e377e6835ad98f439c6dfdb42b97e2820c44b1
chunk.count=3
chunk.0.file=movie.mp4.001
chunk.0.size=104857600
chunk.0.sha256=...
```

The chunks must stay in the same directory as their manifest. When reassembling, SplitFire rejects manifests whose
file names would point outside their directory.

## Using it as a library

The jar has no dependencies. Run `./mvnw install` to put it in your local Maven repository, then depend on
`com.pikle6.splitfire:splitfire:0.2.0`.

```java
import com.pikle6.splitfire.Chunking;
import com.pikle6.splitfire.Manifest;
import com.pikle6.splitfire.SplitFire;

Manifest manifest = SplitFire.split(Path.of("movie.mp4"), Path.of("chunks"), Chunking.ofSize(100L * 1024 * 1024));

Path manifestFile = Path.of("chunks", Manifest.fileNameFor("movie.mp4"));
SplitFire.verify(manifestFile);
Path restored = SplitFire.assemble(manifestFile, Path.of("restored"));
```

Failures are reported as `IOException`s. The specific types are `NoSuchFileException` for a missing chunk,
`IntegrityException` for a chunk or file with the wrong size or checksum, `InvalidManifestException` for a malformed
manifest, and `FileAlreadyExistsException` when a target exists and overwriting wasn't requested.

## Upgrading from 0.1

Version 0.2 is a rewrite. Chunks are now plain files described by a text manifest, replacing the Java-serialized
`.chunk` and `.document` files of 0.1, and checksums use SHA-256 instead of MD5. Chunks created by 0.1 can't be read
by 0.2, so reassemble them with 0.1 first.

## Ideas

Things that might come next:

- Export all chunks as a single zip archive that can be imported again for reassembly.
- Split and reassemble several files in parallel.
- Transfer chunks over the network (SFTP), serve them from a REST server, and watch directories to reassemble files
  as their chunks arrive.
