package mmk.vfs.exceptions;

import java.io.IOException;

public class RootDirectoryModificationException extends IOException {
    public RootDirectoryModificationException(String message) {
        super(message);
    }
}
