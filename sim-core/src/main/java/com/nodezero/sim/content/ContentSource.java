package com.nodezero.sim.content;

import java.io.IOException;
import java.io.InputStream;

/** Where content files come from: the classpath in the game, an in-memory map in tests. */
@FunctionalInterface
public interface ContentSource {

    /** Opens {@code path} (relative to the content root), or returns {@code null} if it does not exist. */
    InputStream open(String path) throws IOException;

    static ContentSource classpath(String root) {
        ClassLoader loader = ContentSource.class.getClassLoader();
        return path -> loader.getResourceAsStream(root + "/" + path);
    }
}
