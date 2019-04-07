package mmk.vfs.exceptions;

import java.io.IOException;

public class OutOfStorage extends IOException {
    public OutOfStorage(String message) {
        super(message);
    }
}
