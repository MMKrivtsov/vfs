# Virtual (File-Based) File System

## Core Idea

Storing files in single size as fixed-size blocks, similarly to FAT file systems.

Block Allocation Table (BAT) - table of indexes of next block in sequence (same size as Storage Block)

Block Storage Manager - system for accessing Storage Blocks, ex. file storage or memory storage.

Storage Block - base allocation unit of %Block Size% bytes

Storage Block Index - index of Block in Block Storage (ex. file)

Storage File - chain (single-linked-list) of Storage Blocks

Storage File Manager - system to access Storage Files

Storage File Index - index of Storage File in Storage File Manager, might or might not match index of first Storage Block,
depending on Storage File Manager implementation

Storage Block Group - several Storage Blocks and BAT for them

VFS File - file in VFS, can be opened for read or read+write, allows writing and reading file contents

Directory Handler - utility class for reading/writing Directory Entries inside of directory Storage File

## File Format

All multi-byte integers are stored in Big-Endian ordering.

* Main Header

| Bytes | Contents |
| --- | --- |
| 0 - 3 | MAGIC : 0x56 0x46 0x53 0x46 (VFSF) |
| 4 | Header Version : 0x01 |
| 5 | Storage Version : 0x01 |
| 6 | Directory Records Version : 0x01 |
| 7 | Reserved / Padding |

* Storage Header (V01)

| Bytes | Contents |
| --- | --- |
| 0 - 3 | Block Size |
| 4 - 7 | Offset in file of first Block |

* Directory Entry Header (V01)

This header is empty, reserved for possible extensions.

* Optional Padding

Some bytes might be skipped between headers and data, current default implementation pads header part of file to size of block.

## Block Allocation Table

Each 4 bytes are either special code or index of next Storage Block in Storage File.
Values (hex):

* 00 00 00 00 - Empty block, can be allocated for creation of new Storage File or for growing existing Storage File
* FF FF FF FF - Last block of block sequence
* Other value - Index of next block in Storage File sequence

## Storage Versions

### V01

Assembles Storage Blocks in groups of BlockSize/4, where first Block of each Group is used as BAT, and others are used
for data storage, thus having Block Size less than 8 would make each group consist only of BAT, making it useless.

## Directory Structure

### V01

Each entry in directory Storage File is exactly 64 bytes long.

| Bytes | Contents |
| --- | --- |
| 0 | Type : 0 - empty, 1 - file, 2 - directory |
| 1 - 3 | Reserved |
| 4 - 7 | Storage File index |
| 8 - 11 | File Size in bytes |
| 12 - 14 | Reserved |
| 15 | Entry name length in bytes |
| 16-63 | Entry name bytes in UTF16-Big-Endian |

This allocation structure limits file names to 24 characters (48 bytes).

## Locking

VFS File can be opened for read or read+write.
* Single file can be opened for Read several times in parallel for multi-threaded read.
* Single file can be opened for write only exclusively (no parallel opens of write nor read).

Storage File and Storage Block can be locked for read or read+write, trying to lock read or write while write is already
locked would make current thread wait until another concurrent locks are released.

## Further Improvements

* Make more tests to better check multi-threading safety
* Refactor to use java.nio.file.FileSystem
