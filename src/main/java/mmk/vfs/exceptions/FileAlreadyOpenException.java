package mmk.vfs.exceptions;

import java.io.IOException;

public class FileAlreadyOpenException extends IOException {

    public FileAlreadyOpenException(String msg) {
        super(msg);
    }
}
