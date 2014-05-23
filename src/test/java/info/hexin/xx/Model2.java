package info.hexin.xx;

import java.io.Serializable;

public class Model2 implements Serializable {
    private static final long serialVersionUID = 1L;
    String name ;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
