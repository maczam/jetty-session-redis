package info.hexin.jetty.session.redis.serializer;

/**
 * 强转
 * 
 * @author hexin
 * 
 */
public class Converter {

    /**
     * 强转为long
     * 
     * @param o
     * @return
     */
    public static long Long(Object o) {
        String(o);
        return Long.parseLong(o.toString());
    }
    
    /**
     * 强转为int
     * 
     * @param o
     * @return
     */
    public static int Int(Object o) {
        CheckNull(o);
        return Integer.parseInt(o.toString());
    }
    
    public static String String(Object o){
        CheckNull(o);
        return o.toString();
    }

    private static void CheckNull(Object o) {
        if (o == null) {
            throw new RuntimeException("转化不能为null");
        }
    }
}
