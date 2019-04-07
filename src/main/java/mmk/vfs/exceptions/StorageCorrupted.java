package mmk.vfs.exceptions;

import java.io.IOException;

public class StorageCorrupted extends IOException {
    public StorageCorrupted(String message) {
        super(message);
    }
}
