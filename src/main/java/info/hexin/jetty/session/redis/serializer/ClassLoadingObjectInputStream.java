package info.hexin.jetty.session.redis.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * 避免反序列化时找不到对应的class
 * 
 * @author hexin
 * 
 */
class ClassLoadingObjectInputStream extends ObjectInputStream {
    public ClassLoadingObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    public ClassLoadingObjectInputStream() throws IOException {
        super();
    }

    @Override
    public Class<?> resolveClass(ObjectStreamClass cl) throws IOException, ClassNotFoundException {

        String className = cl.getName();
        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            try {
                clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            } catch (ClassNotFoundException e1) {
                try {
                    clazz = Thread.currentThread().getContextClassLoader().getParent().loadClass(className);
                } catch (ClassNotFoundException e2) {
                    try {
                        clazz = ClassLoader.getSystemClassLoader().loadClass(className);
                    } catch (ClassNotFoundException e3) {
                        return super.resolveClass(cl);
                    }
                }
            }
        }
        return clazz;
    }
}
