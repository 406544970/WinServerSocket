package SocketServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import Model.User;
import Model.UserState;
import com.google.gson.Gson;

/**
 * @author 梁昊
 * @date:2019/7/23
 */
public class UserStateManage extends Thread {
    /**
     * 工作线程类
     */
    class Handler implements Runnable {
        private Socket socket;
        private UserState us = null;
        private User newUser = null;
        private int userId;
        private int userState;

        /**
         * 构造函数，从调用者那里取得socket
         *
         * @param socket 指定的socket
         * @author dream
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * 从指定的socket中得到输入流
         *
         * @param socket 指定的socket
         * @return 返回BufferedReader
         * @author dream
         */
        private BufferedReader getReader(Socket socket) throws IOException {
            InputStream is = null;
            BufferedReader br = null;
            DataOutputStream out = null;
            try {
                is = socket.getInputStream();
                br = new BufferedReader(new InputStreamReader(is));
                out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF(String.format("Server:%s", br.readLine()));
            } finally {
                out.close();
            }
            return br;
        }

        public void run() {
            try {
                workThreadNum = workThreadNum + 1;
                System.out.println("【第" + workThreadNum + "个的连接:" + socket.getInetAddress() + ":" + socket.getPort() + "】");
                BufferedReader br = getReader(socket);
                String meg = null;
                StringBuffer report = new StringBuffer();
                while ((meg = br.readLine()) != null) {
                    report.append(meg);
                    if (meg.contains(csEndFlag)) {
                        us = getReporterUserState(meg, socket);
                        synchronized (hashLock) {
                            newUserStateList.put(userId, us);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("【客户:" + newUser.getUser_id() + "已经断开连接！】");
                newUserStateList.remove(userId);
                announceStateChange(userId, -1);
            } finally {
                if (socket != null) {
                    try {
                        //断开连接
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private UserState getReporterUserState(String meg, Socket socket) {
            UserState us = new UserState();
            try {
                newUser = gson.fromJson(meg, User.class);
                userId = newUser.getUser_id();
                userState = newUser.getUser_state();
                us.setFlag(2);
                us.setUser_state(userState);
                us.setUser_id(userId);
                us.setUser_ip(newUser.getUser_ip());
                us.setUser_port(newUser.getUser_port());
            } catch (Exception e) {
                System.out.println("【来自客户端的信息不是一个合法的心跳包协议】");
            }
            return us;
        }
    }

    /**
     * 扫描线程
     */
    class scan implements Runnable {
        public void run() {
            while (true) {
                System.out.println("*******" + new Date() + "：扫描线程开始扫描" + "*******");
                synchronized (hashLock) {
                    if (!newUserStateList.isEmpty()) {
                        //遍历在线用户列表
                        for (Map.Entry<Integer, UserState> entry : newUserStateList.entrySet()) {
                            int flag = entry.getValue().getFlag();
                            if ((flag - 1) < 0) {
                                //在这里通知该用户的好友其状态发生改变
//                                 announceStateChange(entry.getKey() , 0);
                            } else {
                                entry.getValue().setFlag(flag - 1);
                                newUserStateList.put(entry.getKey(), entry.getValue());
                            }
                            System.out.println(entry.getKey() + "-->" + entry.getValue().toString());
                        }
                    } else {
                        System.out.println("现在还没有在线用户！");
                    }
                }
                //实现定时扫描
                try {
                    sleep(scanTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 默认构造函数
     */
    public UserStateManage() {
        SetParam("127.0.0.1", 60000, 1800);
    }

    /**
     * @param host ip
     * @param port 端口
     */
    public UserStateManage(String host, int port) {
        SetParam(host, port, 1800);
    }

    /**
     * @param host     ip
     * @param port     端口
     * @param scanTime 心跳周期
     */
    public UserStateManage(String host, int port, int scanTime) {
        SetParam(host, port, scanTime);
    }

    /**
     * @param host     ip
     * @param port     端口
     * @param scanTime 心跳周期
     */
    private void SetParam(String host, int port, int scanTime) {
        this.host = host;
        this.port = port;
        this.scanTime = scanTime;
    }

    /**
     * 序列化组件
     */
    private Gson gson = new Gson();

    /**
     * 在线用户状态列表
     */
    private static ConcurrentHashMap<Integer, UserState> newUserStateList = new ConcurrentHashMap<>();

    /**
     * Lock
     */
    private Object hashLock = new Object();
    /**
     * 当前的连接数和工作线程数
     */
    private static int workThreadNum = 0;
    /**
     * socket连接数量
     */
    private static int socketConnect = 0;
    private ServerSocket serverSocket;
    /**
     * 服务器IP
     */
    private String host;
    /**
     * 服务器端口
     */
    private int port;
    /**
     * 设置心跳包的结束标记
     */
    private String endFlag = "</protocol>";
    /**
     * 结束符
     */
    private CharSequence csEndFlag = endFlag.subSequence(0, 10);
    /**
     * 扫描间隔
     */
    private int scanTime;

    @Override
    public void run() {
        //绑定端口,并开始侦听用户的心跳包
        serverSocket = startListenUserReport(this.port);
        if (serverSocket == null) {
            System.out.println("【创建ServerSocket失败！】");
            return;
        }
        //启动扫描线程
        Thread scanThread = new Thread(new scan());
        scanThread.start();
        //等待用户心跳包请求
        while (true) {
            Socket socket = null;
            try {
                socketConnect = socketConnect + 1;
                //接收客户端的连接
                socket = serverSocket.accept();
                //为该连接创建一个工作线程
                Thread workThread = new Thread(new Handler(socket));
                //启动工作线程
                workThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 创建一个ServerSocket来侦听用户心跳包请求
     *
     * @param port 指定的服务器端的端口
     * @return 返回ServerSocket
     * @author dream
     */
    public ServerSocket startListenUserReport(int port) {
        try {
            ServerSocket serverSocket = new ServerSocket();
            if (!serverSocket.getReuseAddress()) {
                serverSocket.setReuseAddress(true);
            }
            serverSocket.bind(new InetSocketAddress(host, port));
            System.out.println("【开始在" + serverSocket.getLocalSocketAddress() + "上侦听用户的心跳包请求！】");
            return serverSocket;
        } catch (IOException e) {
            System.out.println("【端口" + port + "已经被占用！】");
            if (serverSocket != null) {
                if (!serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
        return serverSocket;
    }

    /**
     * 通知好友
     *
     * @param userId 好友ID
     * @param state  状态
     */
    private void announceStateChange(int userId, int state) {
        System.out.println("通知其好友!");
    }

    /**
     * 查询一个用户是否在线
     *
     * @param userId 指定要查询状态的用户的ID
     * @return true 在线； false 不在线；
     * @author dream
     */
    public boolean isAlive(int userId) {
        synchronized (hashLock) {
            return newUserStateList.containsKey(userId);
        }
    }

    /**
     * 返回指定用户ID的状态
     *
     * @param userId 指定要查询状态的用户的ID
     * @return >0 该用户在线;  -1 该用户离线
     * @author dream
     */
    public int getUserState(int userId) {
        synchronized (hashLock) {
            if (newUserStateList.containsKey(userId)) {
                return newUserStateList.get(userId).getUser_state();
            } else {
                return -1;
            }
        }
    }

    public Object getHashLock() {
        return hashLock;
    }

    public void setHashLock(Object hashLock) {
        this.hashLock = hashLock;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getStateReportPort() {
        return port;
    }

    public void setStateReportPort(int stateReportPort) {
        this.port = stateReportPort;
    }

    public String getEndFlag() {
        return endFlag;
    }

    public void setEndFlag(String endFlag) {
        this.endFlag = endFlag;
    }

    public int getScanTime() {
        return scanTime;
    }

    public void setScanTime(int scanTime) {
        this.scanTime = scanTime;
    }

    public static ConcurrentHashMap<Integer, UserState> getUserStateList() {
        return newUserStateList;
    }

    public static int getWorkThreadNum() {
        return workThreadNum;
    }

}