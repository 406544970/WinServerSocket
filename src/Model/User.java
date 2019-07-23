package Model;

/**
 * @author 梁昊
 * @date:2019/7/23
 */
public class User {
    private int user_id;
    private int user_state;
    private int flag;
    private int user_port;

    public int getUser_id() {
        return user_id;
    }

    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }

    public int getUser_state() {
        return user_state;
    }

    public void setUser_state(int user_state) {
        this.user_state = user_state;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public int getUser_port() {
        return user_port;
    }

    public void setUser_port(int user_port) {
        this.user_port = user_port;
    }

    public String getUser_ip() {
        return user_ip;
    }

    public void setUser_ip(String user_ip) {
        this.user_ip = user_ip;
    }

    private String user_ip;

}
