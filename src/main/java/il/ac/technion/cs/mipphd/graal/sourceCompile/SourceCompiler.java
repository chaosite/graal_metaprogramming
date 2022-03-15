package il.ac.technion.cs.mipphd.graal.sourceCompile;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Arrays;

public class SourceCompiler {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

    public SourceCompiler() {
    }

    public Method compile(String pkgName, String clzName, String methodName, String source) {
        final StringSourceJavaObject sourceObject;
        try {
            sourceObject = new StringSourceJavaObject(pkgName + "." + clzName, source);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Bad URI when making source object", e);
        }
        final var task = compiler.getTask(null, fileManager, null, null, null, Arrays.asList(sourceObject));
        if (task.call()) {
            try {
                return Arrays.stream(SourceCompiler.class.getClassLoader().loadClass(pkgName + "." + clzName).getMethods()).filter(m -> m.getName().equals(methodName)).findAny().orElse(null);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Couldn't find class after compiling", e);
            }
        }
        throw new RuntimeException("Compilation error?!");
    }
}
