package il.ac.technion.cs.mipphd.graal.sourceCompile;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class StringSourceJavaObject extends SimpleJavaFileObject {
    private String contents = null;
    public StringSourceJavaObject(String name, String content) throws URISyntaxException {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.contents = content;
    }
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return contents;
    }
}
