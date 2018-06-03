package com.XMU.assembled;

import com.unity3d.player.*;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Set;
import java.util.UUID;

import static android.widget.Toast.LENGTH_SHORT;

public class UnityPlayerActivity extends Activity
{
    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
    private ServerSocket serverSocket = null;
    StringBuffer stringBuffer = new StringBuffer();
    public static final int RECV_VIEW = 0;
    private BluetoothAdapter bluetoothAdapter = null;
    private ConnectThread connectThread = null;
    private ConnectedThread connectedThread = null;
    private boolean isOn = false;
    private InputStream inputStream;
    int fg1=0;
    int fg2=0;

    public Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = null;
            switch (msg.what){
                case 1:
                    UnityPlayer.UnitySendMessage("IPadress", "changetext",msg.obj.toString());
                    break;
                case 2:
                    Unity_Command(Integer.parseInt(msg.obj.toString()));
                    stringBuffer.setLength(0);
                    break;
                case RECV_VIEW:
                    if (isOn == false) {
                        isOn = true;
                    }
                    bundle = msg.getData();
                    String recv = bundle.getString("recv");
                    UnityPlayer.UnitySendMessage("rate", "changerate",recv);
                    if (recv.isEmpty() || recv.contains(" ") || recv.contains("#")) {
                        break;
                    }
                    break;

                default:
                    break;
            }

        }
    };
    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState)
    {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        mUnityPlayer = new UnityPlayer(this);
        setContentView(mUnityPlayer);
        mUnityPlayer.requestFocus();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(UnityPlayerActivity.this, "蓝牙未开启", LENGTH_SHORT).show();
        } else {
            Toast.makeText(UnityPlayerActivity.this, "蓝牙已开启", LENGTH_SHORT).show();
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            connectThread = new ConnectThread(device);
            connectThread.start();
            Toast.makeText(UnityPlayerActivity.this, "连接成功", LENGTH_SHORT).show();
            break;
        }

        receiveData();
    }

    @Override protected void onNewIntent(Intent intent)
    {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        mUnityPlayer.quit();
        super.onDestroy();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();
        mUnityPlayer.pause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();
        mUnityPlayer.resume();
    }

    @Override protected void onStart()
    {
        super.onStart();
        mUnityPlayer.start();
    }

    @Override protected void onStop()
    {
        super.onStop();
        mUnityPlayer.stop();
    }

    // Low Memory Unity
    @Override public void onLowMemory()
    {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }
    class ServerThread extends Thread{

        private Socket socket;
        private InputStream inputStream;
        private StringBuffer stringBuffer = UnityPlayerActivity.this.stringBuffer;


        public ServerThread(Socket socket,InputStream inputStream){
            this.socket = socket;
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            int len;
            byte[] bytes = new byte[20];
            boolean isString = false;

            try {
                //在这里需要明白一下什么时候其会等于 -1，其在输入流关闭时才会等于 -1，
                //并不是数据读完了，再去读才会等于-1，数据读完了，最终结果也就是读不到数据为0而已；
                while ((len = inputStream.read(bytes)) != -1) {
                    for(int i=0; i<len; i++){
                        if(bytes[i] != '\0'){
                            stringBuffer.append((char)bytes[i]);
                        }else {
                            isString = true;
                            break;
                        }
                    }
                    if(isString){
                        Message message_2 = handler.obtainMessage();
                        message_2.what = 2;
                        message_2.obj = stringBuffer;
                        handler.sendMessage(message_2);
                        isString = false;
                    }

                }
                //当这个异常发生时，说明客户端那边的连接已经断开
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    inputStream.close();
                    socket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            }

        }
    }

    public void receiveData(){

        Thread thread = new Thread(){
            @Override
            public void run() {
                super.run();
                /*指明服务器端的端口号*/
                try {
                    serverSocket = new ServerSocket(8000);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                GetIpAddress.getLocalIpAddress(serverSocket);

                Message message_1 = handler.obtainMessage();//发送IP地址
                message_1.what = 1;
                message_1.obj = GetIpAddress.getIP() ;
                handler.sendMessage(message_1);

                while (true){
                    Socket socket = null;
                    try {
                        socket = serverSocket.accept();
                        inputStream  = socket.getInputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    new ServerThread(socket,inputStream).start();

                }
            }
        };
        thread.start();

    }

    void Unity_Command(int number) {
        if (number == 1)
        {
            if(fg1==0)
            {
                UnityPlayer.UnitySendMessage("ShowObject", "TMiddle", "1");
                fg1=1;
            }else
            {
                UnityPlayer.UnitySendMessage("ShowObject", "TMiddle", "0");
                fg1=0;
            }
        }
        else if (number == 2)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeBiger", "0.003");
        else if (number == 3)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeSmaller", "0.003");
        else if (number == 4)
            if (fg2 == 0) {
                fg2 = 1;
                UnityPlayer.UnitySendMessage("ShowObject/liver","liverCut", "1");
                UnityPlayer.UnitySendMessage("ShowObject/organ","organCut", "1");
            } else {
                fg2 = 0;
                UnityPlayer.UnitySendMessage("ShowObject/liver","liverCut", "0");
                UnityPlayer.UnitySendMessage("ShowObject/organ","organCut", "0");
            }
        else if (number == 5)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnMinus_x", "3");
        else if (number == 6)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnMinus_y", "3");
        else if (number == 7) {
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnPlus_x", "3");
        } else if (number == 8) {
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnPlus_y", "3");
        } else if (number == 9)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnPlus_z", "3");
        else if (number == 10)
            UnityPlayer.UnitySendMessage("ShowObject", "MakeTurnMinus_z", "3");

    }

    public void onBackPressed() {
        super.onBackPressed();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ConnectThread extends Thread {
        private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.socket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
                return;
            }
            //manageConnectedSocket(socket);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream input = null;
            OutputStream output = null;

            try {
                input = socket.getInputStream();
                output = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.inputStream = input;
            this.outputStream = output;
        }

        public void run() {
            StringBuilder recvText = new StringBuilder();
            byte[] buff = new byte[1024];
            int bytes;
            //Bundle tmpBundle = new Bundle();
            //Message tmpMessage = new Message();
            //tmpBundle.putString("notice", "连接成功");
            //tmpMessage.what = NOTICE_VIEW;
            //tmpMessage.setData(tmpBundle);
            //handler.sendMessage(tmpMessage);
            while (true) {
                try {
                    bytes = inputStream.read(buff);
                    String str = new String(buff, "ISO-8859-1");
                    str = str.substring(0, bytes);

                    // 收到数据，单片机发送上来的数据以"#"结束，这样手机知道一条数据发送结束
                    //Log.e("read", str);
/*                   if (!str.endsWith("#")) {
                        recvText.append(str);
                        continue;
                    }*/
                    recvText.append(str.substring(0, str.length() - 1)); // 去除'#'
                    Log.e(recvText.toString(),str);
                    Bundle bundle = new Bundle();
                    Message message = new Message();

                    // receive_view.append(recvText.toString() + "\n");
                    bundle.putString("recv", recvText.toString());
                    message.what = RECV_VIEW;
                    message.setData(bundle);
                    handler.sendMessage(message);
                    recvText.replace(0, recvText.length(), "");
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    // Pass any events not handled by (unfocused) views straight to UnityPlayer
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
    /*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }
}

class GetIpAddress {

    public static String IP;
    public static int PORT;

    public static String getIP(){
        return IP;
    }
    public static int getPort(){
        return PORT;
    }
    public static void getLocalIpAddress(ServerSocket serverSocket){

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();){
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String mIP = inetAddress.getHostAddress().substring(0, 3);
                    if(mIP.equals("192")){
                        IP = inetAddress.getHostAddress();    //获取本地IP
                        PORT = serverSocket.getLocalPort();    //获取本地的PORT
                        Log.e("IP",""+IP);
                        Log.e("PORT",""+PORT);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }

}