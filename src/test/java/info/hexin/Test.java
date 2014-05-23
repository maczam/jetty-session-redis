package info.hexin;

import java.net.URL;
import java.net.URLClassLoader;

public class Test {
    public static void main(String[] args) throws Exception {
        
        String className = "info.hexin.xx.Model";
        
        URL url = new URL("file",null,"c:/ab.jar");
//        URL url = new URL("file:////c://ab.jar");
        System.out.println(url.getFile());
        
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{url});
        Thread.currentThread().setContextClassLoader(classLoader);
        
//        Class clazz = Class.forName("info.hexin.xx.Model",false,classLoader);
//        Class clazz = Class.forName("info.hexin.xx.Model",true,classLoader);
        
//        Thread.currentThread().getContextClassLoader().loadClass(className);
//        Thread.currentThread().getContextClassLoader().getParent().loadClass(className);
        Thread.currentThread().getContextClassLoader().getSystemClassLoader().loadClass(className);
        
//        Object x = clazz.newInstance();
        
        
//        Model model = new Model();
//        model.setName("Model");
//        Model2 model2 = new Model2();
//        
//        model2.setName("Model2");
        
        
//        ByteSerializer bs = new ByteSerializer();
        
//        List l = new ArrayList();
//        l.add(bs.serialize(x));
//        l.add(bs.serialize(model2));
        
//        
//        for (int i = 0; i < l.size(); i++) {
//            System.out.println(JSON.toString(bs.deserialize((byte[]) l.get(i))));
//        }
       
        
//        
//        Model c =(Model)bs.deserialize(xx);
//        System.out.println(c.getName());
    }
}
