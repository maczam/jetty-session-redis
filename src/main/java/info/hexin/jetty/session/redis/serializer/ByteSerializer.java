package info.hexin.jetty.session.redis.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * 实例序列化
 * 
 * @author hexin
 * 
 */
public class ByteSerializer implements Serializer {

    @Override
    public byte[] serialize(Object object) throws SerializerException {
        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new SerializerException(e);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializerException {
        ClassLoadingObjectInputStream objectInputStream = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            objectInputStream = new ClassLoadingObjectInputStream(inputStream);
            return objectInputStream.readObject();
        } catch (Exception e) {
            throw new SerializerException(e);
        } finally {
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
